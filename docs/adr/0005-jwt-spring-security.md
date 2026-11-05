# ADR-0005: Autenticación JWT y autorización con Spring Security

- **Estado:** Aceptado; núcleo implementado en backend. Extensiones de correo e Invitaciones implementadas después.
- **Ámbito:** identidad, seguridad por petición y decisiones de autorización.

## Contexto

UrbanPulse necesita una identidad compartida para ciudadanos y personal municipal, sesiones sin estado de servidor y permisos controlados aunque cambie el rol de una cuenta. La API REST no debe depender de sesiones en memoria ni confiar en autorizaciones declaradas por el frontend o por `x-roles` en OpenAPI. Una credencial válida no garantiza por sí sola que la cuenta siga activa, conserve permisos ni tenga correo verificado.

## Alternativas consideradas

Se valoraron sesiones de servidor, JWT con roles incrustados como autoridad hasta expirar y JWT firmado con validación del estado de seguridad actual contra PostgreSQL. Se adopta esta última variante porque permite invalidación global tras cambio de contraseña, activación de permisos tras canje y denegación inmediata de cuentas inactivas, a costa de una consulta a base de datos por petición protegida y de requerir disponibilidad de PostgreSQL.

## Decisión implementada

- **Una cuenta `User`** por correo normalizado; al registrarse recibe `CITIZEN`, `emailVerified=false`, `tokenVersion=0` y preferencias activadas por defecto. `Role` y `UserRole` almacenan roles globales; `RoleCode` diferencia ámbitos sin columna `Role.scope`.
- JWT Bearer **HS256** de **7200 s** con claims `sub`, `iat`, `exp`, `iss` y `ver`. `JWT_SECRET` externo Base64, al menos 32 bytes aleatorios, con emisor `JWT_ISSUER` configurado. No hay refresh.
- En cada petición protegida, `DatabaseJwtAuthenticationConverter` comprueba usuario, actividad, presencia de `CITIZEN`, `tokenVersion`, correo verificado y roles globales **actuales en PostgreSQL**, sin confiar en claims de rol antiguos.
- La autoridad `EMAIL_VERIFIED` se incorpora cuando procede y se exige por defecto para negocio; confirmar/reemitir y cambiar contraseña constituyen las excepciones autenticadas previstas. Un usuario sin correo confirmado recibe `403 EMAIL_NOT_VERIFIED` en rutas protegidas.
- Cambio de contraseña con bloqueo pesimista del usuario, BCrypt (coste 12) e incremento **transaccional de `tokenVersion`**. Los JWT anteriores dejan de funcionar en la siguiente petición; confirmar el correo o canjear una invitación **no** invalidan el JWT.
- `SecurityConfig`, handlers de errores y `ProblemDetailFactory` son compartidos (`shared/security`, `shared/error`); los controladores y servicios viven dentro de sus módulos (`auth`, `invitations`, `identity`). Los permisos dependientes de recursos se comprobarán dentro del caso de uso.

## Implementación y comprobaciones

Los endpoints de auth son los cinco POST de registro (`201`), login (`200`), cambio de contraseña (`204`), confirmación de correo (`204`) y reenvío (`202`/`204`/`410`), todos bajo `/api/v1`. El registro crea cuenta `CITIZEN` y comienza verificación de email; no expone un endpoint para registro de personal. El correo no verificado no da acceso normal al negocio.

El sistema responde con errores `ProblemDetail` uniformes: `400` JSON/tipos/cuerpo mal interpretables o mayor de 16 KiB, `401` credenciales/JWT/cuenta/rol base inválidos, `403` autorización denegada, `409` duplicidad de correo, `422` datos invalidables, `429` con `Retry-After`, `503` indisponibilidad tipada PostgreSQL y `500` errores internos saneados. Se evitan datos sensibles en logs y en respuestas.

Se completaron tres hitos de pruebas: **23** (16 unitarias/MVC + 7 PostgreSQL IT) el 25/10/2026; **55** (35 + 20) tras incorporar verificación; y **90 pruebas superadas** (48 + 42) en el informe de Invitaciones del 30/10/2026, además del recorrido HTTP aislado. El historial de commits sitúa el refactor **antes** de Invitaciones y del commit de pruebas. Estos resultados acreditan la validación del backend en entornos aislados, no un despliegue sobre Neon. [Resultados y comandos](../development.md).

