# Informe de cierre de Department — UrbanPulse

Implementación y pruebas: 30–31/10/2026. Revisión final: 31/10/2026. Rama: `feature/department-management`. Base revisada: `58a9c6c`.

## 1. Estado inicial

El árbol de trabajo estaba limpio. Ya existían la entidad Department, repositorio, servicio, controlador, tres DTO, excepciones específicas, migración V2 con semillas y pruebas básicas. Las cuatro operaciones REST y la autorización por correo verificado/ADMIN estaban implementadas. Se ha conservado esa arquitectura y el código funcional.

Faltaba comprobar la persistencia real de departamentos: `mvn test` no ejecutaba los `*IT`. Se detectaron además estos problemas:

- Un generador UUIDv7 propio duplicaba la estrategia Hibernate exigida por ADR-0011.
- La validación del objeto PATCH vacío no estaba expuesta como propiedad válida para Bean Validation; no se distinguía correctamente omisión de null explícito.
- Las longitudes se validaban antes de normalizar espacios.
- Todos los errores de integridad de persistencia se presentaban como duplicados.
- Los PATCH concurrentes podían sobrescribir campos que una petición había omitido.
- Los errores propios de departamentos no utilizaban la fábrica común de ProblemDetail; un UUID de ruta inválido acababa como error interno.
- La prueba de autenticación asumía que solo existía una migración, incompatible con V2.
- Había textos que todavía presentaban departamentos como funcionalidad futura y diagramas incompletos en sus atributos.

## 2. Cambios realizados y motivos

Las rutas Java de esta tabla parten de `backend/src/main/java/es/grupo18/urbanpulse/`; las pruebas de `backend/src/test/java/es/grupo18/urbanpulse/`.

| Archivo | Cambio y motivo |
| --- | --- |
| `entity/Department.java` | Generación mediante `@GeneratedValue` y `@UuidGenerator(VERSION_7)`. Alinea Department con User y ADR-0011, manteniendo callbacks de fechas. |
| `entity/UuidV7Generator.java` | Eliminado el generador propio, ya innecesario. No se altera ningún UUID persistido. |
| `repository/DepartmentRepository.java` | Consulta de actualización con bloqueo pesimista de escritura. Serializa modificaciones sobre el mismo departamento. |
| `service/DepartmentService.java` | PATCH carga la fila bloqueada. Traduce a conflicto únicamente SQLSTATE 23505 de los dos índices únicos de Department; propaga otras causas al manejador común. Mantiene comprobaciones preventivas y `saveAndFlush` para detectar carreras dentro de la transacción. |
| `dto/CreateDepartmentRequest.java` | Recorta nombre/código en el constructor antes de `@Valid`; conserva valores predeterminados y DTO separado de JPA. |
| `dto/PatchDepartmentRequest.java` | DTO con setters que distinguen omisión y null explícito, normalización previa y propiedad `isNotEmpty` para validar el objeto vacío. Se desactiva la inferencia del constructor Jackson para que no convierta campos omitidos en null explícito. La autodetección de getters está deshabilitada en este DTO de entrada: `notEmpty` es un detalle interno de validación y, si llega en JSON, se rechaza como propiedad desconocida. |
| `controller/ApiExceptionHandler.java` | Reutiliza ProblemDetailFactory para 404/409/422 de Department, con `code` e `instance`. Conserva únicamente manejadores específicos. |
| `exception/GlobalExceptionHandler.java` | Reconoce errores de conversión de parámetros como 400 `INVALID_PARAMETER`; conserva el comportamiento común de auth y errores internos. |
| `config/SecurityConfig.java` | Expone `Location` por CORS junto a `Retry-After`, para poder leer la URI creada desde un cliente web. Los requisitos de autorización existentes se conservan. |
| `backend/pom.xml` | Incorpora DepartmentPostgresIT al perfil `postgres-it`. Reutiliza las dependencias de pruebas existentes. |
| `controller/DepartmentControllerTest.java` | Amplía validación, permisos, códigos/estructura de errores, UUID inválido, campos desconocidos y fallos 500/503 sin información interna. |
| `service/DepartmentServiceTest.java` | Amplía listado, detalle, inexistentes, límites normalizados, duplicados y clasificación selectiva de errores SQL. Ajusta la simulación del bloqueo. |
| `entity/UuidV7GeneratorTest.java` | Conserva la prueba de versión/variante del identificador, ahora sobre la estrategia Hibernate. La persistencia real también la verifica. |
| `DepartmentPostgresIT.java` | Nueva suite PostgreSQL: migraciones, semillas, UUIDv7, restricciones, ciclo de vida, concurrencia y seguridad con JWT real y permisos actuales en BD. |
| `AuthenticationPostgresIT.java` | Verifica específicamente que V1 se aplicó con éxito, sin asumir que sea la única migración del proyecto. Mantiene las comprobaciones de autenticación. |

