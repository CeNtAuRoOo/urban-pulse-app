# Despliegue

La imagen productiva del backend se construye con [Dockerfile](../backend/Dockerfile). El cliente web se exporta como SPA y se sirve con Nginx. La API y su origen CORS deben configurarse para las URL públicas de ese entorno. El [Compose](../compose.yaml), Mailpit y el dataset sintético pertenecen exclusivamente al desarrollo.

## PostgreSQL y almacenamiento

Preparar PostgreSQL 18 con PostGIS disponible en `public` y permisos para crear el esquema `cartografia` y la extensión, o preinstalarla con el administrador. Flyway aplica V1 desde una BD vacía y Hibernate valida después. Revisar el historial y los respaldos antes de conectar una base con datos. El reordenamiento de V1 cambia el checksum. Un historial antiguo con datos que conservar requiere una transición específica. [ADR de migraciones](adr/ADR-006-flyway.md).

Configurar las variables de la [plantilla del backend](../backend/.env.example) mediante secretos del entorno de despliegue. Separar credenciales y bucket privados de desarrollo y producción. El cliente solicita evidencias a través de la API autenticada. La política de compensación y sus límites se describen en el [diseño de evidencias](design-docs/DD-003-evidencias.md).

## Identidad, correo y operación

Configurar emisor JWT, tres secretos Base64 independientes y orígenes CORS explícitos. Usar HTTPS y el transporte SMTP productivo con autenticación, remitente y TLS adecuados. La captura en Mailpit verifica el transporte local. El envío de correos de verificación e invitaciones se hace después del commit. Un fallo posterior puede requerir reemisión. [Diseño de identidad](design-docs/DD-001-identidad.md).

CI utiliza dobles S3 y PostgreSQL efímero. La preparación productiva incluye comprobar imagen web, migraciones, permisos efectivos, entrega SMTP, controles de acceso al bucket y restauración. El estado de CD, observabilidad y pruebas externas figura en el [registro operativo](pending.md#operación).
