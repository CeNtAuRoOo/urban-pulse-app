# UrbanPulse — Autenticación y seguridad (diseño implementado)

> **Estado a 27/10/2026:** módulo de autenticación implementado en el backend de la rama `feature/modulo-autenticacion`. Se dispone de entidades, Flyway V1, servicios, configuración JWT y pruebas. **No significa desplegado:** la V1 no consta aplicada a Neon `development` y se debe repetir la suite completa tras las últimas correcciones. Los demás tags siguen siendo diseño revisable.

## 1. Referencias y alcance

Consultar [arquitectura](architecture.md), [dominio](domain-model.md), [contrato modular](api/openapi/base-openapi.yaml), [ADR-0005](adr/0005-jwt-spring-security.md), [ADR-0006](adr/0006-flyway.md), [ADR-0009](adr/0009-rbac-departamentos-equipos.md) y [ADR-0011](adr/0011-identificadores-uuidv7.md). La unidad implementada es el tag **Autenticación**, no todos los usuarios/invitaciones del proyecto.

| Funcionalidad                                                                              | Estado                                |
| ------------------------------------------------------------------------------------------ | ------------------------------------- |
| `POST /api/v1/auth/register` y `POST /api/v1/auth/login`                                   | **Implementada**                      |
| `POST /api/v1/users/me/password-changes` y revocación por versión                          | **Implementada**                      |
| `User`, `Role`, `UserRole`, `NotificationPreferences`, Flyway V1                           | **Implementada**                      |
| JWT de dos horas, comprobación de rol base `CITIZEN`, estado y versión por petición        | **Implementada**                      |
| Rate limiting por instancia, cuerpo limitado, `ProblemDetail` y fallos de BD diferenciados | **Implementada**                      |
| Verificación efectiva y obligatoria del email; recuperación olvidada; refresh              | **Mejoras futuras**                   |
| Primer `ADMIN` mediante procedimiento manual controlado                                    | **Tarea posterior**                   |
| Invitaciones, membresías departamentales, equipos                                          | **Otros tags**                        |
| `GET ?fields=` y anotaciones de visibilidad por propiedad                                  | **Investigación futura, sin aprobar** |

## 2. Modelo de identidad realmente persistido

La migración `backend/src/main/resources/db/migration/V1__create_authentication.sql` crea únicamente `users`, `roles`, `user_roles` y carga los cinco roles. `Role` solo tiene `code` y `description`; **no existen `RoleScope` ni `Role.scope`**. `RoleCode.isGlobal()` y `isDepartmental()` derivan el ámbito. `UserRole` solo admite `CITIZEN`, `OPERATOR`, `ADMIN`; departamentales quedarán ligados a `DepartmentMembership` en otro tag.

`User`: UUIDv7 generado por Hibernate, `displayName` (2–120), email normalizado (`trim` y `toLowerCase(Locale.ROOT)`, máximo 254), BCrypt, `active=true`, `emailVerified=false`, `tokenVersion=0`, fechas `Instant` y preferencias `inApp/email/push=true` (`@Embeddable`, columnas de `users`). PostgreSQL conserva comprobaciones de longitudes, email normalizado, `tokenVersion≥0`, catálogo, roles globales, PK/FK e índice único `users_email_unique`. No se crea una tabla de sesiones.

Todos los usuarios han de conservar `CITIZEN`, incluidos operadores y administradores. La creación vía registro lo asigna transaccionalmente. **La autenticación rechaza un usuario que carezca de `CITIZEN`**, aunque disponga de `ADMIN`; no intenta arreglar automáticamente los datos. No existe todavía una restricción SQL global que fuerce `CITIZEN` a cada fila de `users`.

## 3. Registro y login