No se han reescrito DepartmentController ni DepartmentResponse: ya separaban correctamente transporte, servicio y entidad. Tampoco se han modificado DatabaseJwtAuthenticationConverter, filtros JWT, handlers de seguridad, tablas de autenticación ni las excepciones de dominio existentes.

## 3. Funcionalidad final

Todas las rutas llevan prefijo `/api/v1`. El JWT debe identificar una cuenta activa, con CITIZEN y versión de token vigente, conforme a la seguridad existente.

| Operación | Permisos adicionales | Resultado correcto |
| --- | --- | --- |
| `GET /departments` | Correo verificado | 200, array de departamentos activos e inactivos, ordenado por nombre según la collation PostgreSQL. |
| `GET /departments/{departmentId}` | Correo verificado | 200, departamento solicitado, aunque esté inactivo. |
| `POST /departments` | Correo verificado y ADMIN global | 201, DepartmentDTO y cabecera Location con URI del recurso creado. |
| `PATCH /departments/{departmentId}` | Correo verificado y ADMIN global | 200, DepartmentDTO actualizado. |

El DTO de respuesta contiene `id`, `name`, `code`, `description` y `active`. Las fechas quedan internas; no se exponen entidades JPA.

Nombre obligatorio de 2–120 caracteres; código obligatorio de 2–20, exclusivamente letras ASCII, números, guion y guion bajo. Se aplica `trim()` de Java a nombre/código antes de validar; se conservan espacios interiores del nombre. El código se convierte a mayúsculas con Locale.ROOT. Nombre y código son únicos sin distinguir mayúsculas. Las restricciones de longitud del contrato se refieren a los valores normalizados. Descripción opcional de hasta 500 caracteres, sin recortarla automáticamente. Alta con `active=true` cuando se omite o envía null.

En PATCH, omitir un campo conserva su valor; null explícito devuelve 400. Esto mantiene los campos PATCH no anulables del contrato. Una descripción vacía elimina su contenido, sin convertirlo a SQL NULL. Un objeto vacío, cadenas fuera de límites o códigos inválidos devuelven 422. `active=false` desactiva y conserva UUID e historial; no hay DELETE. Si los valores enviados son idénticos, Hibernate puede no emitir UPDATE ni cambiar updatedAt.

| Estado | Casos y códigos relevantes |
| --- | --- |
| 400 | JSON ilegible, tipos incorrectos o null explícito en PATCH (`INVALID_JSON`); UUID de ruta inválido (`INVALID_PARAMETER`). |
| 401 | JWT ausente o no válido; tratamiento y cabecera Bearer de los handlers existentes. |
| 403 | Correo sin verificar (`EMAIL_NOT_VERIFIED`) o privilegios insuficientes. |
| 404 | UUID válido sin departamento: `DEPARTMENT_NOT_FOUND`. |
| 409 | Nombre/código duplicado: `DEPARTMENT_DUPLICATE`, también ante la carrera detectada por PostgreSQL. |
| 422 | `VALIDATION_ERROR`; propiedades JSON desconocidas: `UNKNOWN_PROPERTY`. |
| 500 | Error interno o integridad no clasificable como duplicado: `INTERNAL_ERROR`, sin SQL privado. |
| 503 | Indisponibilidad tipada de base de datos: `SERVICE_UNAVAILABLE`. |

Los errores utilizan ProblemDetail (`type`, `title`, `status`, `detail`, `instance`, `code`; errores por campo cuando corresponde). No hay dos advice tratando las mismas excepciones específicas.

