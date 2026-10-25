# Modelo de dominio de UrbanPulse (versión base v1, revisión de autenticación)

> **Estado: diseño inicial compartido; NO validado entidad por entidad ni implementado.** Modelo inicial incorporado el 18/10/2026; revisión del tag Autenticación acordada el 25/10/2026, **pendiente de implementar y de sincronizar manualmente los diagramas**. Las figuras no son migraciones SQL ni un inventario de clases Java ya existentes. El modelo se verifica y actualiza incrementalmente al implementar cada tag de la API.

## 1. Fuentes, alcance y cómo interpretar el modelo

La referencia de requisitos es «UrbanPulse: Plataforma cloud para la gestión inteligente de incidencias urbanas» (_Descripción inicial del proyecto_). Las decisiones se registran en los [ADR](adr/README.md); el [contrato OpenAPI modular](api/README.md) y los [diagramas](diagrams/README.md) son propuestas revisables. Los acuerdos base se sincronizaron con OpenAPI `0.2.0` y la revisión de autenticación propone OpenAPI `0.3.0`. **El cambio de contrato no demuestra la existencia de código**.

En particular, consultar la [especificación del diseño de autenticación](auth-design.md), que recoge por separado la primera implementación prevista, las mejoras futuras y las validaciones exigidas a Codex.

**Fuera de esta fase:** los apartados opcionales del enunciado marcados en rojo, especialmente analista avanzado, entrenamiento/evaluación de modelos, hotspots predictivos y RAG. El análisis estadístico operativo e informes permanecen posibles mediante consultas. La prioridad asistida puede empezar con reglas sin añadir IA.

Se parte de un monolito Spring Boot, PostgreSQL en Neon, fotografías en Neon Object Storage y frontend Expo web/móvil. **No hay entidades JPA, repositorios de negocio, endpoints funcionales ni migraciones del dominio en el estado actual de referencia.**

## 2. Los dos diagramas y su función

| Vista                           | Archivo                                                                      | Qué representa                                                              | Cómo usarla                                                                                         |
| ------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Modelo relacional lógico        | [`diagrams/relacional.drawio`](diagrams/relacional.drawio)                   | Tablas candidatas, PK/FK, relaciones y cardinalidades; cuatro páginas       | Refinar restricciones SQL y migraciones Flyway solo en el ámbito implementado.                      |
| Catálogo simplificado de clases | [`diagrams/clases-simplificado.drawio`](diagrams/clases-simplificado.drawio) | Entidades Java previstas, campos, tipos, enums, objetos `@Embeddable` y DTO | Referencia de estructura, deliberadamente sin conexiones UML; las relaciones se consultan en el ER. |

Páginas ER: (1) incidencias/evidencias, (2) tramitación, (3) identidad/organización, (4) territorio/contexto. Los dibujos son fuentes de diseño editables; una `@Embeddable` no requiere tabla propia y un DTO no implica una entidad JPA.

**Cambios manuales pendientes en los diagramas de identidad:** añadir a `User` `emailVerified: boolean` y `tokenVersion: long`, representar sus columnas SQL y valores iniciales; indicar `NotificationPreferences` en `true` por defecto; mantener `UserRole` explícita y anotar `CITIZEN` como rol base de toda cuenta. Ver instrucciones específicas de revisión del tag. Hasta que se editen y exporten `.drawio`/`.svg`, **las imágenes existentes seguirán representando el diseño v1 anterior y no deben presentarse como sincronizadas**.

**Convención:** nombres de conceptos y campos del dominio en inglés (`Incident`, `StatusChange`, `DepartmentMembership`); documentación en español. Entidades, enums, objetos de valor y DTO se distinguen por sus responsabilidades, no solo por su ubicación en el dibujo.

## 3. Conceptos centrales y responsabilidades

### 3.1. Incidencias y ubicación