- **Registro público:** `POST /auth/register`, `RegisterRequest(displayName,email,password)`. JSON estricto, no acepta roles/flags inyectados. BCrypt coste **12**, mínimo 8 caracteres y máximo **72 bytes UTF-8** (sin truncamiento). Hash generado antes de la transacción; se guardan usuario y `UserRole(CITIZEN)` atómicamente. La unicidad SQL resuelve carreras y devuelve `409` por correo duplicado. Devuelve `201 TokenDTO` con JWT y `UserDTO`; **no envía `Location`** hasta disponer de `GET /users/me`.
- **Login público:** `POST /auth/login`, `LoginRequest(email,password)`, normalización de correo; hash BCrypt ficticio para reducir diferencias temporales cuando no exista la cuenta. Correo desconocido, contraseña incorrecta y cuenta inactiva generan el mismo `401` sin enumerar cuentas. Éxito `200 TokenDTO`. Contraseña excesiva también genera `401`. `emailVerified=false` **no bloquea acceso en esta fase**.
- Los DTO y tokens no exponen `passwordHash` ni `tokenVersion`. `UserDTO` incluye `emailVerified` y roles globales actuales; no inventar pertenencias departamentales.

## 4. JWT y seguridad por petición

JWT Bearer **HS256** firmado por Nimbus/Spring Security, secreto externo `JWT_SECRET` en Base64 que decodifica a **≥32 bytes aleatorios** y `JWT_ISSUER` externo; duración fija de **7200 s**. Claims `sub` (UUID canónico), `iat`, `exp`, `iss`, `ver`. No se confía en un claim de roles ni se emite email en el token. Sin refresh ni cookies/sesiones de servidor; CORS con lista explícita de orígenes, `allowCredentials=false`, CSRF desactivado al usar exclusivamente `Authorization: Bearer`.

Spring valida firma, emisor, fechas y claims. `DatabaseJwtAuthenticationConverter` ejecuta en cada petición protegida una proyección de PostgreSQL con actividad, `tokenVersion` y roles globales vigentes; exige `CITIZEN`, rechaza usuarios desactivados o JWT con versión distinta y genera las autoridades `ROLE_*`. Por diseño es **fail closed** y sin caché entre peticiones. Los permisos por propiedad, pertenencia departamental, titularidad y estado del recurso siguen fuera del módulo; `x-roles` en OpenAPI es documental.

## 5. Cambio de contraseña

`POST /users/me/password-changes` pertenece al tag Autenticación; requiere Bearer y `ChangePasswordRequest(currentPassword,newPassword)`. Se valida la contraseña actual y el mínimo de la nueva; se impone máximo de 72 bytes UTF-8. `UserRepository.findForPasswordChange` usa `PESSIMISTIC_WRITE`/`SELECT FOR UPDATE` en una transacción; vuelve a comprobar actividad y versión del JWT ya bajo bloqueo. `User.changePassword` actualiza hash e incrementa `tokenVersion`, y el `flush` hace que ambos cambios queden confirmados o revertidos conjuntamente. Devuelve **204 sin cuerpo**. El token utilizado y todos los anteriores quedan inválidos en las siguientes peticiones. Se permite cambiar a una contraseña idéntica: también revoca tokens.

## 6. Errores y observabilidad

`ProblemDetail` con `application/problem+json`; `GlobalExceptionHandler` atiende MVC y `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`/filtro atienden errores de seguridad. `400`: JSON mal formado, tipos incompatibles o cuerpo >16 KiB. `422`: Bean Validation o propiedad JSON no admitida. `409`: email duplicado. `401`: credenciales inválidas, token inválido/caducado/revocado, cuenta inactiva o sin `CITIZEN`. `403`: permisos insuficientes. `429`: bucket agotado (`Retry-After`). **`503`: indisponibilidad tipada de PostgreSQL** (conexión, recurso, timeout/SQLSTATE contemplado); `500`: fallo interno inesperado o error SQL de programación. En `503` y `500` no se añade `WWW-Authenticate`; en `401`, sí.