## 4. Base de datos

Se conserva sin cambios `backend/src/main/resources/db/migration/V2__create_departments.sql`: no hay nuevo checksum ni necesidad de V3 para estos cambios de aplicación. V1 y V2 se aplicaron juntas sobre PostgreSQL 18.6 efímero, con validación Hibernate; no se accedió a Neon production ni se aplicaron migraciones a Neon development.

`departments` tiene UUID PK (default SQL `uuidv7()`), nombre y código NOT NULL, descripción nullable, active NOT NULL DEFAULT TRUE y fechas timestamptz NOT NULL. Los índices `uk_departments_name_normalized` y `uk_departments_code_normalized` aseguran unicidad sobre `lower(...)`. Los CHECK validan nombre no vacío y formato/longitud del código; VARCHAR limita las longitudes máximas. La API normaliza previamente; la BD rechaza códigos sin normalizar. El backend genera UUIDv7 con Hibernate y conserva createdAt al actualizar; updatedAt se mantiene mediante callbacks JPA, no mediante un trigger para escrituras SQL externas.

| Semilla | Código | UUID fijo conservado |
| --- | --- | --- |
| Mantenimiento Urbano | MANT | `0199f6de-0001-7000-8000-000000000001` |
| Limpieza | LIMP | `0199f6de-0002-7000-8000-000000000002` |
| Parques y Jardines | PARQ | `0199f6de-0003-7000-8000-000000000003` |

No se crean FK a equipos, pertenencias ni incidencias todavía no implementados. Los futuros módulos pueden referenciar estas semillas. Deben coordinar la siguiente versión Flyway libre: V2 ya está ocupada.

## 5. Documentación y diagramas

- `docs/api/openapi/paths/catalogos.yaml`: operaciones, permisos, semántica PATCH, Location y errores aplicables; se retiran respuestas de duplicado de las lecturas y 404 del alta/listado.
- `components/schemas/catalogos.yaml`: normalización, nulabilidad de descripción en alta/respuesta, valor predeterminado de active y PATCH no anulable.
- `components/parameters.yaml`: DepartmentId UUIDv7; `base-openapi.yaml`: estado real de implementación. Se revisó `components/responses.yaml` y se reutilizan sus respuestas comunes sin duplicarlas.
- `docs/api/openapi.yaml` y `docs/api/openapi.html`: regenerados con Redocly, sin edición manual. El HTML incluye cambios de salida de la herramienta, además de los cambios del contrato.
- `docs/domain-model.md`, `docs/api/README.md`, `docs/api/domain-openapi.md`, `docs/development.md` y `docs/architecture.md`: corrigen estado, reglas, permisos, persistencia, pruebas y alcance pendiente.
- ADR-0006, ADR-0009 y ADR-0011: revisión de migraciones, ADMIN global y generación UUIDv7, conservando las decisiones existentes.
- `docs/diagrams/relacional.drawio` (ER03) y `clases-simplificado.drawio`: atributos/restricciones de Department actualizados. Relaciones y geometría de los demás módulos se conservan.
- `relacional-03.svg` y `clases-simplificado.svg`: fragmento Department regenerado desde las fuentes mediante el nuevo `docs/diagrams/export-department.ps1`; texto vectorial sustituye la alternativa raster antigua de esa celda. Se renderizaron ambos SVG y se inspeccionaron las celdas sin recortes ni solapamientos. El README de diagramas explica el procedimiento y su limitación.
- `CHANGELOG.md` e informe presente: resultados y motivos del cierre.

## 6. Pruebas y verificaciones realizadas

Última ejecución completa: **31/10/2026, 00:36:03 Europe/Madrid, BUILD SUCCESS, 1 min 44 s** (el log del contenedor muestra `2026-10-30T23:36:03Z`). Incluye la regresión del campo interno `notEmpty` en PATCH. Comando ejecutado desde la raíz:

```sh
docker compose run --rm --no-deps -v /tmp/urbanpulse-departments-build:/app/target -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal backend mvn -B -ntp verify -Ppostgres-it
```