| Concepto                   | Responsabilidad                                                                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `Incident`                 | Reporte central: informante, descripción, estado, prioridad, categoría opcional, departamento actual opcional, ubicación, fechas y control de versión. |
| `Category`                 | Catálogo de clasificación; puede registrarse una incidencia sin categoría.                                                                             |
| `Attachment`               | Metadatos de fotografías y referencia privada a objeto; el binario no reside en PostgreSQL.                                                            |
| `Location`, `Coordinates`  | Objetos de valor `@Embeddable`; coordenadas obligatorias, precisión opcional, dirección/distrito/barrio derivados opcionales; sin tablas propias.      |
| `District`, `Neighborhood` | Referencias territoriales para geocodificación, filtrado y estadísticas; cada barrio pertenece a distrito.                                             |
| `DuplicateLink`            | Enlace confirmado entre dos incidencias por personal autorizado, preservando los reportes.                                                             |
| `DuplicateSuggestionDTO`   | Sugerencia calculada por proximidad espacial/temporal y posiblemente categoría; no entidad ni confirmación automática.                                 |

La incidencia puede crearse sin datos externos o activo urbano identificado. La fotografía requiere autorización, MIME, tamaño y tratamiento de privacidad a revisar en Evidencias (ADR-0004).

### 3.2. Identidad y organización

| Concepto                       | Responsabilidad en diseño                                                                                                   |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| `User`                         | Cuenta única con credenciales protegidas, roles, estado y preferencias. Revisión auth: `emailVerified` y `tokenVersion`.    |
| `Role`, `UserRole`, `RoleCode` | Catálogo y asignación N:M explícita de roles GLOBAL, con ámbito GLOBAL/DEPARTMENT derivado de `RoleCode` (sin `RoleScope`). |
| `Department`                   | Unidad municipal que recibe incidencias.                                                                                    |
| `DepartmentMembership`         | Vínculo temporal usuario–departamento–rol DEPARTMENT; delimita actuación.                                                   |
| `Team`, `TeamMembership`       | Equipos reutilizables con pertenencias históricas; desactivar no borra trabajos.                                            |
| `StaffInvitation`              | Alta/incorporación controlada de personal, código de un uso almacenado mediante hash/HMAC.                                  |
| `NotificationPreferences`      | Valor embebido en User (`inApp`, `email`, `push`). Valores iniciales acordados: **todos true**.                             |

**Roles iniciales:** globales `CITIZEN`, `OPERATOR`, `ADMIN`; departamentales `DEPARTMENT_COORDINATOR`, `TECHNICIAN`. **Toda cuenta posee `CITIZEN`**, incluidos operadores, administradores, coordinadores y técnicos; los roles municipales se acumulan. Se mantiene un único `User` sin jerarquías de clases. `ANALYST` está aplazado.

El operador central valida, prioriza y deriva sin adscripción departamental. El coordinador decide equipo/técnico en sus departamentos y el técnico ejecuta trabajos autorizados. Tener un rol no concede acceso indiscriminado: se comprueba también cuenta activa, pertenencia departamental, asignación, titularidad y estado. `TeamMembership` referencia una `DepartmentMembership` del mismo departamento y rol técnico; el historial no se borra.

**Revisión específica de `User` para `auth`:**

| Campo previsto           | Tipo lógico               | Requisito acordado                                                                       |
| ------------------------ | ------------------------- | ---------------------------------------------------------------------------------------- |
| `id`                     | `UUID`                    | UUIDv7 generado en Hibernate, columna `uuid`.                                            |
| `displayName`            | `String`                  | Longitud de API 2..120, pendiente de comprobar con validación y SQL.                     |
| `email`                  | `String`                  | Normalizado, único sin distinguir mayúsculas, no permite duplicados bajo concurrencia.   |
| `passwordHash`           | `String`                  | Hash BCrypt; nunca API ni logs.                                                          |
| `active`                 | `boolean`                 | Cuenta activa al registrarse; se comprueba en accesos protegidos.                        |
| `emailVerified`          | `boolean`                 | `false` inicial; flujo futuro de correo lo pondrá a true tras confirmar titularidad.     |
| `tokenVersion`           | `long`                    | `0` inicial; se incrementa atómicamente al cambiar contraseña e invalida JWT anteriores. |
| `preferences`            | `NotificationPreferences` | `inApp/email/push` en `true` al alta.                                                    |
| `createdAt`, `updatedAt` | `Instant`                 | Persistencia `timestamptz`.                                                              |

