# Política de seguridad

UrbanPulse es un proyecto universitario en desarrollo cuyo repositorio de código es **público**. El hecho de que su código sea visible no autoriza a compartir credenciales o datos personales.

## Comunicación privada de vulnerabilidades

No publicar pruebas de explotación, tokens, credenciales ni datos personales en Issues o Pull Requests públicos. Comunicar el problema por un canal privado acordado con los responsables del proyecto. Si GitHub habilita la función de **notificación privada de vulnerabilidades** (_Private Vulnerability Reporting_) para este repositorio, utilizarla preferentemente. No se presupone que dicha función esté activada.

## Secretos y configuración

- Nunca versionar `.env` reales, contraseñas, tokens de usuario, secretos JWT, claves privadas ni credenciales JDBC o S3 de Neon.
- Usar archivos `.env.example` con valores ficticios para describir las variables esperadas. En desarrollo Compose carga `backend/.env.development`, ignorado por Git; las credenciales de `production` deben ser diferentes y proporcionarse mediante el entorno de despliegue.
- Las variables Expo `EXPO_PUBLIC_*` se incorporan al cliente: **son públicas y nunca deben contener secretos**.
- Aplicar mínimo privilegio, autorización en el backend y HTTPS en el despliegue.
- No incluir datos personales reales ni fotografías sensibles en tests, ejemplos, capturas o Issues. No ejecutar tests automáticos compartidos contra Neon `production` ni dar acceso al frontend a PostgreSQL o al bucket S3 privado.

## Dependencias y desarrollo

**Seguridad HTTP en esta etapa:** registro y login son públicos y están limitados por IP (login también por email normalizado); el cambio de contraseña, la confirmación y el reenvío exigen Bearer. Los cinco POST limitan cuerpos a 16 KiB. Los errores internos y de base de datos se registran sin incluir contraseñas, JWT ni mensajes JDBC; indisponibilidad de PostgreSQL = `503`, credenciales/token inválidos = `401`. El limitador usa `getRemoteAddr()` con `server.forward-headers-strategy=none`; revisar el proxy confiable antes de exponerlo detrás de un balanceador.

El frontend dispone de `npx expo install --check`, `npx expo-doctor`, `npx tsc --noEmit` y `npm run lint`. Estas comprobaciones no sustituyen a una revisión de seguridad.

Inspeccionar `npm audit` y priorizar los avisos de riesgo elevado antes de distribuir una versión. Las salidas previas de npm informaron de vulnerabilidades; no constan aún resultados que demuestren que hayan desaparecido. No ejecutar `npm audit fix --force` de forma automática si pudiera romper la compatibilidad con Expo.

El bucket `incident-evidence` ya existe con visibilidad privada y acceso S3 comprobado mediante `HeadBucket`. **La autorización de cada fotografía, la revisión de contenidos y las operaciones de subida/descarga todavía no están implementadas.** El módulo de autenticación ya incluye JWT HS256 de dos horas, BCrypt, control de rol `CITIZEN`, consulta de permisos vigentes en PostgreSQL, revocación mediante `tokenVersion` y limitación local por instancia. La rotación de claves, el despliegue HTTPS y el saneamiento de IP detrás de proxies se revisarán antes de producción.

**Correo y cuentas no verificadas:** la API solo permite cambiar contraseña, confirmar y reenviar con JWT válido mientras `emailVerified=false`; otras rutas protegidas responden `403 EMAIL_NOT_VERIFIED`. Los códigos de ocho dígitos se almacenan como HMAC, nunca en claro ni en logs, y su emisión está limitada por buckets de usuario/IP. Mailpit en desarrollo no entrega fuera del equipo. El envío transaccional tras commit no garantiza recepción y permite reenvío manual. Las cuentas sin verificar con más de 24 horas se eliminan por lotes solo si carecen de referencias históricas. Se conservan las vinculadas a pertenencias, invitaciones o auditoría y se excluyen antes del límite para no bloquear las demás. Guardar `EMAIL_VERIFICATION_SECRET` separado de `JWT_SECRET`, fuera de Git.

## Invitaciones municipales

ADMIN activo y verificado administra; el titular CITIZEN verificado canjea solo para
su propio correo normalizado. HMAC-SHA256 de códigos aleatorios de 256 bits, con
STAFF_INVITATION_SECRET externo e independiente del JWT y verificación. No se
expone código/hash por API ni en logs o auditoría. Rotar esa clave invalida los
códigos pendientes; coordinar revocación y nueva emisión. No hay reenvío de códigos.

Canje, permiso y auditoría comparten transacción y bloqueos; SQL impide permisos
activos duplicados. Las nuevas FK históricas restringen borrado; destinatario email
sin FK permite limpiar cuentas pendientes sin referencias históricas.
Los POST de invitaciones reutilizan 16 KiB; buckets de canje 10/900 s por usuario y
30/900 s por IP, locales por instancia. Primer ADMIN, retirada de permisos, cuota
de emisión por ADMIN, entrega fiable y SMTP productivo siguen fuera del módulo.
El flujo HTTP aislado ya ejecutado utilizó `compose.test.yaml` con material sintético,
sin credenciales reales ni puerto PostgreSQL publicado al host. Ese archivo no figura
en el repositorio; nunca se deben reutilizar los fixtures en producción.

**Usuarios y pertenencias:** perfil propio y administración requieren cuenta activa y correo verificado; ADMIN global administra cuentas. PATCH solo admite `displayName` en el perfil y `active` en administración. Se protege al último ADMIN activo y verificado, también frente a bajas simultáneas. Las altas departamentales requieren cuenta activa/verificada; el historial nunca concede autoridad global. Las actualizaciones y la limpieza se coordinan mediante bloqueos de usuario. La auditoría de Invitaciones se mantiene; RF18 administrativo sigue parcialmente pendiente.

## Incidencias

Registro y consulta propia requieren cuenta activa, CITIZEN y correo verificado. Listado administrativo solo OPERATOR/ADMIN globales. Detalle, historial y corrección: titular o esos roles; la corrección solo en REPORTED y con If-Match obligatorio. La autorización transversal para corregir terceros es provisional y debe revisarse por ámbito/campo antes de ampliar tramitación. Técnicos/coordinadores no heredan acceso global por pertenencia.

404 oculta recursos ajenos; DTO de informante/actor solo contienen UUID y displayName. El historial filtra recurso/acciones permitidas y no devuelve JSONB arbitrario. Las respuestas privadas usan no-store; ETag/If-Match evita actualizaciones perdidas y auditoría comparte transacción. Las nuevas FK se conservan al limpiar cuentas no verificadas. Retención/anonimización, cuotas específicas de Incidencias y prevención distribuida de abuso siguen como decisiones futuras en el [roadmap](docs/incidents-implementation-roadmap.md).
