# UrbanPulse — Autenticación, verificación de correo y seguridad

> **Estado a 04/11/2026:** el tag **Autenticación** está implementado en Java/JPA/Flyway V1 y es reutilizado por **Invitaciones** (incluida ahora en la misma V1), que se incorporó después de reorganizar el backend por módulos. Las pruebas actuales con PostgreSQL 18 efímero constan en [Desarrollo](development.md#verificación-del-backend); no consta aplicación de V1 consolidada sobre Neon `development` ni entrega SMTP pública. El [inventario](diagrams/README.md) diferencia las fuentes actuales y las exportaciones gráficas históricas.

## 1. Referencias, alcance y responsabilidades

El registro ciudadano y la verificación de correo resuelven **identidad**; las invitaciones posteriores resuelven **concesión de permisos municipales** a una cuenta ya existente. No hay registro público privilegiado ni dos tipos de cuentas; toda persona conserva `CITIZEN` y el backend decide autorizaciones según permisos vigentes en PostgreSQL. El contrato OpenAPI `0.7.0` incluye cinco operaciones de auth, cinco de Usuarios, cinco de Invitaciones y cuatro por catálogo implementado (Departamentos y Categorías). Incidencias añade seis operaciones que reutilizan identidad y seguridad compartidas; Evidencias añade cinco rutas con la misma política de acceso por incidencia. La tramitación y los restantes recursos siguen en diseño.

La organización de paquetes es **por funcionalidad**:

- `auth/api`: `AuthController`; `auth/dto`: DTO de registro, login, confirmación, contraseña y token.
- `auth/model`/`auth/repository`: `EmailVerification` y su repositorio; `auth/service`: `AuthService`, `JwtTokenService`, `EmailVerificationCodeService`, `EmailVerificationService` y `EmailVerificationCleanup`.
- `auth/mail`: `EmailVerificationRequested` y `VerificationEmailListener`; `auth/security`: filtros de cuotas de autenticación/verificación y tamaño HTTP.
- `identity/model`/`identity/repository`: `User`, `Role`, `RoleCode`, `UserRole`, identificadores compuestos, preferencias y sus repositorios.
- `shared/security`: `SecurityConfig`, `DatabaseJwtAuthenticationConverter`, configuración JWT y handlers; `shared/mail`: `EmailSender`/`SmtpEmailSender`; `shared/ratelimit` y `shared/error`: limitación y `ProblemDetail` comunes.
- `invitations/*`: emisión/canje y HMAC específicos, **no** una segunda implementación de registro o login. Véase [ADR-0007](adr/0007-invitaciones-personal.md).

La clase principal sigue en `es.grupo18.urbanpulse.BackendApplication`. El cambio de estructura no convirtió la aplicación en microservicios ni alteró las rutas HTTP.

## 2. Modelo persistido y registro

`User` posee UUIDv7, correo normalizado (trim/minúsculas), contraseña BCrypt, estado activo, `emailVerified`, `tokenVersion` y preferencias de notificación. Al registrarse, el usuario inicia con `CITIZEN`, `emailVerified=false`, `tokenVersion=0` y preferencias activadas por defecto. `Role` contiene los cinco códigos y `UserRole` relaciona globalmente roles con usuarios; el ámbito se determina en `RoleCode` (`isGlobal`/`isDepartmental`), **sin** `Role.scope` ni `RoleScope`. `EmailVerification` guarda el HMAC y vigencia del código asociado al usuario.

| Método | Ruta bajo `/api/v1`                 | Respuesta normal                                                                                                       |
| ------ | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/auth/register`                    | `201 TokenDTO`, crea usuario `CITIZEN`, genera verificación; sin cabecera `Location`, conforme al contrato de registro |
| `POST` | `/auth/login`                       | `200 TokenDTO` o `401` genérico                                                                                        |
| `POST` | `/users/me/password-changes`        | `204`, requiere cuenta autenticada                                                                                     |
| `POST` | `/auth/email-verifications/confirm` | `204`, confirma código asociado al titular                                                                             |
| `POST` | `/auth/email-verifications/resend`  | `202` al solicitar envío, `204` si ya verificado, `410` si venció la cuenta                                            |

La emisión de registro crea cuenta/rol/verificación en una operación SQL. El correo se solicita **tras confirmar** la transacción; `201` acredita persistencia, **no recepción** del correo. El JWT inicial permite operaciones de autenticación expresamente exceptuadas mientras la cuenta sigue sin verificar, pero no las rutas protegidas de negocio.

## 3. JWT y Spring Security

- Tokens de acceso **HS256** de **7200 segundos (2 horas)**, con `sub`, `iat`, `exp`, `iss` y `ver`; no hay refresh token.
- `JWT_SECRET` se suministra externamente, en Base64 de al menos 32 bytes aleatorios; `JWT_ISSUER` configura el emisor. No versionar claves reales ni utilizar valores de `auth-test.properties` fuera de tests.
- `DatabaseJwtAuthenticationConverter` consulta **en cada petición protegida** el usuario, actividad, `tokenVersion`, rol base `CITIZEN` y **roles globales vigentes** en PostgreSQL. Los roles no se toman del JWT como autoridad permanente.
- La autoridad `EMAIL_VERIFIED` solo se concede si `User.emailVerified=true`; las rutas protegidas la exigen, salvo confirmación/reenvío/cambio de contraseña y excepciones de autenticación previstas. Una cuenta pendiente recibe `403 EMAIL_NOT_VERIFIED`.
- Confirmar el correo no modifica `tokenVersion`, por lo que el **mismo JWT** adquiere acceso normal en la petición siguiente. Canjear una invitación tampoco cambia la versión: el mismo JWT puede reconocer un nuevo rol global desde PostgreSQL.
- Cambiar la contraseña exige la vigente, utiliza BCrypt con coste 12, bloquea al usuario y aumenta **transaccionalmente** `tokenVersion`, de modo que los tokens previos dejan de funcionar en la próxima petición.

Las futuras autorizaciones de incidencias requieren comprobar, además del rol global, pertenencia departamental, titularidad, asignación vigente, estado y tipo de recurso. `x-roles` en OpenAPI es **documental**, no sustituye los controles ejecutables del servicio.

## 4. Verificación de correo y limpieza

La verificación utiliza códigos de **ocho dígitos decimales ASCII**, generados con `SecureRandom`, protegidos mediante HMAC-SHA256 con secreto independiente `EMAIL_VERIFICATION_SECRET` y **vigencia máxima de diez minutos**. No deben aparecer códigos en bases de datos en claro, JWT, respuestas HTTP o logs. Las confirmaciones/reenvíos requieren JWT válido y validan cuenta, versión y estado bajo bloqueo.

`EmailVerificationService` confirma o reemite, y `VerificationEmailListener` entrega por `EmailSender` mediante `@TransactionalEventListener(AFTER_COMMIT)`. `EmailVerificationCleanup` limpia códigos vencidos y cuentas que siguen sin verificar tras **24 horas**, con barrido ordinario horario, lotes acotados y `SKIP LOCKED`. `EMAIL_VERIFICATION_CLEANUP_CRON` permite configurar su calendario; `-` lo desactiva en tests. No aplicar esta limpieza en un entorno compartido sin revisar las nuevas FK y política de retención.

La ampliación de V1 para crear `email_verifications` se realizó **antes de su primera aplicación prevista en Neon**. Si una base ya registró otra versión de V1, es incorrecto reescribirla silenciosamente: conservar el historial Flyway y crear una migración nueva. La limpieza conserva cuentas con referencias históricas en pertenencias, actores de invitaciones o auditoría, las excluye antes de limitar el lote y revalida bajo bloqueo. El destinatario de la invitación sigue como email **sin FK**: eliminar una cuenta pendiente sin referencias no consume su invitación.

## 5. SMTP en desarrollo y fallos de entrega

`shared/mail/EmailSender` abstrae el envío; `SmtpEmailSender` utiliza Spring Mail y configuración externa (`MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, opciones AUTH y STARTTLS). En Compose de desarrollo, Mailpit captura mensajes en SMTP `1025` con interfaz `http://localhost:8025`. No hay proveedor SMTP de Internet ni garantía de entrega; la falta de outbox significa que **fallo de envío después del commit no revierte los datos**. Para verificación existe reenvío manual; para invitaciones no existe y se usa revocación/nueva emisión.