**`Role`** mantiene `code` como PK natural y `description`; no almacena `scope`: el ámbito se deriva de `RoleCode.isGlobal()` / `RoleCode.isDepartmental()`. **`UserRole`** puede tener clave compuesta por usuario+rol y solo acepta roles GLOBAL; revisar integridad e implementación coherente con `User.globalRoles` del dibujo. En registro se asigna `CITIZEN` dentro de la misma transacción de creación del usuario. Los roles adicionales se concederán por invitaciones, fuera del alcance de la rama `auth`.

**Autenticación JWT (ADR-0005):** `POST /auth/register` y `POST /auth/login` devolverán `TokenDTO` con JWT firmado de 7200 segundos, sin refresh token. Claims `sub`, `iat`, `exp`, `iss`, `ver` (`tokenVersion`), sin exponer contraseña ni roles como autoridad. En **cada petición protegida** Spring Security consultará PostgreSQL para comprobar cuenta activa, versión y roles vigentes. La reducción de coste mediante caché no se incorpora ahora.

**Cambio de contraseña:** `POST /users/me/password-changes` comprueba la actual, guarda BCrypt nuevo e incrementa `tokenVersion` en la misma transacción. Las sesiones JWT anteriores dejan de tener efecto en la siguiente petición. Rate limiting de registro/login inicialmente local por instancia con reposición temporal y contador acotado, `429` en exceso.

**Email:** al registro, `emailVerified=false`; la verificación obligatoria bloqueante **no se implementa todavía** sin servicio externo de correo. El futuro flujo debe impedir avanzar antes de confirmar el email, verificado también por backend. Recuperación de contraseña por email y refresh tokens son mejoras futuras.

**Invitaciones:** globales solo `OPERATOR`/`ADMIN`, jamás un código para `CITIZEN`. Cuentas creadas por invitación incluyen `CITIZEN`. Cuenta existente conserva identidad y añade rol autorizado. El primer `ADMIN` se creará manualmente desde Neon en una tarea posterior con hash generado localmente, sin script de secretos versionado.

### 3.3. Tramitación y trazabilidad

| Concepto           | Responsabilidad                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------------ |
| `IncidentReview`   | Decisión `VALIDATE`, `REJECT` o `REQUEST_INFORMATION`, actor, motivo y fecha.                                |
| `PriorityDecision` | Prioridad, origen y justificación auditables.                                                                |
| `StatusChange`     | Historial de transiciones del estado, responsable y motivo.                                                  |
| `Assignment`       | Trabajo asignado a un `Team` **o** técnico individual; autor, aceptación, inicio y fin, reasignaciones.      |
| `IncidentComment`  | Colaboración con visibilidad PUBLIC/INTERNAL.                                                                |
| `Notification`     | Avisos con canal, entrega y lectura; preferencias en `User` embebido.                                        |
| `AuditEvent`       | Operaciones administrativas, transferencias y decisiones no reflejadas necesariamente como cambio de estado. |

Estados previstos: `REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`. `VALIDATED` es independiente de departamento. El antiguo `ASSIGNED` se desglosa. La matriz exhaustiva de transiciones, autorizaciones y estados terminales sigue pendiente de otro tag.

