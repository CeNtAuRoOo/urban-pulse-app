# Modelo de dominio de UrbanPulse (v1, núcleo de autenticación implementado)

> **Estado:** dominio v1 compartido y todavía revisable en los demás tags. **El núcleo de autenticación sí está implementado** mediante las entidades `User`, `Role`, `UserRole`, preferencias embebidas, migración Flyway V1 y cinco endpoints HTTP; las otras entidades del modelo siguen propuestas. Los diagramas de identidad incluyen las decisiones aprobadas, pero no son el esquema SQL ejecutable. La implementación se contrasta incrementalmente por tag.

## 1. Fuentes, alcance y cómo interpretar el modelo

La referencia de requisitos es «UrbanPulse: Plataforma cloud para la gestión inteligente de incidencias urbanas» (_Descripción inicial del proyecto_). Las decisiones se registran en los [ADR](adr/README.md); el [contrato OpenAPI modular](api/README.md) y los [diagramas](diagrams/README.md) son propuestas revisables. Los acuerdos base se sincronizaron con OpenAPI `0.2.0` y el tag de autenticación usa OpenAPI `0.4.0`. El código de auth ya existe; las demás rutas siguen siendo diseños por validar, aunque figuren en el contrato.

En particular, consultar la [especificación de autenticación implementada](auth-design.md), que separa el alcance realmente programado de las mejoras futuras y describe las pruebas disponibles.

**Fuera de esta fase:** los apartados opcionales del enunciado marcados en rojo, especialmente analista avanzado, entrenamiento/evaluación de modelos, hotspots predictivos y RAG. El análisis estadístico operativo e informes permanecen posibles mediante consultas. La prioridad asistida puede empezar con reglas sin añadir IA.

Se parte de un monolito Spring Boot, PostgreSQL en Neon, fotografías en Neon Object Storage y frontend Expo web/móvil. **El núcleo de identidad ya tiene entidades JPA, repositorios, cinco endpoints y una migración V1 ampliada; permanecen pendientes los repositorios y endpoints del negocio de incidencias.**

## 2. Los dos diagramas y su función

| Vista                           | Archivo                                                                      | Qué representa                                                              | Cómo usarla                                                                                         |
| ------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Modelo relacional lógico        | [`diagrams/relacional.drawio`](diagrams/relacional.drawio)                   | Tablas candidatas, PK/FK, relaciones y cardinalidades; cuatro páginas       | Refinar restricciones SQL y migraciones Flyway solo en el ámbito implementado.                      |
| Catálogo simplificado de clases | [`diagrams/clases-simplificado.drawio`](diagrams/clases-simplificado.drawio) | Entidades Java previstas, campos, tipos, enums, objetos `@Embeddable` y DTO | Referencia de estructura, deliberadamente sin conexiones UML; las relaciones se consultan en el ER. |

Páginas ER: (1) incidencias/evidencias, (2) tramitación, (3) identidad/organización, (4) territorio/contexto. Los dibujos son fuentes de diseño editables; una `@Embeddable` no requiere tabla propia y un DTO no implica una entidad JPA.

**La revisión actual de los `.drawio`** contiene `emailVerified`, `tokenVersion`, preferencias y `RoleCode` sin `RoleScope`, pero ahora falta incorporar la entidad persistida `EmailVerification`, su relación 1:1 con `User` y la tabla homónima de Flyway. **Actualizar ER página 3 y clases simplificadas**, y reexportar sus SVG; el UML sigue siendo modelo, no código ejecutable. Las exportaciones SVG deben seguir correspondiendo a las fuentes editables.

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