## 6. Cuotas, validación, errores y privacidad

El backend reutiliza `shared/ratelimit/InMemoryRateLimiter` con token buckets **locales a la instancia** y memoria acotada. Las políticas de auth incluyen registro por IP **5/300 s**, login por IP **20/1200 s** y login por correo **5/300 s**; la configuración de verificación aplica confirmación usuario **5/3600 s**, IP **30/3600 s**, reenvío cooldown **1/60 s**, usuario **5/86400 s** e IP **20/3600 s**. El límite protector de cuerpo para los cinco POST auth es **16 KiB**. Los contadores se pierden al reiniciar y no son distribuidos. Se usa la IP de conexión, sin confiar en `Forwarded` ni `X-Forwarded-For` proporcionadas por clientes.

Las respuestas siguen un `ProblemDetail` uniforme: `400` para JSON no interpretable, tipos erróneos o exceso de cuerpo; `401` para credenciales, JWT, cuenta o rol base inválidos; `403` para acceso sin permisos/correo pendiente; `409` para email ya existente; `422` para datos interpretables no válidos; `429` con `Retry-After` para cuota; `503` para indisponibilidad tipada de PostgreSQL y `500` saneado para fallos internos. No divulgar contraseñas, tokens, códigos, HMAC ni SQL interno en respuestas o logs. Evitar depender de cadenas de mensajes para control de errores en el cliente; emplear sus códigos.