El directorio target aislado evita interferencias con los archivos de compilación del IDE. El socket permite crear los contenedores efímeros y `host.docker.internal` permite alcanzar sus puertos desde el contenedor Maven en Docker Desktop. Las propiedades dinámicas de los tests sustituyen URL/usuario/contraseña JDBC por las del contenedor. No se ejecutó NeonConnectivityIT. No se instaló Java ni Maven en Windows.

| Suite | Pruebas | Fallos / errores / omitidas |
| --- | ---: | --- |
| DepartmentServiceTest | 8 | 0 / 0 / 0 |
| DepartmentControllerTest | 11 | 0 / 0 / 0 |
| UuidV7GeneratorTest | 1 | 0 / 0 / 0 |
| Resto de pruebas unitarias/MVC de auth, correo y seguridad | 35 | 0 / 0 / 0 |
| **Total Surefire** | **55** | **0 / 0 / 0** |
| DepartmentPostgresIT | 5 | 0 / 0 / 0 |
| AuthenticationPostgresIT | 8 | 0 / 0 / 0 |
| EmailVerificationPostgresIT | 12 | 0 / 0 / 0 |
| **Total Failsafe** | **25** | **0 / 0 / 0** |
| **Total verificado** | **80** | **0 / 0 / 0** |

Los cinco escenarios de DepartmentPostgresIT comprueban: ciclo de vida/migraciones/semillas/fechas; restricciones SQL y UUID por defecto; creaciones concurrentes con un éxito y un conflicto; PATCH simultáneos conservando ambos cambios; y JWT firmado real cuyo acceso cambia al verificar el correo, conceder ADMIN y retirarlo en BD. El transporte HTTP se prueba con MockMvc y la cadena real de seguridad, sin abrir un servidor público.

Hubo ejecuciones intermedias fallidas por configuración del entorno de compilación y deserialización PATCH/simulaciones Mockito, corregidas antes de la pasada final. No se rebajaron expectativas ni se retiraron pruebas. Los avisos de instrumentación dinámica de Mockito/Byte Buddy y `Unsafe` de Lombok no hacen fallar el build; pertenecen a las dependencias ya existentes.

Comandos de contrato realmente ejecutados:

```sh
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli@2.16.0 bundle docs/api/openapi/base-openapi.yaml --output docs/api/openapi.yaml
npx --yes @redocly/cli@2.16.0 build-docs docs/api/openapi.yaml --output docs/api/openapi.html
```

Lint válido, repetido el 31/10: **0 errores y 2 advertencias** (`info-license`, servidor localhost). No se inventa una licencia ni un servidor desplegado para eliminarlas. Bundle y HTML generados correctamente. Se ha ejecutado `git diff --check`, revisado marcadores de conflicto, enlaces locales y archivos modificados. No se han añadido secretos reales; las credenciales de tests son datos sintéticos.

## 7. Compatibilidad con main

Se comprobó el estado inicial, se actualizó la referencia remota mediante `git fetch origin main` (repetido el 31/10) y se compararon ambas bases. `main...HEAD`: 0 commits por integrar desde main y 5 propios; `origin/main...HEAD`: 0 por integrar desde origin/main y 4 propios. El main local estaba un commit por detrás de origin/main. No se modificó main ni se realizó merge/rebase.

Los cuatro commits propios son la funcionalidad inicial, formato OpenAPI, integración previa y corrección `58a9c6c`. Origin/main ya es antecesor de la rama: no hay divergencia de historial con esa referencia. No se detectaron incompatibilidades funcionales del módulo con la autenticación incorporada; pasan sus suites completas. Esto no garantiza compatibilidad con futuros commits que lleguen después de esta revisión.

Los únicos cambios comunes de ejecución son la cabecera CORS Location y el 400 de conversión de parámetros. El DTO y las semillas dan a los compañeros una referencia estable; no se implementan sus módulos ni se cambia el mecanismo de permisos.

## 8. Pendientes reales y límites

