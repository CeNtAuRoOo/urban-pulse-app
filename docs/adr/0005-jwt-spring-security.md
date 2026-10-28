# ADR-0005: Autenticación JWT y autorización con Spring Security

- **Estado:** Aceptado; núcleo de autenticación implementado en código (despliegue pendiente).
- **Fecha:** 2026-10-11.
- **Revisión de diseño:** 2026-10-25.
- **Verificación documental de implementación:** 2026-10-27.
- **Ámbito:** UrbanPulse, backend, identidad y seguridad.

## Contexto

UrbanPulse expone una API REST compartida por clientes web y móviles. Ciudadanos y personal municipal tienen permisos distintos y pueden tener varios roles. El backend debe validar cada operación, independientemente del frontend. La infraestructura Spring Boot/Spring Security y la conexión a Neon están preparadas; **los endpoints de autenticación y entidades de identidad se han incorporado al código, sin acreditación de despliegue en Neon `development`**.

## Opciones consideradas

1. JWT Bearer emitidos y verificados por el backend (**elegida**).
2. Sesiones HTTP mantenidas en el servidor.
3. Proveedor externo de identidad.
4. API keys compartidas (descartadas como autenticación de usuarios).

En cuanto a autorización: confiar en roles embebidos en JWT durante toda su vigencia o consultar el estado y los privilegios actuales en PostgreSQL (**elegida**). En cuanto a abusos: contador en PostgreSQL/Redis/gateway o limitación inicial local en la memoria del backend (**elegida por ahora**).

## Decisión implementada en el núcleo auth

1. **Registro ciudadano** `POST /auth/register` y **login** `POST /auth/login` mediante credenciales y BCrypt, con rechazo genérico de credenciales incorrectas; el registro público solo concede `CITIZEN` (ADR-0009). Ambos devuelven un `TokenDTO` con JWT de acceso y datos autorizados del titular; registro responde `201`, login `200`.
2. JWT Bearer **firmado**, sin datos sensibles ni roles como fuente autoritativa. Duración inicial **7200 segundos (2 h)**, sin refresh token. Claims mínimos `sub` (UUID), `iat`, `exp`, `iss` y `ver` (`User.tokenVersion`). El emisor y el material de firma se configuran externamente, nunca mediante secretos versionados. Se implementa HS256 mediante Spring Security/Nimbus, con `JWT_SECRET` Base64 externo de al menos 32 bytes aleatorios, emisor externo y rotación pendiente para producción.
3. Validar la firma, caducidad y claims en Spring Security y **consultar PostgreSQL en cada petición protegida** para comprobar cuenta activa, `tokenVersion` y roles vigentes. Comprobar también titularidad, ámbito departamental y estado de recurso cuando se implementen esos casos. Un JWT emitido antes de una revocación de rol no concede permisos retirados; una cuenta desactivada pierde acceso en la siguiente petición.
4. **Cambio de contraseña autenticado:** `POST /users/me/password-changes`. Comprobar contraseña actual, guardar el hash BCrypt nuevo e **incrementar `User.tokenVersion` en la misma transacción**. Todos los JWT anteriores, incluido el empleado para cambiarla, dejan de ser válidos en las siguientes peticiones. La respuesta de éxito es `204` y el cliente deberá iniciar sesión de nuevo.
5. **Rate limiting inicial en memoria por instancia**, en la capa/filtros de seguridad, antes de operaciones costosas. Aplicar políticas diferenciadas a registro y login, por IP y, en login, también por identificador normalizado de cuenta. Contadores con expiración/reposición temporal y tamaño acotado; rechazar exceso con `429` y `Retry-After` cuando sea calculable. Las capacidades y reposiciones actuales son registro/IP 5/300 s, login/IP 20/1200 s, login/email 5/300 s, con máximo 10 000 buckets, TTL propio por política y cuerpo de los cinco POST limitado a 16 KiB (confirmación/reenvío consumen buckets independientes y cambio de contraseña ninguno). Son parámetros de implementación revisables, no cuotas empresariales definitivas. Una instancia reiniciada pierde sus contadores y varias instancias no comparten límite.
6. **Errores uniformes:** `application/problem+json` (`ProblemDetail`). `400` para JSON no interpretable, `422` para datos válidamente parseados que incumplen validaciones, `409` para registro con email duplicado, `401` para fallo de login/token/cuenta o ausencia de rol `CITIZEN`, `403` para falta de privilegios, `503` para indisponibilidad de PostgreSQL tipada y `500` para errores inesperados. Las respuestas `503`/`500` no llevan `WWW-Authenticate`. Integrar `@RestControllerAdvice` con `AuthenticationEntryPoint`/`AccessDeniedHandler` y filtros, porque algunas excepciones ocurren antes del controlador.
7. **Correo (implementado localmente):** el registro crea `emailVerified=false` y un código HMAC protegido en la misma transacción. Se envía por SMTP después del commit; confirmación por código marca `emailVerified=true` y elimina la solicitud de forma atómica. En cada petición protegida se lee el estado en PostgreSQL y se exige `EMAIL_VERIFIED` para negocio; los JWT de cuentas pendientes solo sirven para login/confirmación/reenvío/cambio de contraseña. Recuperación de contraseña por email permanece pendiente.
8. **Preferencias iniciales:** `NotificationPreferences` con `inApp`, `email` y `push` en `true`; guardar como preferencia, sin implementar envíos ni confundirla con permiso del dispositivo o consentimiento de comunicaciones comerciales.
9. Proteger con HTTPS/TLS en despliegue, definir CORS de orígenes permitidos y revisar CSRF según transporte Bearer. No registrar contraseñas, hashes, JWT ni secretos; usar errores no enumerables. La autenticación del servicio PostgreSQL permanece independiente de los JWT de usuarios.