Prioridades: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. La derivación se refleja en `Incident.assignedDepartment` y `ASSIGNED_TO_DEPARTMENT`, **sin crear `Assignment`**. El coordinador crea `Assignment` a equipo/técnico y puede cambiar a `WORK_ASSIGNED`. Como máximo una asignación de trabajo activa por incidencia. Reasignar finaliza la anterior y crea otra sin forzar cambio de estado. Transferir departamento se audita, aunque no haya transición. Aceptar asignación cambia `acceptedBy`/`acceptedAt` pero no inicia necesariamente trabajo.

Recorrido representativo: `REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`. Puede existir `REJECTED` y `REOPENED`; no es una matriz completa.

### 3.4. Territorio y contexto urbano (provisional)

`UrbanAsset`, `UrbanAssetAssociation`, `ExternalSource`, `ExternalObservation` y DTO relacionados reflejan intención del enunciado, pero integración con proveedores, persistencia exacta y periodos de vigencia no están validados. `UrbanContextViewDTO` es vista de consulta, no tabla. Los campos JSON flexibles del proveedor no constituyen contrato definitivo. No se eligen todavía mapas, geocodificación ni PostGIS. Fallo externo **no bloquea** registrar incidencia (RF26).

## 4. Identificadores, tipos y restricciones

- **Identificadores:** UUIDv7 Java `UUID` y PostgreSQL `uuid` como PK para entidades con identidad propia, generado ahora expresamente mediante `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` en Hibernate (pendiente de prueba de integración). Excepciones `Role.code` y clave compuesta de `UserRole`. `createdAt` continúa siendo obligatorio y UUID no es mecanismo de seguridad.
- **Fechas:** `Instant`/`timestamptz`. **Enumeraciones:** códigos de cadena estables, no ordinales.
- Los límites `[API]` del diagrama eran hipótesis del borrador; validar tamaños, nullability e índices según los casos de uso. `TEXT` no implica máximo arbitrario.
- Respetar email único normalizado, objectKey único, par canónico de duplicados, XOR en Assignment, pertenencia al departamento, trabajo activo único, rol adecuado al ámbito calculado por su código, `CITIZEN` base y `tokenVersion` actualizado de forma transaccional; imponer en servicio y SQL cuando corresponda.
- Las relaciones y cardinalidades dibujadas son hipótesis revisables; contrastar con DTO, integridad y pruebas. No materializar tablas antes de que las necesite un tag.

## 5. Proceso obligatorio: dominio y OpenAPI por tag

Este documento complementa [guía API](api/README.md), [contrato integrado](api/domain-openapi.md) y [CONTRIBUTING](../CONTRIBUTING.md). No implementar todas las entidades en un PR.

1. Consultar enunciado/requisitos del tag, alcance obligatorio y opcional aplazado.
2. Revisar YAML modular `paths/<tag>.yaml`, esquemas y DTO; separar contrato de proyección y entidades persistentes.
3. Revisar el relacional (PK/FK/cardinalidad) y las clases (atributos, enums, `@Embeddable`), incluidas entidades compartidas.
4. Acordar obligaciones, nullability, límites, historial, permisos, relaciones, transiciones, privacidad y almacenamiento; **no considerar aprobado un campo solo por estar dibujado**.
5. Diseñar y probar Flyway/JPA con `ddl-auto=validate`; no usar `update`; pruebas en base aislada, nunca experimentalmente en Neon `production`.
6. Actualizar YAML modular, bundle/HTML, diagramas fuente/exportaciones, documento de dominio, ADR, pruebas y CHANGELOG. Coordinar cambios sobre entidades compartidas.
7. PR: diferenciar **revisado**, **implementado**, **pendiente** y **pruebas realmente ejecutadas**. Aprobar el tag no valida todo el modelo.

### Orientación de revisión por tags

