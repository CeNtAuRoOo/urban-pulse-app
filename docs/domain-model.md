# Modelo de dominio de UrbanPulse (versión base v1)

> **Estado: diseño inicial compartido; NO validado entidad por entidad ni implementado.** Fecha de incorporación documental: 18/10/2026. El modelo debe verificarse y actualizarse de forma incremental **al implementar cada tag de la API**. Las figuras no son migraciones SQL ni un inventario de clases Java existentes.

## 1. Fuentes, alcance y cómo interpretar el modelo

La referencia de requisitos es **«UrbanPulse: Plataforma cloud para la gestión inteligente de incidencias urbanas»** (_Descripción inicial del proyecto_). Las decisiones acordadas para el equipo se documentan en los [ADR](adr/README.md). El [contrato OpenAPI modular](api/README.md) y los [diagramas](diagrams/README.md) son **propuestas de diseño revisables**: cuando difieren entre sí, se debe resolver la discrepancia en el PR del tag afectado, no asumir automáticamente que uno de ellos está implementado.

**Fuera de esta fase:** los apartados opcionales del enunciado marcados en rojo, especialmente el actor analista avanzado, entrenamiento/evaluación de modelos, _hotspots_ predictivos y los documentos/consultas RAG. El análisis estadístico operativo y los informes no desaparecen por ello; sus resultados pueden construirse con consultas sin introducir entidades analíticas prematuramente. La prioridad asistida puede evolucionar desde reglas sin incorporar todavía IA.

Se comienza con un monolito Spring Boot, PostgreSQL (Neon), evidencias fotográficas en Neon Object Storage y un frontend Expo compartido para web y móvil. **No hay entidades JPA, repositorios de negocio, endpoints funcionales ni migración inicial de tablas** en esta entrega documental.

## 2. Los dos diagramas y su función

| Vista                           | Archivo                                                                      | Qué representa                                                                                      | Cómo usarla                                                                                                               |
| ------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Modelo relacional lógico        | [`diagrams/relacional.drawio`](diagrams/relacional.drawio)                   | Tablas candidatas, campos principales, PK/FK, relaciones y cardinalidades; cuatro páginas por áreas | Punto de partida para refinar restricciones y migraciones Flyway **cuando** se implemente la persistencia correspondiente |
| Catálogo simplificado de clases | [`diagrams/clases-simplificado.drawio`](diagrams/clases-simplificado.drawio) | Clases Java previstas, atributos, tipos, límites propuestos, enums, `@Embeddable` y DTO auxiliares  | Referencia rápida de estructura; **deliberadamente sin líneas de asociación**: las relaciones se consultan en el ER       |

Las páginas ER son (1) incidencias/evidencias, (2) tramitación municipal, (3) identidad/organización y (4) territorio/contexto urbano. Los dos dibujos **no son esquemas generados del código ni están cerrados**. Puede que una `@Embeddable` no requiera tabla independiente y que un DTO no corresponda a ninguna entidad JPA.

**Convención de nombres:** conceptos y campos del dominio en inglés (`Incident`, `StatusChange`, `DepartmentMembership`); documentación explicativa en español. Las entidades se distinguen de enums, objetos de valor y DTO por sus responsabilidades, no por aparecer en el mismo diagrama.

## 3. Conceptos centrales y responsabilidades

### 3.1. Incidencias y ubicación