<<<<<<< HEAD
| Concepto                        | Responsabilidad en el diseño                                                                                      |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `User`                          | Cuenta única por identidad/correo con credenciales protegidas y preferencias de notificaciones.                   |
| `Role`, `UserRole` y `RoleCode` | Catálogo de roles y asignación N:M de **roles globales**; `RoleScope` distingue `GLOBAL` de `DEPARTMENT`.         |
| `Department`                    | Unidad municipal responsable de recibir incidencias; dispone de nombre y código corto únicos.                    |
| `DepartmentMembership`          | Relación temporal usuario–departamento–rol **departamental**; limita el ámbito de coordinación o actuación.       |
| `Team` y `TeamMembership`       | Equipos reutilizables de un departamento, con pertenencias históricas; desactivar no elimina trabajos históricos. |
| `StaffInvitation`               | Alta o incorporación controlada de personal; código de un solo uso que no se guarda en claro (ADR-0007).          |
=======
| Concepto                       | Responsabilidad en diseño                                                                                                   |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| `User`                         | Cuenta única con credenciales protegidas, roles, estado y preferencias. Revisión auth: `emailVerified` y `tokenVersion`.    |
| `Role`, `UserRole`, `RoleCode` | Catálogo y asignación N:M explícita de roles GLOBAL, con ámbito GLOBAL/DEPARTMENT derivado de `RoleCode` (sin `RoleScope`). |
| `Department`                   | Unidad municipal que recibe incidencias.                                                                                    |
| `DepartmentMembership`         | Vínculo temporal usuario–departamento–rol DEPARTMENT; delimita actuación.                                                   |
| `Team`, `TeamMembership`       | Equipos reutilizables con pertenencias históricas; desactivar no borra trabajos.                                            |
| `StaffInvitation`              | Alta/incorporación controlada de personal, código de un uso almacenado mediante hash/HMAC.                                  |
| `NotificationPreferences`      | Valor embebido en User (`inApp`, `email`, `push`). Valores iniciales acordados: **todos true**.                             |
>>>>>>> origin/main

**Roles iniciales:** globales `CITIZEN`, `OPERATOR`, `ADMIN`; departamentales `DEPARTMENT_COORDINATOR`, `TECHNICIAN`. **Toda cuenta posee `CITIZEN`**, incluidos operadores, administradores, coordinadores y técnicos; los roles municipales se acumulan. Se mantiene un único `User` sin jerarquías de clases. `ANALYST` está aplazado.

<<<<<<< HEAD
**Departamento implementado en el tag Catálogos:** `Department` usa UUIDv7, `name` (2–120) y `code` (2–20, mayúsculas ASCII, números, `_` o `-`) únicos sin distinción de mayúsculas/minúsculas; `description` es opcional con máximo 500 caracteres y `active` permite conservar referencias históricas al desactivar. Mantiene `createdAt` y `updatedAt` como `Instant`. Los valores se normalizan al crear o modificar (espacios en nombre/código y código en mayúsculas). El contrato expone esos cinco atributos de negocio, pero no las marcas de tiempo.

Tener un rol no otorga acceso indiscriminado: se debe comprobar también la pertenencia activa al departamento, asignación vigente, titularidad del recurso y estado. `TeamMembership` referencia una `DepartmentMembership` y debe mantenerse la invariancia **equipo y miembro pertenecen al mismo departamento**. La pertenencia activa puede variar; el historial no se borra.
=======
El operador central valida, prioriza y deriva sin adscripción departamental. El coordinador decide equipo/técnico en sus departamentos y el técnico ejecuta trabajos autorizados. Tener un rol no concede acceso indiscriminado: se comprueba también cuenta activa, pertenencia departamental, asignación, titularidad y estado. `TeamMembership` referencia una `DepartmentMembership` del mismo departamento y rol técnico; el historial no se borra.

**Revisión específica de `User` para `auth`:**

| Campo previsto           | Tipo lógico               | Requisito acordado                                                                       |
| ------------------------ | ------------------------- | ---------------------------------------------------------------------------------------- |
| `id`                     | `UUID`                    | UUIDv7 generado en Hibernate, columna `uuid`.                                            |
| `displayName`            | `String`                  | Longitud de API 2..120, pendiente de comprobar con validación y SQL.                     |
| `email`                  | `String`                  | Normalizado, único sin distinguir mayúsculas, no permite duplicados bajo concurrencia.   |
| `passwordHash`           | `String`                  | Hash BCrypt; nunca API ni logs.                                                          |
| `active`                 | `boolean`                 | Cuenta activa al registrarse; se comprueba en accesos protegidos.                        |
| `emailVerified`          | `boolean`                 | `false` inicial; la verificación por HMAC lo establece en `true` dentro de una transacción.     |
| `tokenVersion`           | `long`                    | `0` inicial; se incrementa atómicamente al cambiar contraseña e invalida JWT anteriores. |
| `preferences`            | `NotificationPreferences` | `inApp/email/push` en `true` al alta.                                                    |
| `createdAt`, `updatedAt` | `Instant`                 | Persistencia `timestamptz`.                                                              |

**`Role`** mantiene `code` como PK natural y `description`; no almacena `scope`: el ámbito se deriva de `RoleCode.isGlobal()` / `RoleCode.isDepartmental()`. **`UserRole`** puede tener clave compuesta por usuario+rol y solo acepta roles GLOBAL; revisar integridad e implementación coherente con `User.globalRoles` del dibujo. En registro se asigna `CITIZEN` dentro de la misma transacción de creación del usuario. Los roles adicionales se concederán por invitaciones, fuera del alcance de la rama `auth`.

