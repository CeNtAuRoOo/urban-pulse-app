# ADR-0007: Invitaciones para incorporar permisos municipales

- **Estado:** Aceptado; módulo backend implementado. El [contrato](../api/README.md), las [pruebas](../development.md) y el [modelo](../domain-model.md) se mantienen en sus documentos respectivos.
- **Fecha original:** 2026-10-11.
- **Decisión definitiva:** 2026-10-30; sustituye el borrador de registro especial de personal.

## Contexto y alternativas

La identidad ciudadana y la concesión de permisos municipales tienen ciclos distintos. Se mantiene una sola cuenta `User` por correo. Se descartan el alta por un administrador, las contraseñas provisionales y el registro público con selección de privilegios. Se adopta una invitación obligatoriamente vinculada a un correo que su titular canjea con una cuenta activa y verificada, utilizando el registro ciudadano existente si lo necesita.

## Decisión implementada

1. Solo `ADMIN` activo con `CITIZEN` y `EMAIL_VERIFIED` puede emitir, listar, consultar y revocar. Spring Security aplica autorización real; `x-roles` solo documenta.
2. Emitir crea `StaffInvitation` y `AuditEvent`, **nunca** `User`. Email normalizado exactamente como `User.normalizeEmail` (trim/minúsculas), obligatorio y de máximo 254 caracteres.
3. `OPERATOR`/`ADMIN` son globales invitables y no admiten `departmentId`, ni siquiera `null`. `TECHNICIAN`/`DEPARTMENT_COORDINATOR` requieren departamento existente y activo al emitir y al canjear. `CITIZEN` no es invitable. `Department` se comparte con la administración de Departamentos de `organization`, que ofrece listado, detalle, alta y PATCH; Invitaciones reutiliza su entidad, repositorio y bloqueo de fila. Los equipos siguen pendientes.
4. El destinatario se registra en `/auth/register`, verifica su correo o utiliza una cuenta ya verificada. Solo envía `invitationCode` al canje autenticado. `User.email` debe coincidir con `StaffInvitation.email`. No se alteran identidad, contraseña, correo, `tokenVersion` ni permisos anteriores.
5. Vigencia por defecto de **siete días exactos de 24 horas** desde `Clock.instant()`. `security.staff-invitation.ttl-days` admite 1..30; el cliente no establece `expiresAt`. El instante de vencimiento no es válido. `ACTIVE`, `REDEEMED`, `REVOKED`, `EXPIRED` se derivan de fechas; no hay columna `status`, reactivación ni scheduler de invitaciones.
6. `SecureRandom` genera 32 bytes (256 bits) Base64URL canónicos sin padding, de 43 caracteres. Solo se almacena HMAC-SHA256 con propósito y clave específica (`bytea` de 32 bytes e índice único), separado de JWT y verificación de correo. `STAFF_INVITATION_SECRET` se suministra externamente como Base64 de al menos 32 bytes. Rotarlo invalida códigos pendientes; no hay keyring.
7. El código llega exclusivamente mediante `EmailSender`/SMTP, tras commit y fuera de la transacción. Evento temporal y componentes evitan filtraciones por logs/serialización. El correo en español informa de registro/verificación/login, rol, departamento y expiración UTC, con HTML escapado. `POST` devuelve `201 InvitationDTO`, `Location` hacia GET y `Cache-Control: no-store`, sin código ni HMAC en respuesta.
8. SMTP sencillo, sin outbox ni reintentos. Fallo tras commit conserva invitación, y solo se registra tipo de excepción. El administrador puede revocar y emitir otra. Mailpit captura localmente; no acredita entrega externa. `201` **no** garantiza recepción.
9. Emisiones duplicadas permitidas. Un permiso global o pertenencia departamental activa idéntica responde `409 PERMISSION_ALREADY_GRANTED` sin consumir. `UserRole` mantiene PK compuesta; `DepartmentMembership` conserva historia e índice único parcial sobre `(user_id, department_id, role_code) WHERE left_at IS NULL`.
10. Concesión, canje y auditoría comparten una transacción: cualquier fallo de permiso, consumo o auditoría provoca el rollback del conjunto. Bloqueo pesimista de **usuario, invitación, departamento**; el del usuario serializa códigos diferentes y coordina autenticación. Revalidación de cuenta, `CITIZEN`, correo, `tokenVersion`, departamento y expiración (también después de esperar). Restricciones SQL son protección adicional; las colisiones de permisos conocidas se traducen a `409`.
11. Revocación solo para `ACTIVE`: guarda `revokedAt`/`revokedBy` y auditoría. Estado final devuelve `409 INVITATION_NOT_ACTIVE`. SQL impone fechas/actores coherentes y exclusión entre canje y revocación. No se borran invitaciones para revocarlas.
12. `AuditEvent` mínimo reutilizable: actor, acción, tipo/ID de recurso, `occurredAt`, detalles JSONB (rol/ámbito/departamento). `summary` y `requestId` siguen opcionales y sin inventar valores. Acciones `STAFF_INVITATION_CREATED`, `STAFF_INVITATION_REDEEMED`, `STAFF_INVITATION_REVOKED`; nunca códigos, hashes, JWT, contraseñas ni correo del invitado.
13. FK de usuarios/departamentos históricos con `ON DELETE RESTRICT`, sin cascadas destructivas. Actores y titulares de permisos siempre verificados. El destinatario es correo sin FK: eliminar una cuenta sin verificar tras 24 h no afecta a su invitación y permite registro nuevo con el mismo email.
14. Limitación de canje después de autenticar, antes de HMAC/bloqueos: usuario **10/900 s** e IP **30/900 s** con reposición gradual, en memoria por instancia. Dirección de conexión, sin confiar en `Forwarded`. Se consume IP y luego usuario, sin reembolso del primer bucket. Cuotas distintas de las de autenticación.
15. Listado con filtros exactos por estado/email/rol/departamento ejecutados en SQL, `page=1`, `pageSize=20` (máximo 100), orden `createdAt DESC, id DESC`, metadatos `PageMetadata`, offset máximo `Integer.MAX_VALUE` (`422` si se supera). Las inserciones concurrentes pueden desplazar páginas; cursor no implementado.

