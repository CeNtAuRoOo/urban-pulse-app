# DD-001 — Identidad y acceso municipal

Descripción retrospectiva del backend. El mismo usuario puede actuar como ciudadano y recibir permisos municipales. La API autentica en Spring Security y vuelve a comprobar cuenta y permisos en PostgreSQL. Un fallo de acceso a la persistencia debe distinguirse de una credencial inválida.

## JWT y Spring Security

- Tokens de acceso **HS256** de **7200 segundos (2 horas)**, con `sub`, `iat`, `exp`, `iss` y `ver`; no hay refresh token.
- `JWT_SECRET` se suministra externamente, en Base64 de al menos 32 bytes aleatorios; `JWT_ISSUER` configura el emisor. No versionar claves reales ni utilizar valores de `auth-test.properties` fuera de tests.
- `DatabaseJwtAuthenticationConverter` consulta **en cada petición protegida** el usuario, actividad, `tokenVersion`, rol base `CITIZEN` y **roles globales vigentes** en PostgreSQL. Los roles no se toman del JWT como autoridad permanente.
- La autoridad `EMAIL_VERIFIED` solo se concede si `User.emailVerified=true`; las rutas protegidas la exigen, salvo confirmación/reenvío/cambio de contraseña y excepciones de autenticación previstas. Una cuenta pendiente recibe `403 EMAIL_NOT_VERIFIED`.
- Confirmar el correo no modifica `tokenVersion`, por lo que el **mismo JWT** adquiere acceso normal en la petición siguiente. Canjear una invitación tampoco cambia la versión: el mismo JWT puede reconocer un nuevo rol global desde PostgreSQL.
- Cambiar la contraseña exige la vigente, utiliza BCrypt con coste 12, bloquea al usuario y aumenta **transaccionalmente** `tokenVersion`, de modo que los tokens previos dejan de funcionar en la próxima petición.

## Verificación de correo y limpieza

La verificación utiliza códigos de **ocho dígitos decimales ASCII**, generados con `SecureRandom`, protegidos mediante HMAC-SHA256 con secreto independiente `EMAIL_VERIFICATION_SECRET` y **vigencia máxima de diez minutos**. No deben aparecer códigos en bases de datos en claro, JWT, respuestas HTTP o logs. Las confirmaciones/reenvíos requieren JWT válido y validan cuenta, versión y estado bajo bloqueo.

`EmailVerificationService` confirma o reemite, y `VerificationEmailListener` entrega por `EmailSender` mediante `@TransactionalEventListener(AFTER_COMMIT)`. `EmailVerificationCleanup` limpia códigos vencidos y cuentas que siguen sin verificar tras **24 horas**, con barrido ordinario horario, lotes acotados y `SKIP LOCKED`. `EMAIL_VERIFICATION_CLEANUP_CRON` permite configurar su calendario; `-` lo desactiva en tests. No aplicar esta limpieza en un entorno compartido sin revisar las nuevas FK y política de retención.

## SMTP en desarrollo y fallos de entrega

`shared/mail/EmailSender` abstrae el envío; `SmtpEmailSender` utiliza Spring Mail y configuración externa (`MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, opciones AUTH y STARTTLS). En Compose de desarrollo, Mailpit captura mensajes en `mailpit:1025` con interfaz pública `http://localhost:9999` (puerto interno 8025). No hay proveedor SMTP de Internet ni garantía de entrega; la falta de outbox significa que **fallo de envío después del commit no revierte los datos**. Para verificación existe reenvío manual; para invitaciones no existe y se usa revocación/nueva emisión.

## Cuotas, validación, errores y privacidad

El backend reutiliza `shared/ratelimit/InMemoryRateLimiter` con token buckets **locales a la instancia** y memoria acotada. Las políticas de auth incluyen registro por IP **5/300 s**, login por IP **20/1200 s** y login por correo **5/300 s**; la configuración de verificación aplica confirmación usuario **5/3600 s**, IP **30/3600 s**, reenvío cooldown **1/60 s**, usuario **5/86400 s** e IP **20/3600 s**. El límite protector de cuerpo para los cinco POST auth es **16 KiB**. Los contadores se pierden al reiniciar y no son distribuidos. Se usa la IP de conexión, sin confiar en `Forwarded` ni `X-Forwarded-For` proporcionadas por clientes.

Las respuestas siguen un `ProblemDetail` uniforme: `400` para JSON no interpretable, tipos erróneos o exceso de cuerpo; `401` para credenciales, JWT, cuenta o rol base inválidos; `403` para acceso sin permisos/correo pendiente; `409` para email ya existente; `422` para datos interpretables no válidos; `429` con `Retry-After` para cuota; `503` para indisponibilidad tipada de PostgreSQL y `500` saneado para fallos internos. No divulgar contraseñas, tokens, códigos, HMAC ni SQL interno en respuestas o logs. Evitar depender de cadenas de mensajes para control de errores en el cliente; emplear sus códigos.

## Incorporación de personal

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
13. FK de usuarios/departamentos históricos con `ON DELETE RESTRICT`, sin cascadas destructivas. Las altas de actores y titulares de permisos exigen cuenta verificada. La limpieza conserva cualquier cuenta con referencias históricas y la excluye antes de limitar el lote. El destinatario es correo sin FK: eliminar una cuenta sin verificar tras 24 h no afecta a su invitación y permite registro nuevo con el mismo email.
14. Limitación de canje después de autenticar, antes de HMAC/bloqueos: usuario **10/900 s** e IP **30/900 s** con reposición gradual, en memoria por instancia. Dirección de conexión, sin confiar en `Forwarded`. Se consume IP y luego usuario, sin reembolso del primer bucket. Cuotas distintas de las de autenticación.
15. Listado con filtros exactos por estado/email/rol/departamento ejecutados en SQL, `page=1`, `pageSize=20` (máximo 100), orden `createdAt DESC, id DESC`, metadatos `PageMetadata`, offset máximo `Integer.MAX_VALUE` (`422` si se supera). Las inserciones concurrentes pueden desplazar páginas; cursor no implementado.

## Validación

Las suites de autenticación, verificación, invitaciones y administración comprueban revocación, cambio de contraseña, canjes concurrentes y conservación del último ADMIN. El control por departamento depende del recurso y del caso de uso. [ADR-005](../adr/ADR-005-jwt-spring-security.md) · [ADR-007](../adr/ADR-007-invitaciones-personal.md) · [Contrato](../api/README.md) · [Evolución](../pending.md#seguridad).