**Autenticación JWT implementada (ADR-0005):** `POST /auth/register` y `POST /auth/login` devuelven `TokenDTO` con JWT firmado de 7200 segundos, sin refresh token. Claims `sub`, `iat`, `exp`, `iss`, `ver` (`tokenVersion`), sin exponer contraseña ni roles como autoridad. En **cada petición protegida** Spring Security consultará PostgreSQL para comprobar cuenta activa, versión y roles vigentes. La reducción de coste mediante caché no se incorpora ahora.

**Cambio de contraseña implementado:** `POST /users/me/password-changes` comprueba la actual, guarda BCrypt nuevo e incrementa `tokenVersion` en la misma transacción. Las sesiones JWT anteriores dejan de tener efecto en la siguiente petición. Rate limiting de registro/login inicialmente local por instancia con reposición temporal y contador acotado, `429` en exceso.

**Email:** al registro, `emailVerified=false` y se crea transaccionalmente una solicitud `EmailVerification` con código HMAC. La confirmación por correo ya está implementada: `EMAIL_VERIFIED` habilita el acceso a recursos de negocio, comprobado por el backend en cada petición. Mailpit realiza las pruebas SMTP sin entregas externas. Recuperación de contraseña por email y refresh tokens siguen siendo mejoras futuras.

**Invitaciones:** globales solo `OPERATOR`/`ADMIN`, jamás un código para `CITIZEN`. Cuentas creadas por invitación incluyen `CITIZEN`. Cuenta existente conserva identidad y añade rol autorizado. El primer `ADMIN` se creará manualmente desde Neon en una tarea posterior con hash generado localmente, sin script de secretos versionado.
>>>>>>> origin/main

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

<<<<<<< HEAD
- **Identificadores:** UUIDv7 es la PK de `Department` ya implementada (`UUID` Java / `uuid` PostgreSQL 18): JPA lo genera antes de insertar y la migración conserva `uuidv7()` como valor por defecto. Sigue siendo el criterio de las demás entidades con identidad propia; `Role.code` es la excepción de clave natural estable.
- Fechas/hora: `Instant` y `timestamptz`; `createdAt` sigue siendo obligatorio aunque UUIDv7 incorpore información temporal.
- Los límites con `[API]` en el diagrama proceden del **borrador** OpenAPI actual, no de una validación de negocio definitiva. Los campos marcados `TEXT` no imponen máximos arbitrarios; al implementar cada entidad se definirán tamaño, obligatoriedad e índices con criterios funcionales.
- Enumeraciones persistentes: representar por nombre/código estable, no por ordinal; verificar el efecto de futuras modificaciones.
- Respetar unicidad y restricciones de negocio (`User.email` normalizado, `Attachment.objectKey`, par canónico de duplicados, exclusión `Team`/técnico en `Assignment`, pertenencia de personal al departamento, un trabajo activo por incidencia) en servicios **y**, cuando proceda, en PostgreSQL.
- Las cardinalidades dibujadas y las FK son **hipótesis revisables**; contrastarlas con DTOs, integridad referencial, requisitos, datos de prueba y operaciones reales.
=======
- **Identificadores:** UUIDv7 Java `UUID` y PostgreSQL `uuid` como PK para entidades con identidad propia, generado ahora expresamente mediante `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` en Hibernate (comprobado para `User` en integración PostgreSQL efímera). Excepciones `Role.code` y clave compuesta de `UserRole`. `createdAt` continúa siendo obligatorio y UUID no es mecanismo de seguridad.
- **Fechas:** `Instant`/`timestamptz`. **Enumeraciones:** códigos de cadena estables, no ordinales.
- Los límites `[API]` del diagrama eran hipótesis del borrador; validar tamaños, nullability e índices según los casos de uso. `TEXT` no implica máximo arbitrario.
- Respetar email único normalizado, objectKey único, par canónico de duplicados, XOR en Assignment, pertenencia al departamento, trabajo activo único, rol adecuado al ámbito calculado por su código, `CITIZEN` base y `tokenVersion` actualizado de forma transaccional; imponer en servicio y SQL cuando corresponda.
- Las relaciones y cardinalidades dibujadas son hipótesis revisables; contrastar con DTO, integridad y pruebas. No materializar tablas antes de que las necesite un tag.
>>>>>>> origin/main

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

Dominio v1 y OpenAPI `0.2.0` constituyeron una referencia base de diseño armonizada; OpenAPI `0.4.0` recoge la verificación de correo del tag Autenticación. Mantener esta semántica en las exportaciones de diagramas. **El núcleo de autenticación ya tiene SQL V1 y entidades JPA** (`User`, `Role`, `UserRole`, `EmailVerification`); el resto del dominio continúa en diseño.