## Implementación y comprobaciones

- **Implementado:** `AuthController` expone cinco POST; `AuthService` usa BCrypt **coste 12**, registro transaccional con CITIZEN y cambio bloqueado mediante `PESSIMISTIC_WRITE` con incremento de `tokenVersion`. `DatabaseJwtAuthenticationConverter` consulta cada petición y exige rol base CITIZEN además de cuenta activa/versión. Secretos y CORS externos; sin roles en claims autoritativos.
- **Implementado:** errores RFC 9457 `ProblemDetail` en MVC y filtros; `AuthenticationBackendException` distingue interrupción de PostgreSQL (`503`) de fallo lógico (`500`) sin filtrar detalles JDBC. Logging selectivo por tipo de error. Token buckets locales; límites de cuerpo de 16 KiB.
- **Esquema:** `V1__create_authentication.sql` contiene exclusivamente `users`, `roles` y `user_roles`, precarga cinco roles y no añade `Role.scope`; `email_verified=false`, `token_version=0`, preferencias `true`, UUIDv7 Hibernate.
- **Verificación histórica:** Codex registró el 25/10/2026 una ejecución con 23 pruebas superadas (16 unitarias/MVC y 7 PostgreSQL efímero). Las correcciones posteriores incluyen nuevas pruebas HTTP de 503/500 y CITIZEN y protección de cuerpo; **la ejecución final comunicada para el módulo ampliado incluye 55 pruebas superadas**. No consta aplicación de Flyway V1 sobre Neon `development` ni despliegue. Contrato modular/artefactos y diagramas se revisan por separado.

## Mejoras futuras expresamente fuera de esta rama

- Refresh tokens, renovación y cierre de sesiones individual.
- Proveedor de envío real, outbox/reintentos automáticos y recuperación de contraseña por correo. La verificación local SMTP/Mailpit con códigos ya se ha implementado.
- Recuperación de contraseña olvidada por correo, tokens de recuperación de uso único y caducidad.
- Despliegue de limitación compartida mediante gateway/Redis si hay escalado horizontal o requisitos estrictos de límite global.
- Política avanzada de visibilidad de campos `fields` y anotaciones `OWNER`, `ADMIN`, `NONE`, etc. Pendiente de investigación y aprobación; no forma parte del contrato activo.

## Consecuencias y riesgos

**Ventajas:** API autocontenida, autorización coherente con el dominio, revocación por cambio de contraseña sin tabla de tokens, coste operativo bajo inicialmente.

**Costes/riesgos:** lectura de PostgreSQL en cada petición protegida; posibilidad de fallo de conexión, caducidad de sesión sin refresh y ausencia temporal de email verificable. El limitador local no proporciona una cuota global entre instancias. El JWT robado seguirá funcionando hasta caducar si no se cambia contraseña, desactiva cuenta o retiran sus permisos. La consulta de `tokenVersion` solo invalida tokens al incrementar su valor por una acción aprobada, no existe una blacklist por token.

## Fuentes y relaciones

- [Diseño funcional de autenticación](../auth-design.md).
- [ADR-0007: invitaciones](0007-invitaciones-personal.md).
- [ADR-0009: roles](0009-rbac-departamentos-equipos.md).
- [ADR-0011: UUIDv7](0011-identificadores-uuidv7.md).
- [OpenAPI modular](../api/openapi/paths/autenticacion.yaml).

**Estado real:** núcleo auth implementado en código y pruebas diseñadas; no implica despliegue. Se debe repetir la suite completa tras las últimas correcciones.

### Ampliación de implementación — 28/10/2026

- `DatabaseJwtAuthenticationConverter` incorpora la autoridad `EMAIL_VERIFIED` cuando `User.emailVerified=true`. `SecurityConfig` la exige por defecto, con excepción para confirmación/reenvío y cambio de contraseña. Una cuenta sin verificar recibe `403 EMAIL_NOT_VERIFIED` en las rutas protegidas restantes; no se cambia `tokenVersion` al verificar.
- `EmailVerification` y tabla de código HMAC de ocho dígitos, generados con `SecureRandom`; clave externa Base64 de mínimo 32 bytes distinta de JWT. Vida del código: diez minutos como máximo; cuenta no verificada: 24 horas; bloqueo pesimista en confirmación/reenvío.
- SMTP desacoplado mediante `EmailSender`, implementado con `JavaMailSender`, `VerificationEmailListener` y evento `AFTER_COMMIT`. En desarrollo Compose ejecuta Mailpit; ningún proveedor externo está activado. La respuesta del registro/reenvío puede ser éxito aunque el correo no se entregue; se permite reenvío manual, sin outbox.
- Nuevos buckets locales (confirmación usuario 5/3600 s, IP 30/3600 s; reenvío enfriamiento 1/60 s, usuario 5/86400 s, IP 20/3600 s). No son ventanas fijas ni sobreviven a reinicios. Los cinco POST tienen máximo 16 KiB.
- `EmailVerificationCleanup` elimina códigos vencidos y cuentas sin verificar tras 24 horas, en barridos horarios limitados por lote y `SKIP LOCKED`; su política **se revisará antes de añadir nuevas FK a usuarios**. Codex comunicó 55 tests superados, sin ejecución conjunta backend real+Mailpit ni migración sobre Neon.