## Contrato y errores

Cinco operaciones bajo `/api/v1`: `POST`/`GET /staff-invitations`, `GET /staff-invitations/{invitationId}`, `POST /staff-invitations/{invitationId}/revocations` y `POST /users/me/staff-invitation-redemptions`. Se elimina el contrato anterior `/auth/staff-registrations` y los tipos `StaffRegistrationRequest`/`InvitationCreatedDTO`. El canje usa `InvitationRedemptionDTO` sin ampliar `UserDTO` con pertenencias. Los nuevos roles globales pueden reconocerse con el JWT anterior al consultarlos en la base en la petición siguiente.

Errores: `400` JSON/tipos no interpretables y cuerpo mayor de 16 KiB; `401` JWT/identidad inválidos; `403` permiso o `EMAIL_NOT_VERIFIED`; `404` recurso administrativo ausente; `409` permiso vigente o estado final; `422` validación, `INVALID_DEPARTMENT` o `INVALID_INVITATION_CODE`; `429 Retry-After`; `503` por indisponibilidad tipada PostgreSQL y `500` para otros fallos. Códigos inexistentes, consumidos, revocados, caducados o para otro correo comparten un error genérico sin filtración; JSON desconocido devuelve `422`.

## Alcance y consecuencias

Se reutilizan identidad, autenticación y transporte SMTP: `invitations` no registra usuarios mediante `AuthService`. La protección de cuota es local, no distribuida. Listados administrativos con correos personales solo son visibles a `ADMIN` verificado. No se implementa frontend, bootstrap productivo de ADMIN, retirada de `UserRole`/`DepartmentMembership`, equipos (`Team`/`TeamMembership`), auditoría pública, outbox/reintentos, proveedor SMTP público ni despliegue/migración sobre Neon. Las cuentas ADMIN de pruebas son **fixtures exclusivamente efímeros**.

Antes de producción deberá acordarse una política de retención para invitaciones y auditoría (datos personales), así como una estrategia de baja/anonimización con FK históricas `RESTRICT`, rotación del secreto y tratamiento de invitaciones pendientes al revocar permisos. También deben estudiarse cuota de emisión por ADMIN y coordinación entre varias instancias.

## Persistencia y administración departamental

Las tablas de Invitaciones pertenecen a la única V1 junto con identidad y los catálogos ([ADR-0006](0006-flyway.md)). `organization` administra los departamentos compartidos, sin semillas. PATCH utiliza el mismo bloqueo de fila que emisión/canje; se conserva el orden usuario → invitación → departamento y se rechazan unidades inactivas. Desactivar no revoca pertenencias ni borra historia. Category permanece independiente y no altera la concesión de permisos.

## Validación y diagramas

Las suites del perfil `postgres-it` comprueban las cinco áreas con PostgreSQL efímero y excluyen Neon. [Desarrollo](../development.md#verificación-del-backend) mantiene los comandos y resultados vigentes, incluida la regresión entre administración departamental y canje.

Las fuentes ER02, ER03, clases y C4 reflejan Invitaciones, auditoría, correo y departamentos compartidos. Los SVG regenerados manualmente corresponden estructuralmente a sus fuentes e incluyen ambos catálogos; véase [inventario de diagramas](../diagrams/README.md). Los flujos de incidencias y el despliegue definitivo requieren nuevos diseños al implementarse.