## Extensión del 28/10/2026: verificación obligatoria y SMTP

`EmailVerification` y V1 ampliada incorporan códigos de **ocho dígitos** mediante HMAC-SHA256 de clave `EMAIL_VERIFICATION_SECRET` independiente de JWT. Vencen como máximo a los **diez minutos**; una cuenta pendiente se limpia después de **24 horas** mediante `EmailVerificationCleanup` (cron ordinario horario, lotes acotados y bloqueos `SKIP LOCKED`). El mismo JWT inicialmente limitado sirve para acceder a negocio tras verificar sin cambiar `tokenVersion`.

`EmailSender` y `SmtpEmailSender` residen en `shared/mail`. `VerificationEmailListener` escucha `AFTER_COMMIT`: un fallo SMTP posterior no revierte registro/reenvío. Mailpit en Compose es **correo local**, sin entrega externa garantizada ni outbox. Existen cuotas locales independientes: registro/IP **5/300 s**, login/IP **20/1200 s** y login/email **5/300 s**; confirmación/usuario **5/3600 s**, confirmación/IP **30/3600 s**; reenvío enfriamiento **1/60 s**, usuario **5/86400 s**, IP **20/3600 s**. No son ventanas fijas ni límites distribuidos. Las cinco operaciones POST auth conservan límite protector de **16 KiB**.

V1 se amplió **antes** de su primera aplicación prevista en Neon; no modificar su checksum si una BD ya la ejecutó. La limpieza deberá revisarse al introducir nuevas FK a `users`, evitando borrar historial necesario o vulnerar políticas de privacidad.

## Extensión del 31/10/2026: integración con Invitaciones

Las cinco rutas de Invitaciones implementadas después de la reorganización exigen **`ADMIN` activo/verificado** para crear/listar/consultar/revocar, y **`CITIZEN` activo/verificado** para canjear. Dentro de una transacción se vuelve a comprobar actividad, versión y roles, bloqueando primero `User`, luego invitación y departamento cuando existe. Emitir no crea `User`; el titular usa registro y verificación normales. Canjear añade `UserRole` o `DepartmentMembership` sin cambiar `tokenVersion`: un JWT anterior al canje reconoce un nuevo rol global por la lectura posterior de PostgreSQL.

Las cuotas de canje son independientes, **10/900 s por usuario** y **30/900 s por IP**, gestionadas por `InvitationRateLimitFilter` con el limitador compartido en memoria. El límite general de cuerpo sigue aplicándose a sus POST mediante un filtro actualmente alojado en `auth/security`; una posible extracción futura a `shared/security` es opcional. La emisión usa `AFTER_COMMIT` y HMAC independiente, sin compartir el código ni el secreto de verificación. Más detalles en [ADR-0007](0007-invitaciones-personal.md) y [contrato API](../api/README.md).

## Consecuencias, riesgos y límites

El JWT es compacto y no exige almacenamiento de sesión, pero el sistema depende de PostgreSQL para validar permisos actuales; una base temporalmente indisponible debe causar `503`, **no** un `401` falso ni una autorización basada en permisos antiguos. La seguridad de cada tag necesita comprobar sus propias reglas de negocio además de los filtros HTTP. El rate limiting local no protege múltiples réplicas como un límite global ni se debe confiar ciegamente en `Forwarded` tras un proxy.

No están incluidos: refresh tokens, recuperación de contraseña olvidada, logout individual por token, bootstrap del primer ADMIN, retirada de permisos, gestión de equipos y asignaciones de trabajo, proveedor SMTP público, keyring/rotación segura y autorización granular de incidencias. La propuesta de selección `GET fields` y permisos por campo requiere un estudio propio, no tablas anticipadas.

## Referencias

- [Diseño funcional de Autenticación](../auth-design.md), [arquitectura modular](../architecture.md) y [OpenAPI modular](../api/README.md).
- [ADR-0006 — Flyway](0006-flyway.md), [ADR-0007 — Invitaciones](0007-invitaciones-personal.md) y [ADR-0009 — roles](0009-rbac-departamentos-equipos.md).
- [Pruebas y evidencia histórica de Invitaciones](../development.md).