1. **Revisión interactiva de diagramas pendiente.** CONTRIBUTING pide «abrir los `.drawio` en diagrams.net; comprobar las cuatro páginas ER y la página de clases». El 30/10 la herramienta de control del navegador se detuvo al no poder determinar la URL activa con suficiente confianza. El 31/10 se pudo abrir diagrams.net en el navegador integrado, pero su selector no produjo una selección de archivo utilizable; un enlace servido localmente tampoco fue accesible desde ese navegador. Se detuvo el servidor temporal. La comprobación de XML y el render local de SVG se realizaron, pero no se presentan como cumplimiento de esa apertura manual. La comparación estructural con HEAD confirma que únicamente cambió la celda Department de cada fuente y que se conservan las cuatro páginas ER y la página de clases.
2. Los diagramas de correo y C4 ya tenían pendientes documentados en main. Se conservan identificados en `docs/diagrams/README.md`; no se rediseñan módulos ajenos.
3. No se ha probado un despliegue en Neon ni el frontend. No son pruebas pendientes del backend aislado de Department: son validaciones de despliegue/otros módulos. Antes de desplegar, comprobar historial/checksums reales de Flyway, especialmente la V1 que main amplió antes de su primer despliegue; no ejecutar `repair` ni sobrescribir historia a ciegas.
4. Las dos advertencias de Redocly y los avisos de dependencias Java descritos no bloquean las pruebas. No hay dependencia nueva de runtime ni migración nueva.

Category, Team, DepartmentMembership, invitaciones, DELETE, paginación/filtros no contratados y pantallas frontend quedan fuera del alcance. No se declara validado todo el tag Catálogos.

## 9. Propuesta de Pull Request

**Título:** `feat(catalogos): completar la gestión de departamentos y su integración con seguridad`

Descripción siguiendo la plantilla del repositorio:

### Descripción

Completa Department dentro de Catálogos: listado/detalle, creación y PATCH con correo verificado y ADMIN en escritura. Corrige PATCH vacío/null, normalización, errores ProblemDetail y conflictos concurrentes; adopta UUIDv7 Hibernate según ADR-0011. Conserva V2 y los UUID de semillas y permite desactivación sin borrado.

Revisados los cuatro contratos y sus DTO, Department en ambas vistas de dominio, índices únicos, campos, nulabilidad y relaciones futuras. Se conservan equipos y pertenencias como diseño. Los cambios compartidos son el manejo 400 de parámetros y Location por CORS; las pruebas de autenticación y correo siguen pasando. Contrato modular, artefactos derivados, documentación y fuentes/exportaciones de Department actualizados.

### Ámbito

- [x] Backend
- [x] Documentación
- [ ] Frontend
- [ ] Infraestructura / CI

### Tipo de cambio

- [x] Nueva funcionalidad
- [x] Corrección de error
- [x] Refactorización
- [x] Documentación
- [x] Pruebas
- [ ] CI/CD

### Pruebas realizadas

`mvn -B -ntp verify -Ppostgres-it` dentro de Compose con target aislado y Testcontainers: 55 unitarias/MVC y 25 PostgreSQL, sin fallos, errores ni omitidas. Lint Redocly válido con dos advertencias; bundle/HTML regenerados. `git diff --check`, XML y render local de los diagramas comprobados. Comando completo y alcance en este informe.

### Checklist

- [x] El cambio compila.
- [x] Las pruebas correspondientes se han ejecutado.
- [x] La documentación se ha actualizado.
- [x] No se han añadido secretos ni datos personales reales.
- [x] Se ha valorado la compatibilidad con la API existente.
- [x] Se han revisado los archivos modificados.
- [ ] Completar apertura manual de las cuatro páginas ER y la página de clases en diagrams.net conforme a CONTRIBUTING.

### Issue relacionado

No se proporcionó número de issue; no se inventa un `Closes`.

## 10. Veredicto

**El backend de Department está implementado y supera la verificación completa, sin fallos funcionales conocidos. La rama está preparada para revisión técnica, pero no declaro cerrada al 100 % la lista de requisitos de la PR mientras quede la comprobación interactiva de diagramas exigida por CONTRIBUTING.** El impedimento concreto es la limitación del control del navegador indicada arriba; no hay pruebas PostgreSQL pendientes.

Los cambios permanecen sin stage y sin commit. No se han ejecutado git add, commit, push, cambio de rama, merge ni rebase, ni se ha creado/publicado una PR. El usuario conserva la revisión y autorización de esas operaciones.