`AuthenticationBackendException` transporta la categoría y el tipo de fallo sin conservar mensajes/cadenas de causas JDBC; se usa el failure handler de Spring Security para que las excepciones de servicio no salgan del filtro sin respuesta homogénea. Logging propio selectivo de errores de infraestructura por clase de excepción, sin JWT, contraseñas ni mensajes JDBC. Los errores de dependencias opcionales de futuro no se convierten automáticamente en bloqueo de auth.

## 7. Protecciones frente a abuso

`AuthRateLimitFilter` valida el cuerpo de los **tres POST de autenticación**, con **máximo 16 384 bytes**, conservándolo para su lectura posterior; el límite funciona también si falta `Content-Length`. Aplica **rate limiting solo a registro y login**, antes de BCrypt: registro por IP **5 tokens/300 s**; login por IP **20/1200 s**; login por correo normalizado **5/300 s**. Reposición continua, no reinicio de ventana; `Retry-After` aproximado. `InMemoryRateLimiter` limita a **10 000 claves** y usa TTL de inactividad igual al mayor periodo de reposición configurado (actualmente **1200 s**), con limpieza oportunista y reloj monotónico. Las cuotas son **por instancia** y se pierden al reiniciar.

`server.forward-headers-strategy=none`: usa exclusivamente IP de la conexión, sin confiar en `Forwarded`/`X-Forwarded-For` del cliente. **Revisar al implantar un proxy confiable**, porque los usuarios podrían compartir el límite de la IP del intermediario. No incorporar Redis ni un gateway ahora. El entorno de pruebas puede utilizar `login-ip.refill-seconds=300`, distinto de los 1200 s configurados en el servicio.

## 8. Pruebas y despliegue

Existe una suite de tests unitarios/MVC y `AuthenticationPostgresIT` sobre **PostgreSQL 18 efímero** con Testcontainers: migración Flyway, validación Hibernate, UUIDv7, restricciones/rollback, conflictos bajo concurrencia, bloqueo de cambios de contraseña y revocación de JWT. MVC cubre `401`/`403`/`500`/`503`, ausencia de `CITIZEN`, tamaños de cuerpo, logs sin secretos, CORS y rate limiting. **Evidencia documentada de Codex el 25/10/2026: 16 unitarios/MVC y 7 de integración superados**; las nuevas correcciones requieren volver a ejecutar la suite actual y registrar el resultado real. Comando desde `backend/`:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

`NeonConnectivityIT` permanece manual; no se ha aplicado la nueva V1 sobre Neon `development`. Antes del primer arranque de la versión nueva con Compose, comprobar destino de las variables y realizar respaldo/procedimiento de recuperación. **Jamás usar Neon `production` para experimentos.** `ddl-auto=validate` permanece activo.

## 9. Tareas futuras y reglas por tag

- **Correo:** verificación real y **bloqueante antes de utilizar la app** cuando se implemente, y recuperación de contraseña olvidada por email; diseñar tokens de uso único y no enumeración.
- **Refresh:** tokens renovables y cierre de sesión individual, aplazados.
- **Primer administrador:** procedimiento manual posterior con hash BCrypt local temporal e inserciones administradas, asignando `CITIZEN` y `ADMIN`, sin secretos versionados.
- **Invitaciones:** solo `OPERATOR`/`ADMIN` como roles globales invitados; `CITIZEN` es siempre base; roles departamentales y verificaciones de email en su tag.
- **Autorización futura:** permisos por departamento, titularidad, estado y equipo; revisar `SecurityConfig` en cada tag. Investigar `GET fields` y anotaciones `OWNER`/`ADMIN`/`NONE` sin anticipar implementación.
- **Infraestructura:** rotación de secretos, HTTPS, CORS por despliegue, resolución de IP de proxies, validación en Neon development y pruebas de imágenes Docker.

Consultar [guía de desarrollo](development.md), [seguridad](../SECURITY.md), [OpenAPI](api/README.md) y [ADR](adr/README.md) para mantener las decisiones sincronizadas.
