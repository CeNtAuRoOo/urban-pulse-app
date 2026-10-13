# Política de seguridad

UrbanPulse es un proyecto universitario en desarrollo cuyo repositorio de código es **público**. El hecho de que su código sea visible no autoriza a compartir credenciales o datos personales.

## Comunicación privada de vulnerabilidades

No publicar pruebas de explotación, tokens, credenciales ni datos personales en Issues o Pull Requests públicos. Comunicar el problema por un canal privado acordado con los responsables del proyecto. Si GitHub habilita la función de **notificación privada de vulnerabilidades** (_Private Vulnerability Reporting_) para este repositorio, utilizarla preferentemente. No se presupone que dicha función esté activada.

## Secretos y configuración

- Nunca versionar `.env` reales, contraseñas, tokens de usuario, secretos JWT, claves privadas ni credenciales JDBC o S3 de Neon.
- Usar archivos `.env.example` con valores ficticios para describir las variables esperadas.
- Las variables Expo `EXPO_PUBLIC_*` se incorporan al cliente: **son públicas y nunca deben contener secretos**.
- Aplicar mínimo privilegio, autorización en el backend y HTTPS en el despliegue.
- No incluir datos personales reales ni fotografías sensibles en tests, ejemplos, capturas o Issues.

## Dependencias y desarrollo

El frontend dispone de `npx expo install --check`, `npx expo-doctor`, `npx tsc --noEmit` y `npm run lint`. Estas comprobaciones no sustituyen a una revisión de seguridad.

Inspeccionar `npm audit` y priorizar los avisos de riesgo elevado antes de distribuir una versión. Las salidas previas de npm informaron de vulnerabilidades; no constan aún resultados que demuestren que hayan desaparecido. No ejecutar `npm audit fix --force` de forma automática si pudiera romper la compatibilidad con Expo.

La configuración final de autenticación JWT, permisos, protección de evidencias, limitación de peticiones y gestión de claves sigue pendiente de implementación y verificación.
