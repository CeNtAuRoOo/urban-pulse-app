# Informe de cierre de Category — UrbanPulse

Fecha: **02/11/2026**. Rama de trabajo: `feature/category-management`. El árbol estaba limpio al comenzar la implementación inicial; la última revisión partió de esos cambios aún sin confirmar. Rama y estado comprobados con Git. Trabajo local, sin commits, push, merge ni Pull Request. Los resultados vigentes están en la [última revisión](#10-última-revisión-antes-de-commits); las secciones 5 y 6 conservan las evidencias del cierre inicial.

## 1. Alcance y decisiones

Implementadas cuatro operaciones de Category con entidad JPA, repositorio, servicio transaccional, controlador, tres DTO, errores específicos y Flyway V3. Se mantiene la arquitectura por capas y las dependencias existentes. Se estudiaron las guías generales, OpenAPI modular, ambas vistas gráficas, ADR-0005/0006/0009/0010/0011 y el módulo Department con su informe de cierre.

Category tiene únicamente `id`, `name`, `description` y `active`. A diferencia de Department, no tiene `code`, fechas internas ni semillas. Las operaciones contratadas no necesitan marcas temporales; añadirlas no resolvería RF18. Incident y su FK quedan pendientes; el diseño sigue permitiendo una incidencia sin categoría. Desactivar conserva identidad y futuras referencias históricas.

La fuente relacional ya proponía nombre único y cuatro atributos. Se concretaron los límites y la nulabilidad de descripción en ambos diagramas. El texto general del dominio que exigía createdAt a todas las entidades se precisó: Category no lo necesita. No se introduce una decisión arquitectónica nueva ni se cambia C4.

## 2. Reglas definitivas

- UUIDv7 generado por Hibernate (`@GeneratedValue`, `@UuidGenerator(VERSION_7)`), con `uuidv7()` SQL como default para inserciones externas.
- Nombre obligatorio, no blanco, de 2–100 puntos de código Unicode después de `trim()`; conserva espacios interiores. Descripción opcional hasta 500, sin trim. `CodePointLength` del Hibernate Validator ya instalado evita que un carácter suplementario cuente doble en Java frente a PostgreSQL/OpenAPI.
- Nombre único sin distinguir mayúsculas, también entre categorías inactivas. Repositorio e índice utilizan `lower(name)` con las reglas de PostgreSQL; no se eliminan acentos ni normalizan formas Unicode equivalentes.
- Creación: descripción omitida o null → SQL NULL; active omitido o null → true; false se conserva. Esta nulabilidad sigue el precedente Department y está explícita en OpenAPI.
- PATCH: omisión conserva; null JSON explícito → 400 `INVALID_JSON`; objeto vacío → 422 `VALIDATION_ERROR`. `description: ""` limpia sin convertir a null. `active: false` desactiva y true reactiva. Nombre idéntico o cambio de mayúsculas del mismo recurso no entra en conflicto consigo mismo.
- Listado de activas e inactivas, sin paginación, por nombre ascendente según collation PostgreSQL y UUID como desempate. Detalle disponible para categorías inactivas.
- Bloqueo `PESSIMISTIC_WRITE` de la fila durante PATCH: un segundo parche lee el estado actualizado tras esperar al primero y conserva los campos omitidos. En cambios concurrentes del mismo campo prevalece la última escritura serializada; no se añade ETag ni control de versiones del cliente.
- Comprobación preventiva de unicidad más índice único. `saveAndFlush` permite traducir a 409 las carreras dentro de la transacción; solo SQLSTATE `23505` del índice `uk_categories_name_normalized` se clasifica como duplicado. Otras violaciones se propagan al manejo común.

## 3. Contrato HTTP y seguridad

| Método | Ruta                              | Permisos                           | Éxito                            |
| ------ | --------------------------------- | ---------------------------------- | -------------------------------- |
| GET    | `/api/v1/categories`              | JWT válido y correo verificado     | 200, array completo              |
| GET    | `/api/v1/categories/{categoryId}` | JWT válido y correo verificado     | 200, recurso                     |
| POST   | `/api/v1/categories`              | Lo anterior y ADMIN global vigente | 201, recurso y Location absoluta |
| PATCH  | `/api/v1/categories/{categoryId}` | Lo anterior y ADMIN global vigente | 200, recurso actualizado         |

La respuesta contiene `id`, `name`, `description` (puede ser null) y `active`. SecurityConfig incluye expresamente las rutas de Category en las reglas de Catálogos. El conversor JWT existente consulta estado, versión del token, verificación y roles globales actuales en PostgreSQL. Las etiquetas x-roles no aplican autorización. Los campos adicionales, incluido `roles` o `code`, se rechazan; el cuerpo no concede permisos. Las reglas existentes de auth y Department se conservan.

| Estado | Casos y códigos                                                                                                                                       |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | JSON malformado, tipos incorrectos, claves duplicadas/contenido adicional o null en PATCH: `INVALID_JSON`; UUID de ruta inválido: `INVALID_PARAMETER` |
| 401    | JWT ausente, inválido, caducado o revocado: `UNAUTHORIZED`, con WWW-Authenticate Bearer                                                               |
| 403    | Cuenta sin verificar: `EMAIL_NOT_VERIFIED`; privilegios insuficientes: `FORBIDDEN`                                                                    |
| 404    | UUID válido inexistente: `CATEGORY_NOT_FOUND`                                                                                                         |
| 409    | Nombre duplicado, incluso bajo concurrencia: `CATEGORY_DUPLICATE`                                                                                     |
| 422    | Datos inválidos/PATCH vacío: `VALIDATION_ERROR`; campos desconocidos: `UNKNOWN_PROPERTY`                                                              |
| 500    | Fallo inesperado o integridad no clasificada: `INTERNAL_ERROR`                                                                                        |
| 503    | Indisponibilidad identificada de BD: `SERVICE_UNAVAILABLE`                                                                                            |

Los errores reutilizan `ApiException`, `GlobalExceptionHandler` y `ProblemDetailFactory`: `application/problem+json`, estado, código, detalle seguro e instance con la ruta. No se modifica ApiExceptionHandler ni se crea otro advice. No se devuelven SQL, causas internas, credenciales ni tokens. Se mantiene la configuración existente que desactiva el log de errores SQL de Hibernate.

## 4. Persistencia

V3 crea únicamente `categories`: PK UUID, name VARCHAR(100) NOT NULL, description VARCHAR(500) nullable y active BOOLEAN NOT NULL DEFAULT TRUE. CHECK de longitud, nombre no blanco (incluido whitespace Unicode de Java) y nombre recortado; índice único `lower(name)` sin filtro por estado. SQL rechaza escrituras externas sin normalizar; la API las normaliza antes de persistir. No se implementan relaciones a tablas futuras.

V1/V2 se conservan sin cambios respecto a HEAD; `ddl-auto=validate` permanece intacto. Flyway V1, V2 y V3 se han aplicado conjuntamente en PostgreSQL **18.6** efímero mediante Testcontainers. No se ha cargado el archivo privado `.env.development`, ejecutado NeonConnectivityIT, arrancado el servicio normal contra Neon ni modificado ninguna base compartida.

## 5. Pruebas del cierre inicial (evidencia histórica)

Ejecución completa del cierre inicial: **02/11/2026, 11:13:19 Europe/Madrid** (`10:13:19Z` en el log), **BUILD SUCCESS**, 1 min 22 s. Se compilaron fuentes principales y tests, se empaquetó el JAR y se ejecutaron Surefire y Failsafe. La revisión posterior añade una prueba MVC y sustituye la comprobación temporal de bloqueo; sus resultados figuran en la sección 10.

Comando Maven ejecutado desde `/app`, que monta `backend/`:

```sh
mvn -B -ntp verify -Ppostgres-it
```

Comando Docker reproducible desde la raíz, en PowerShell, usando la imagen de desarrollo existente (Java 25/Maven 3.9.16):

```powershell
docker run --rm --mount "type=bind,source=$PWD/backend,target=/app" --mount type=volume,source=urban-pulse-app_maven-cache,target=/root/.m2 --mount type=volume,source=urbanpulse-categories-build,target=/app/target --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /app urban-pulse-app-backend:latest mvn -B -ntp verify -Ppostgres-it
```

El target aislado evita interferir con la compilación del IDE. No se pasan variables ni credenciales de Neon al contenedor. Las propiedades dinámicas de las suites apuntan únicamente a sus contenedores PostgreSQL efímeros. El socket Docker y host.docker.internal permiten usar Testcontainers desde Maven en Docker Desktop.

| Suite                                                   | Pruebas | Fallos / errores / omitidas |
| ------------------------------------------------------- | ------: | --------------------------- |
| CategoryServiceTest                                     |       7 | 0 / 0 / 0                   |
| CategoryControllerTest                                  |      15 | 0 / 0 / 0                   |
| DepartmentServiceTest + DepartmentControllerTest        |      19 | 0 / 0 / 0                   |
| Resto de unitarias/MVC (auth, correo, seguridad y UUID) |      36 | 0 / 0 / 0                   |
| **Total Surefire**                                      |  **77** | **0 / 0 / 0**               |
| CategoryPostgresIT                                      |       6 | 0 / 0 / 0                   |
| DepartmentPostgresIT                                    |       5 | 0 / 0 / 0                   |
| AuthenticationPostgresIT                                |       8 | 0 / 0 / 0                   |
| EmailVerificationPostgresIT                             |      12 | 0 / 0 / 0                   |
| **Total Failsafe**                                      |  **31** | **0 / 0 / 0**               |
| **Total**                                               | **108** | **0 / 0 / 0**               |

Los escenarios PostgreSQL de Category comprueban migraciones y validación JPA, UUIDv7, persistencia y ciclo de vida, orden del listado, restricciones directas SQL, límites Unicode, nombres reservados por inactivas, JWT firmado con cambios efectivos de verificación/ADMIN y revocación, creaciones concurrentes, renombrados concurrentes con rollback del perdedor y espera del bloqueo durante PATCH sin perder campos. MVC cubre las cuatro operaciones, Location, JSON estricto, permisos y errores 400/401/403/404/409/422/500/503. Los tests de servicio comprueban normalización, valores predeterminados, límites, inexistentes, duplicados y traducción selectiva de integridad.

Log local de la ejecución final: `backend/target/category-verify.log` (ignorado). Informes Surefire/Failsafe en el volumen Docker `urbanpulse-categories-build`, directorios `surefire-reports` y `failsafe-reports`. Las advertencias de Unsafe/Lombok, carga de agentes Mockito y acceso nativo JNA provienen de dependencias ya existentes y no fallaron el build. Los logs WARN/ERROR de fallos simulados de correo/BD corresponden a escenarios esperados de las pruebas.

## 6. Contrato, diagramas y verificaciones del cierre inicial

Comandos Redocly **2.16.0**, realmente ejecutados:

```sh
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli@2.16.0 bundle docs/api/openapi/base-openapi.yaml --output docs/api/openapi.yaml
npx --yes @redocly/cli@2.16.0 build-docs docs/api/openapi.yaml --output docs/api/openapi.html
```

Lint: **0 errores, 2 advertencias previas**, por falta de licencia y servidor localhost. No se inventan licencia ni despliegue para ocultarlas. Bundle y HTML regenerados desde el modular; no editados a mano. Se conserva la versión documental 0.4.0 y el prefijo HTTP /api/v1. Se concretan validación/nulabilidad, permisos, códigos específicos, Location e inactivas; se eliminan errores 404/409 no aplicables al listado y 404 al alta, y se añade 503.

Actualizadas las celdas Category de `relacional.drawio` (ER01) y `clases-simplificado.drawio`; exportaciones `relacional-01.svg` y `clases-simplificado.svg` regeneradas desde esas fuentes con `export-category.ps1`. Se conserva la geometría y el diseño futuro de la relación opcional con Incident. XML parseado y ambos SVG renderizados localmente con Sharp; las dos celdas se han inspeccionado visualmente como PNG sin recortes ni solapamientos. No se ha realizado apertura manual completa en diagrams.net ni se han cambiado los diagramas C4.

La primera invocación npm falló por restricciones de red del entorno; la repetición autorizada funcionó. La exportación requirió habilitar scripts solo en el proceso PowerShell y corregir la lectura UTF-8, sin cambiar la política global del equipo. La primera suite completa también pasó; se repitió tras añadir coherencia y pruebas Unicode. No hay comprobaciones omitidas presentadas como superadas.

Verificaciones finales: `git diff --check` sin errores; `git diff --exit-code HEAD --` sobre V1, V2 y application.properties confirma que no cambiaron. Comparación estructural del bundle con HEAD: todas las rutas y esquemas ajenos a Category permanecen iguales. Comparación XML: solo cambian las etiquetas de Category en ambas fuentes y su fragmento en cada SVG, preservando geometría y relaciones. Revisados los 37 archivos modificados/nuevos, sin marcadores de conflicto ni firmas comunes de secretos; no se incluyen artefactos de target ni archivos ajenos al alcance.

## 7. Inventario de archivos

Rutas Java principales relativas a `backend/src/main/java/es/grupo18/urbanpulse/`:

- Nuevos: `entity/Category.java`, `repository/CategoryRepository.java`, `service/CategoryService.java`, `service/CategoryNotFoundException.java`, `service/DuplicateCategoryException.java`, `controller/CategoryController.java`, `dto/CreateCategoryRequest.java`, `dto/PatchCategoryRequest.java`, `dto/CategoryResponse.java`.
- Modificado: `config/SecurityConfig.java`, únicamente para incluir Category en la autorización de Catálogos.
- Nueva migración: `backend/src/main/resources/db/migration/V3__create_categories.sql`.
- Nuevas pruebas, relativas a `backend/src/test/java/es/grupo18/urbanpulse/`: `service/CategoryServiceTest.java`, `controller/CategoryControllerTest.java`, `CategoryPostgresIT.java`.
- Modificado `backend/pom.xml`: incorpora CategoryPostgresIT a Failsafe; sin nuevas dependencias.
- OpenAPI modificado: `docs/api/openapi/base-openapi.yaml`, `paths/catalogos.yaml`, `components/schemas/catalogos.yaml`, `components/parameters.yaml`; derivados `docs/api/openapi.yaml` y `docs/api/openapi.html`.
- Diagramas modificados: `docs/diagrams/relacional.drawio`, `clases-simplificado.drawio`, `relacional-01.svg`, `clases-simplificado.svg`, `README.md`; nuevo `docs/diagrams/export-category.ps1`.
- Guías modificadas: `README.md`, `CHANGELOG.md`, `docs/domain-model.md`, `docs/architecture.md`, `docs/development.md`, `docs/api/README.md`, `docs/api/domain-openapi.md`.
- Índices sincronizados en la última revisión: `docs/README.md` y `docs/adr/README.md`.
- ADR existentes actualizados en su estado de implementación: `docs/adr/0006-flyway.md`, `docs/adr/0011-identificadores-uuidv7.md`; sin nuevas decisiones.
- Nuevo informe: `docs/categories-finalization.md`.

## 8. Límites y pendientes

**RF18 permanece pendiente.** No existe un historial de auditoría que identifique al administrador responsable de altas, modificaciones o desactivaciones. Debe acordarse un mecanismo transversal coherente con AuditEvent y ADR-0010; las autorizaciones y los UUID no lo sustituyen.

No se ha probado despliegue en Neon ni frontend. Antes de un futuro despliegue autorizado, revisar entorno e historial/checksums reales de Flyway; no ejecutar repair ni sobrescribir migraciones. No se incorporan Incident, equipos, pertenencias, invitaciones, DELETE/PUT, filtros, paginación ni funcionalidades de IA. La revisión visual completa de las cuatro páginas en diagrams.net queda para el revisor; los pendientes de EmailVerification/SMTP/C4 ya documentados son ajenos a Category. El cotejo no supone compatibilidad garantizada con futuros cambios de main.

## 9. Propuesta de commits y checklist del futuro PR

Propuesta (no ejecutada):

1. `feat(catalogos): implementar categorías con persistencia y autorización`
2. `test(catalogos): verificar categorías y concurrencia en PostgreSQL`
3. `docs(catalogos): sincronizar contrato y diagramas de categorías`

Checklist de revisión hacia main:

- [x] Cuatro operaciones, DTO y semántica de null/omisión coherentes.
- [x] Correo verificado y ADMIN global exigidos en servidor; regresión de auth/Department superada.
- [x] V3 aislada, V1/V2 conservadas, Hibernate validate y UUIDv7 comprobados.
- [x] Unicidad SQL y bloqueo transaccional probados con PostgreSQL efímero.
- [x] 109 pruebas completas sin fallos, errores ni omitidas en la última revisión (sección 10).
- [x] OpenAPI validado y derivados regenerados; fuentes y exportaciones Category sincronizadas.
- [x] RF18 y límites documentados sin declararlos resueltos.
- [x] Diff sin errores de whitespace; V1/V2 y contratos de otros módulos conservados.
- [ ] Revisar visualmente el dominio completo en diagrams.net si se requiere antes de integrar.
- [ ] Revisar cambios que hayan llegado a main y coordinar la numeración Flyway antes del PR.
- [ ] Aprobar la propuesta de commits y autorizar por separado cualquier push o apertura del PR.

## 10. Última revisión antes de commits

Revisión acotada solicitada el **02/11/2026**, realizada únicamente en `feature/category-management`. Se conservan la implementación funcional, las dependencias y V1/V2/V3. Los hashes SHA-256 de las tres migraciones coinciden antes y después de esta revisión, incluida V3 todavía sin seguimiento. No se modifica main ni se realizan commits, push, merge o PR.

### Archivos modificados en esta revisión

| Archivo                                                                              | Corrección                                                                                                                                                                                                                                                                                                                               |
| ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/README.md`                                                                     | Estado vigente de autenticación, Department y Category, enlaces a ambos informes y UUIDv7 probado para User/Department/Category. Se separan los resultados históricos (23, 55, 80 y 108 pruebas) de la ejecución actual. Se elimina la atribución anacrónica de los cinco endpoints auth a la comprobación de infraestructura del 15/10. |
| `docs/adr/README.md`                                                                 | ADR-0009 distingue Department implementado de pertenencias/roles departamentales y equipos pendientes; ADR-0011 refleja los tres tipos con UUIDv7 probado. Estado de V1/V2/V3, Category y evidencia histórica de correo explicitados.                                                                                                    |
| `backend/src/test/java/es/grupo18/urbanpulse/CategoryPostgresIT.java`                | Refuerzo de `rowLockMakesPartialUpdateWaitAndPreserveCommittedFields` mediante observación del bloqueo real en PostgreSQL.                                                                                                                                                                                                               |
| `backend/src/test/java/es/grupo18/urbanpulse/controller/CategoryControllerTest.java` | Nueva prueba `responsesIncludeExplicitNullDescriptionAsRequiredByOpenApi` para las cuatro operaciones.                                                                                                                                                                                                                                   |
| `docs/categories-finalization.md`                                                    | Resultados de esta revisión, alcance de archivos nuevos y limitaciones, manteniendo las ejecuciones anteriores como históricas.                                                                                                                                                                                                          |

La espera anterior de 300 ms solo acreditaba que un Future no había terminado; un hilo lento podía cumplir esa condición sin haber intentado bloquear la fila. Ahora ambas operaciones se ejecutan en transacciones con PID PostgreSQL identificado. Antes de liberar la primera, se consulta `pg_blocking_pids` hasta observar que el PID de la segunda está bloqueado por el de la primera. Después se liberan las transacciones y se verifican ambos cambios persistidos y la descripción preservada.

El sondeo cada 20 ms y el límite de 10 s acotan la espera: no constituyen la condición de éxito, que exige evidencia positiva del bloqueo. Se conserva una salvaguarda de 30 s para la primera transacción y la liberación en finally. No se añaden bibliotecas, servicios auxiliares ni cambios de producción. Un entorno que no alcance el estado esperado dentro del límite hará fallar el test explícitamente, en lugar de dar por demostrado el bloqueo.

OpenAPI declara description **obligatoria en la respuesta y nullable**. La nueva prueba MVC verifica tanto la presencia de la propiedad (`hasJsonPath`) como su valor JSON null (`nullValue`) en listado, detalle, creación y PATCH. Esto distingue un null explícito de una propiedad omitida. **No se detecta discrepancia**: DTO, serialización y contrato se conservan sin cambios.

### Verificaciones ejecutadas y resultados

Se repitió el comando Docker completo de la sección 5, que ejecuta desde el montaje de `backend/`:

```sh
mvn -B -ntp verify -Ppostgres-it
```

Resultado: **BUILD SUCCESS**, **02/11/2026 11:36:57 Europe/Madrid** (`10:36:57Z`), **1 min 15 s**. Incluye compilación, empaquetado, pruebas unitarias/MVC y las cuatro suites de PostgreSQL 18.6 efímero. No se ejecutó contra Neon ni se cargó `.env.development`.

| Suite / grupo                                                | Pruebas | Fallos / errores / omitidas |
| ------------------------------------------------------------ | ------: | --------------------------- |
| CategoryControllerTest                                       |      16 | 0 / 0 / 0                   |
| CategoryServiceTest                                          |       7 | 0 / 0 / 0                   |
| Resto de unitarias/MVC (incluidos auth, correo y Department) |      55 | 0 / 0 / 0                   |
| **Surefire**                                                 |  **78** | **0 / 0 / 0**               |
| CategoryPostgresIT                                           |       6 | 0 / 0 / 0                   |
| DepartmentPostgresIT                                         |       5 | 0 / 0 / 0                   |
| AuthenticationPostgresIT                                     |       8 | 0 / 0 / 0                   |
| EmailVerificationPostgresIT                                  |      12 | 0 / 0 / 0                   |
| **Failsafe**                                                 |  **31** | **0 / 0 / 0**               |
| **Total vigente**                                            | **109** | **0 / 0 / 0**               |

También se ejecutó:

```sh
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
git diff --check
```

OpenAPI: **0 errores, 2 advertencias previas** (`info-license` y servidor localhost). Diff de archivos con seguimiento: sin errores de whitespace. Los logs locales, ignorados por Git, son `backend/target/category-final-review-verify.log` y `backend/target/category-final-review-openapi.log`. Persisten los avisos conocidos de dependencias Java; no fallan el build.

### Revisión de archivos nuevos y límites

Se enumeraron expresamente los **16 archivos sin seguimiento** con `git ls-files --others --exclude-standard`, incluidos los 15 archivos de la implementación de Category y el archivo preexistente `repomix-output.xml`. Se revisaron entidad, DTO, controlador, servicio, repositorio, excepciones, V3, las tres clases de pruebas, informe y exportador gráfico; no se detectaron cambios funcionales necesarios fuera del alcance.

Además del diff normal, se comparó cada archivo nuevo contra un archivo vacío usando:

```sh
git -c core.autocrlf=false -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --no-index --check -- backend/target/review-empty.tmp <archivo>
```

Se revisaron expresamente los **15 archivos nuevos de la implementación de Category**, incluyendo entidad, DTO, controlador, servicio, repositorio, excepciones, migración V3, tres clases de pruebas, informe de cierre y exportador gráfico. No se detectaron cambios funcionales necesarios fuera del alcance.

Todas las verificaciones solicitadas se ejecutaron; ninguna prueba de esta revisión quedó sin ejecutar. No se regeneraron OpenAPI ni los SVG porque sus fuentes no cambiaron, y no se repitió la revisión visual previa. No se probaron despliegue en Neon, frontend ni una apertura completa en diagrams.net: siguen fuera de esta revisión. RF18 permanece pendiente. La comparación con el estado inicial de esta revisión confirma que solo se modificaron los cinco archivos enumerados arriba.

## 11. Estado posterior a la revisión (02/11/2026)

Las referencias anteriores al trabajo local sin commits ni push, así como las propuestas de commits, describen el estado del proyecto en el momento de aquellas revisiones. Se conservan como evidencia histórica de la implementación y sus verificaciones.

Posteriormente se crearon y publicaron en `feature/category-management` los commits `9c240ed` (implementación), `d0c6e33` (pruebas), `a6fb5a9` (documentación) y `9328462` (correcciones documentales).

Tras su publicación, la rama quedó sincronizada con GitHub y preparada para abrir un Pull Request contra `main`. La integración en `main`, el despliegue en Neon y la auditoría administrativa RF18 permanecen fuera del alcance de este cierre.