| Tags                                                  | Conceptos habituales (orientación, no lista exhaustiva)                                                                                                                    |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Autenticación, Usuarios, Invitaciones, Administración | `User`, `Role`, `UserRole`, `DepartmentMembership`, `StaffInvitation`, `AuditEvent`; en primera rama auth solo núcleo `User`/`Role`/`UserRole` más preferencias embebidas. |
| Catálogos                                             | `Category`, `Department`, equipos según operaciones aprobadas.                                                                                                             |
| Incidencias, Evidencias                               | `Incident`, `Location`, `Coordinates`, `Category`, `Attachment`, `StatusChange`.                                                                                           |
| Tramitación                                           | `IncidentReview`, `PriorityDecision`, `Assignment`, `Team`, `IncidentComment`, `StatusChange`.                                                                             |
| Duplicados                                            | `DuplicateSuggestionDTO`, `DuplicateLink`, `Incident`.                                                                                                                     |
| Contexto urbano, Mapas                                | `District`, `Neighborhood`, `UrbanAsset`, `ExternalSource`, `ExternalObservation`, DTO.                                                                                    |
| Notificaciones                                        | `Notification`, `NotificationPreferences`.                                                                                                                                 |
| Analítica, Informes                                   | Consultas/proyecciones; persistencia adicional justificada por casos de uso.                                                                                               |
| Asistencia inteligente, Conocimiento                  | Funciones opcionales aplazadas cuando corresponda; no implementar RAG por existir tag exploratorio.                                                                        |

## 6. Consistencia del dominio con OpenAPI

Dominio v1 y OpenAPI `0.2.0` constituyeron una referencia base de diseño armonizada; OpenAPI `0.3.0` recoge la revisión del tag Autenticación. Mantener esta semántica en las exportaciones de diagramas, actualmente **pendientes de editar**. Ambos diseños siguen sin SQL ni entidades JPA.

El contrato mantiene DTO independientes de JPA. Roles globales/departamentales acotados, estados de asignación, invitaciones, duplicados, IDs urbanos internos/externos y UUID siguen las decisiones anteriores. `StatusChangeDTO.fromStatus`/`changedBy` son opcionales cuando no existan. Evaluaciones IA que requieran un modelo no elegido permanecen fuera de rutas activas.

**Revisión de autenticación:** registro `201 TokenDTO`, login `401` genérico, cambio de contraseña protegido `204`, `ChangePasswordRequest`, `UserDTO.emailVerified`, roles base CITIZEN, invitaciones globales restringidas a roles municipales, invalidación JWT por `tokenVersion`, limitación local con `429` y errores de validación `400`/`422`. Ver [diseño detallado](auth-design.md).

**GET `fields`:** no está aprobado. La propuesta de anotaciones por atributo `OWNER`/`ADMIN`/`NONE`, proyección selectiva SQL y políticas contextuales se estudiará al implementar Usuarios; no añadir anotaciones, tablas ni endpoints en auth para esto.

## 7. Decisiones documentadas y estado

- [ADR-0005](adr/0005-jwt-spring-security.md): autenticación JWT, contraseña, revocación por versión, seguridad y rate limiting (**diseño acordado, código pendiente**).
- [ADR-0007](adr/0007-invitaciones-personal.md): invitaciones de personal aceptadas, con restricción CITIZEN y canje existente; política operativa pendiente.
- [ADR-0009](adr/0009-rbac-departamentos-equipos.md): roles GLOBAL/DEPARTMENT, CITIZEN base, pertenencias y equipos.
- [ADR-0010](adr/0010-ciclo-vida-y-asignaciones.md): derivación frente a trabajo; transiciones por refinar.
- [ADR-0011](adr/0011-identificadores-uuidv7.md): UUIDv7 generado desde Hibernate, comprobación de JPA pendiente.
- [Diseño de autenticación](auth-design.md): especificación completa de la tarea de backend actual, mejoras futuras y tests requeridos.

**Importante:** este cambio documental propone/revisa el contrato y dominio, **no aplica migraciones ni expone endpoints**. Los diagramas actualizados deben revisarse antes de confirmar el PR de diseño; después de Codex se actualizará el estado de implementación de cada pieza, con pruebas reales.
