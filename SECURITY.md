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

**Correo y cuentas no verificadas:** la API solo permite cambiar contraseña, confirmar y reenviar con JWT válido mientras `emailVerified=false`; otras rutas protegidas responden `403 EMAIL_NOT_VERIFIED`. Los códigos de ocho dígitos se almacenan como HMAC, nunca en claro ni en logs, y su emisión está limitada por buckets de usuario/IP. Mailpit en desarrollo no entrega fuera del equipo. El envío transaccional tras commit no garantiza recepción y permite reenvío manual. Las cuentas sin verificar se eliminan al superar las 24 horas por un barrido horario; revisar dicho borrado antes de añadir entidades dependientes. Guardar `EMAIL_VERIFICATION_SECRET` separado de `JWT_SECRET`, fuera de Git.