| Concepto                    | Responsabilidad en el diseño                                                                                                                                  |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Incident`                  | Reporte central; guarda informante, descripción, estado, prioridad, categoría opcional, departamento actual opcional, ubicación, fechas y control de versión. |
| `Category`                  | Catálogo de clasificación; una incidencia puede registrarse sin categoría.                                                                                    |
| `Attachment`                | Metadatos de **fotografías** y referencia privada al objeto; el binario no reside en PostgreSQL.                                                              |
| `Location` y `Coordinates`  | Objetos de valor `@Embeddable`; coordenadas obligatorias, precisión opcional, dirección/distrito/barrio derivados opcionales. No tienen tablas propias.       |
| `District` y `Neighborhood` | Referencias territoriales para geocodificación, filtrado y estadísticas; un barrio pertenece a un distrito.                                                   |
| `DuplicateLink`             | Enlace entre dos incidencias confirmado por personal autorizado, preservando ambos reportes.                                                                  |
| `DuplicateSuggestionDTO`    | Sugerencia calculada mediante proximidad espacial/temporal y quizá categoría; **no es entidad ni confirma duplicidad**.                                       |

Una incidencia se puede crear sin enriquecimiento externo ni identificación exacta del activo urbano. Las fotografías requieren validación de formato, tamaño y autorización (ADR-0004); el detalle se completará en el tag `Evidencias`.

### 3.2. Identidad y organización

| Concepto                        | Responsabilidad en el diseño                                                                                      |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `User`                          | Cuenta única por identidad/correo con credenciales protegidas y preferencias de notificaciones.                   |
| `Role`, `UserRole` y `RoleCode` | Catálogo de roles y asignación N:M de **roles globales**; `RoleScope` distingue `GLOBAL` de `DEPARTMENT`.         |
| `Department`                    | Unidad municipal responsable de recibir incidencias.                                                              |
| `DepartmentMembership`          | Relación temporal usuario–departamento–rol **departamental**; limita el ámbito de coordinación o actuación.       |
| `Team` y `TeamMembership`       | Equipos reutilizables de un departamento, con pertenencias históricas; desactivar no elimina trabajos históricos. |
| `StaffInvitation`               | Alta o incorporación controlada de personal; código de un solo uso que no se guarda en claro (ADR-0007).          |

**Roles iniciales:** globales `CITIZEN`, `OPERATOR`, `ADMIN`; departamentales `DEPARTMENT_COORDINATOR`, `TECHNICIAN`. El operador central puede validar/priorizar y derivar incidencias en todo el municipio **sin pertenecer a un departamento**. El coordinador decide el equipo/técnico dentro de sus departamentos. El técnico ejecuta y documenta trabajos autorizados. Se excluye por ahora `ANALYST` del catálogo inicial porque el actor analista avanzado está aplazado.

Tener un rol no otorga acceso indiscriminado: se debe comprobar también la pertenencia activa al departamento, asignación vigente, titularidad del recurso y estado. `TeamMembership` referencia una `DepartmentMembership` y debe mantenerse la invariancia **equipo y miembro pertenecen al mismo departamento**. La pertenencia activa puede variar; el historial no se borra.

### 3.3. Tramitación y trazabilidad

| Concepto           | Responsabilidad en el diseño                                                                                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `IncidentReview`   | Decisión `VALIDATE`, `REJECT` o `REQUEST_INFORMATION`, actor, motivo y fecha.                                                                                                         |
| `PriorityDecision` | Prioridad establecida, procedencia y justificación auditables.                                                                                                                        |
| `StatusChange`     | Historial de transiciones del estado de la incidencia, responsable y motivo.                                                                                                          |
| `Assignment`       | **Solo trabajo asignado** a un `Team` **o** a un técnico individual: quién asigna, aceptación, inicio y finalización. Permite conservar reasignaciones incluso sin cambiar el estado. |
| `IncidentComment`  | Colaboración con visibilidad `PUBLIC`/`INTERNAL`.                                                                                                                                     |
| `Notification`     | Avisos a usuarios, canal, entrega y lectura; preferencias dentro de `User` como `NotificationPreferences` embebido.                                                                   |
| `AuditEvent`       | Acciones administrativas, transferencias de departamento y decisiones relevantes que no siempre implican `StatusChange`.                                                              |

**Estados del diseño v1:** `REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`. `VALIDATED` es independiente: el operador puede validar sin disponer aún de un departamento. El estado anterior `ASSIGNED` del enunciado/borrador OpenAPI se **desglosa** en dos fases. La matriz exacta de transiciones, autorizaciones y estados terminales **aún requiere revisión durante la implementación**.

**Prioridades:** `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` (escala de diseño adoptada). La asignación inicial al departamento se expresa mediante `Incident.assignedDepartment` y el estado `ASSIGNED_TO_DEPARTMENT`; **no** se crea una `Assignment` de trabajo en esa fase. El coordinador crea una `Assignment` para un `Team` o un `User` técnico y la incidencia pasa a `WORK_ASSIGNED`. Como máximo una asignación de trabajo activa por incidencia es la regla de diseño. Una reasignación finaliza la asignación previa y crea otra; una transferencia entre departamentos se audita incluso cuando no cambia el estado.

Ejemplo: `REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`. También existen `REJECTED` y `REOPENED`; este ejemplo **no define todas las transiciones permitidas**. Si un técnico acepta una asignación, `acceptedAt` puede cambiar sin que cambie el estado hasta que realmente comience la actuación.

### 3.4. Territorio y contexto urbano (provisional)

`UrbanAsset`, `UrbanAssetAssociation`, `ExternalSource`, `ExternalObservation` y DTO relacionados reflejan la intención del enunciado, pero **la integración con proveedores, su persistencia exacta y los periodos de vigencia no están investigados ni validados**. `UrbanContextViewDTO` es una vista de consulta, no una tabla. Los campos JSON representan datos flexibles del proveedor, no un contrato definitivo. No se eligen aún proveedor de mapas, geocodificación ni PostGIS. Un fallo de fuente externa **no bloquea** el registro de una incidencia (RF26).

## 4. Identificadores, tipos y restricciones: criterio de interpretación

- **Identificadores previstos:** UUIDv7 como propuesta de PK para las entidades con identidad propia (`UUID` Java / `uuid` PostgreSQL 18); excepción para el código estable de `Role` (`RoleCode`). Es una decisión de diseño, **no** una migración aplicada. La estrategia concreta de generación entre Java y PostgreSQL se valida al mapear JPA.
- Fechas/hora: `Instant` y `timestamptz`; `createdAt` sigue siendo obligatorio aunque UUIDv7 incorpore información temporal.
- Los límites con `[API]` en el diagrama proceden del **borrador** OpenAPI actual, no de una validación de negocio definitiva. Los campos marcados `TEXT` no imponen máximos arbitrarios; al implementar cada entidad se definirán tamaño, obligatoriedad e índices con criterios funcionales.
- Enumeraciones persistentes: representar por nombre/código estable, no por ordinal; verificar el efecto de futuras modificaciones.
- Respetar unicidad y restricciones de negocio (`User.email` normalizado, `Attachment.objectKey`, par canónico de duplicados, exclusión `Team`/técnico en `Assignment`, pertenencia de personal al departamento, un trabajo activo por incidencia) en servicios **y**, cuando proceda, en PostgreSQL.
- Las cardinalidades dibujadas y las FK son **hipótesis revisables**; contrastarlas con DTOs, integridad referencial, requisitos, datos de prueba y operaciones reales.

## 5. Proceso obligatorio al implementar un tag

Este documento **complementa** el flujo design-first de [la guía de API](api/README.md) y de [CONTRIBUTING](../CONTRIBUTING.md); no se propone crear todas las entidades en un único PR.

1. **Consultar el enunciado y requisitos afectados.** Identificar alcance obligatorio, opcional aplazado y decisiones ya registradas en ADR.
2. **Revisar el OpenAPI modular del tag** (`api/openapi/paths/<tag>.yaml` y esquemas correspondientes). Separar contratos de lectura/escritura de conceptos persistentes.
3. **Revisar ambas vistas del dominio:** tablas/FK/cardinalidades del `relacional.drawio` y campos/tipos/enums/embeddables del `clases-simplificado.drawio`, incluso si la misma entidad aparece en otros tags.
4. **Acordar cambios del dominio**: obligaciones, nulabilidad, límites, cardinalidades, historial, permisos por rol y ámbito, invariantes, transiciones, datos contextuales y privacidad. Si hay discrepancia con OpenAPI, modificar de forma coordinada los artefactos implicados; **no considerar aprobado el esquema por estar dibujado**.
5. **Diseñar la persistencia y las migraciones Flyway** correspondientes al alcance implementado; no usar `ddl-auto=update`. Añadir entidades JPA y repositorios, DTO y servicios según el caso de uso. Usar pruebas aisladas para invariantes, mapeos, autorización y operaciones.
6. **Actualizar documentación y comunicar impactos:** los **dos diagramas** cuando proceda (original editable y exportaciones), este documento, ADR relevantes, OpenAPI del tag y su bundle si se conserva, tests y CHANGELOG. Coordinar las modificaciones de entidades compartidas con otros compañeros.
7. **Describir el estado del tag en el PR:** componentes de dominio revisados, decisiones tomadas/pendientes, archivos afectados, tests y si la funcionalidad está implementada o solo diseñada. La aprobación del PR consolida **ese alcance**, no la totalidad del dominio v1.

### Orientación de revisión por grupos de tags

| Tags                                                  | Conceptos del dominio que suelen afectar (orientativo, no exhaustivo)                                                              |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Autenticación, Usuarios, Invitaciones, Administración | `User`, `Role`, `UserRole`, `DepartmentMembership`, `StaffInvitation`, `AuditEvent`                                                |
| Catálogos                                             | `Category`, `Department`, equipos según operaciones aceptadas                                                                      |
| Incidencias, Evidencias                               | `Incident`, `Location`, `Coordinates`, `Category`, `Attachment`, `StatusChange`                                                    |
| Tramitación                                           | `IncidentReview`, `PriorityDecision`, `Assignment`, `Team`, `IncidentComment`, `StatusChange`                                      |
| Duplicados                                            | `DuplicateSuggestionDTO`, `DuplicateLink`, `Incident`                                                                              |
| Contexto urbano, Mapas                                | `District`, `Neighborhood`, `UrbanAsset`, `ExternalSource`, `ExternalObservation`, DTO contextuales                                |
| Notificaciones                                        | `Notification`, `NotificationPreferences`                                                                                          |
| Analítica, Informes                                   | Consultas y proyecciones; persistencia adicional **solo** con caso de uso justificado                                              |
| Asistencia inteligente, Conocimiento                  | Funciones avanzadas aplazadas cuando correspondan al alcance marcado en rojo; no implementar RAG por existir un tag en el borrador |

## 6. Desajustes conocidos con el contrato OpenAPI actual

No se corrigen los YAML en este PR **documental**, para mantener el proceso ya acordado de revisión por tag:

- `IncidentStatus` del borrador aún utiliza `ASSIGNED` en lugar de `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED`; operaciones, filtros y respuestas relacionados necesitan revisión.
- `UserDTO` / `UserSummaryDTO` todavía reflejan un único `role`; el diseño de `Role`, `UserRole`, `RoleScope` y `DepartmentMembership` distingue roles globales de departamentales. Se debe acordar la representación pública y los permisos.
- La asignación de trabajo en `tramitacion.yaml` incluye `departmentId`, `teamId` y `technicianId`; el modelo v1 separa **derivación municipal** (`Incident.assignedDepartment`) y **destinatario del trabajo** (`Assignment`). Revisar endpoints, DTOs y reglas de autorización.
- Las invitaciones del borrador no reflejan por completo el posible rol departamental, ni el canje por una cuenta existente. Consultar ADR-0007.
- Las respuestas de `DuplicateSuggestionDTO` y los DTOs contextuales pueden diferir del diagrama simplificado; **la forma final de la API se decide en el tag**.
- IDs de ejemplo como `inc_001`, `usr_1` en OpenAPI son ilustrativos; deberán adecuarse a la convención elegida cuando se revise el contrato.

## 7. Decisiones documentadas y límites de esta entrega

- [ADR-0005](adr/0005-jwt-spring-security.md): autenticación JWT y autorización efectiva en el backend.
- [ADR-0007](adr/0007-invitaciones-personal.md): invitaciones de personal, **aceptado** como mecanismo; parámetros operativos pendientes.
- [ADR-0009](adr/0009-rbac-departamentos-equipos.md): roles globales/departamentales y pertenencias.
- [ADR-0010](adr/0010-ciclo-vida-y-asignaciones.md): distinción entre derivación al departamento y asignación de trabajos.
- [ADR-0011](adr/0011-identificadores-uuidv7.md): estrategia base de identificadores.

**Alcance de este PR:** documentación, diagramas versionados por el equipo y decisiones de diseño; **sin** modificar SQL, entidades Spring Boot, endpoints o esquemas OpenAPI. La ausencia de implementación no equivale a que el diseño esté validado funcionalmente.