## 7. Evidencias y límites de verificación

**Pruebas realizadas:** una primera versión de autenticación registró **23 pruebas** (16 unitarias/MVC + 7 PostgreSQL IT); tras verificar correo se completaron **55** (35 + 20); la entrega de Invitaciones registró **90 pruebas superadas** (48 + 42: 8 de `AuthenticationPostgresIT`, 12 de `EmailVerificationPostgresIT` y 22 de `StaffInvitationPostgresIT`) y un flujo HTTP satisfactorio con el JAR, PostgreSQL y Mailpit aislados. El historial Git sitúa la reorganización antes de los commits de Invitaciones y pruebas.

Para repetir desde `backend/` (Java 25, Maven y Docker):

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

Las suites de persistencia se ejecutan contra PostgreSQL 18 Testcontainers, no Neon; el perfil excluye `NeonConnectivityIT`. El flujo HTTP histórico se describe en [Desarrollo, pruebas aisladas](development.md), con la advertencia sobre los archivos de runner ausentes del repositorio. La prueba de conectividad `NeonConnectivityIT` es **otra comprobación**, ejecutada históricamente en `development`, que no valida Flyway V1 consolidada sobre Neon ni operaciones completas de S3.

## 8. Integración con el canje municipal

`StaffInvitationService` comprueba cuenta activa y verificada con `CITIZEN`, correo normalizado exactamente coincidente y versión JWT dentro de la transacción. Bloquea primero `User` para coordinar canjes, cambios de contraseña y limpieza; después la invitación y, cuando corresponde, el departamento. Concede un `UserRole` global o un `DepartmentMembership` activo, conserva los permisos previos, marca la invitación canjeada y audita **atómicamente**. Una invitación puede dirigirse a quien todavía no tiene cuenta; el registro y la verificación del módulo auth se reutilizan sin una ruta de staff registration.

El `AuthRateLimitFilter` se mantiene en `auth/security` aunque incluye protección general del cuerpo HTTP utilizada por Invitaciones; podría extraerse en el futuro a `shared/security` si se justifica, **sin necesidad de reorganizar ahora el código ni dividir `SecurityConfig`**. El canje usa otro filtro específico y cuotas por IP/usuario. Reglas y seguridad específicas en [ADR-0007](adr/0007-invitaciones-personal.md); variantes de DTO, estados y errores en [API](api/README.md).

## 9. Funcionalidades pendientes

Procedimiento controlado del primer `ADMIN` productivo; recuperación de contraseñas olvidadas por email; tokens renovables/refresh e invalidación más fina; transporte SMTP real con outbox y reintentos; rotación de secretos JWT/HMAC; política de protección multiinstancia y confianza de IP tras proxy; HTTPS/CORS/cookies/CSRF según despliegue; autorización granular por departamento, equipo, titularidad y estado; opción futura de `GET fields` con permisos por campo. No se da por implementado lo pendiente.

## 10. Referencias

- [Arquitectura modular](architecture.md), [modelo de dominio](domain-model.md) y [guía de desarrollo](development.md).
- [Contrato OpenAPI](api/README.md), [guía dominio–API](api/domain-openapi.md) y [pruebas de Invitaciones](development.md).
- [ADR-0005 — JWT](adr/0005-jwt-spring-security.md), [ADR-0006 — Flyway](adr/0006-flyway.md), [ADR-0007 — Invitaciones](adr/0007-invitaciones-personal.md) y [SECURITY.md](../SECURITY.md).

## Usuarios y pertenencias

Registro y PATCH de perfil comparten normalización previa del nombre: recorte de espacios Unicode y controles de borde, 2–120 puntos de código y rechazo de controles interiores. Registro devuelve `departmentMemberships: []`; login, perfil y administración incluyen la historia ordenada de pertenencias. Los permisos efectivos siguen consultándose en PostgreSQL.

Las nuevas pertenencias exigen usuario activo/verificado y departamento activo. El servicio interno permite finalizar y reingresar conservando filas históricas, sin exponer nuevas rutas. La desactivación administrativa protege al último ADMIN activo y verificado; la autodesactivación es posible si queda otro. [ADR-0012](adr/0012-gestion-usuarios-perfiles-y-administracion.md) y [ADR-0013](adr/0013-pertenencias-usuarios-departamentos.md).