El contrato mantiene DTO independientes de JPA. Roles globales/departamentales acotados, estados de asignación, invitaciones, duplicados, IDs urbanos internos/externos y UUID siguen las decisiones anteriores. `StatusChangeDTO.fromStatus`/`changedBy` son opcionales cuando no existan. Evaluaciones IA que requieran un modelo no elegido permanecen fuera de rutas activas.

**Revisión de autenticación:** registro `201 TokenDTO`, login `401` genérico, cambio de contraseña protegido `204`, confirmación de correo `204` y reenvío `202`/`204`/`410`, `ChangePasswordRequest`, `UserDTO.emailVerified`, roles base CITIZEN, invitaciones globales restringidas a roles municipales, invalidación JWT por `tokenVersion`, limitación local con `429` y errores de validación `400`/`422`. Ver [diseño detallado](auth-design.md).

**GET `fields`:** no está aprobado. La propuesta de anotaciones por atributo `OWNER`/`ADMIN`/`NONE`, proyección selectiva SQL y políticas contextuales se estudiará al implementar Usuarios; no añadir anotaciones, tablas ni endpoints en auth para esto.

## 7. Decisiones documentadas y estado

**Alcance SQL/JPA implementado:** `users` (UUIDv7 Hibernate, BCrypt, `active`, `email_verified`, `token_version`, preferencias y fechas), `roles` (códigos fijos sin `scope`), `user_roles` (PK compuesta, `CHECK` solo globales) y `email_verifications` (código HMAC, PK/FK y fechas de caducidad). `CITIZEN` se asigna al registrar y se exige al autenticar. El rol base todavía no está forzado por una restricción universal SQL para inserciones externas. La API no tiene endpoints de perfiles, departamentos ni invitaciones.

<<<<<<< HEAD
**Actualización dominio–OpenAPI:** el contrato modular `0.2.0` y el dominio v1 representan el mismo diseño base. El tag Catálogos materializa exclusivamente `Department` con SQL, entidad JPA y endpoints; las demás entidades continúan como diseño pendiente de implementación incremental.
=======
- [ADR-0005](adr/0005-jwt-spring-security.md): autenticación JWT, contraseña, revocación por versión, seguridad y rate limiting (**implementado en código; despliegue pendiente**).
- [ADR-0007](adr/0007-invitaciones-personal.md): invitaciones de personal aceptadas, con restricción CITIZEN y canje existente; política operativa pendiente.
- [ADR-0009](adr/0009-rbac-departamentos-equipos.md): roles GLOBAL/DEPARTMENT, CITIZEN base, pertenencias y equipos.
- [ADR-0010](adr/0010-ciclo-vida-y-asignaciones.md): derivación frente a trabajo; transiciones por refinar.
- [ADR-0011](adr/0011-identificadores-uuidv7.md): UUIDv7 generado desde Hibernate, comprobado en `User` con PostgreSQL efímero.
- [Diseño de autenticación](auth-design.md): especificación completa de la tarea de backend actual, mejoras futuras y tests requeridos.

**Importante:** los cinco endpoints de autenticación, la V1 y las entidades de identidad ya existen en el código; **no consta V1 desplegada sobre Neon `development`**. Los demás tags siguen siendo revisables. Codex comunicó 55 pruebas correctas sobre la última implementación; queda pendiente comprobar el backend real junto con Mailpit y actualizar/reexportar las vistas gráficas.

### EmailVerification (entidad implementada de autenticación)

`EmailVerification` tiene PK/FK compartida `user_id` con `User`, relación uno a cero/uno (un usuario puede no tener solicitud, por ejemplo una vez verificado), `code_hash` bytea de 32 bytes, `created_at` y `expires_at` como `Instant`. Un código dura como máximo diez minutos y nunca prolonga las 24 horas de verificación de la cuenta. No se persiste el código en claro. Confirmar borra la solicitud y marca `User.emailVerified`; reenvío la sustituye bajo bloqueo de `User`. La limpieza elimina los códigos caducados y, más tarde, las cuentas no verificadas con sus roles. **La política de borrado requiere revisión si futuras entidades incorporan FKs a `users`.**

El acceso a operaciones de negocio exige email verificado (`EMAIL_VERIFIED`); una cuenta pendiente conserva JWT solo para autenticar, confirmar/reenviar y cambiar contraseña. Esto no introduce nuevos roles en `RoleCode` ni modifica `UserRole`.
>>>>>>> origin/main
