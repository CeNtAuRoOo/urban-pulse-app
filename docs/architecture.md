# Arquitectura de UrbanPulse

El cliente Expo/React Native Web consume la API REST de un monolito modular Java 25 y Spring Boot. El navegador conoce la URL pública de la API. Solo el backend accede a PostgreSQL, al bucket privado compatible con S3 y al transporte SMTP. Las imágenes C4 sitúan estos procesos y sus límites de confianza. [Diagramas](diagrams/README.md).

| Módulos                                           | Responsabilidad y relación                                                                                                                                                                                            |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `auth`, `identity`, `invitations`, `organization` | La cuenta conserva la identidad al incorporar permisos. La autorización reúne roles globales actuales, pertenencias departamentales y reglas del recurso. [Diseño de identidad](design-docs/DD-001-identidad.md)      |
| `categories`, `incidents`, `processing`, `audit`  | Incidencias mantiene titular, localización, estado y versión. Tramitación registra decisiones y cambios de estado con su auditoría en la misma transacción. [Diseño del expediente](design-docs/DD-002-expediente.md) |
| `attachment`                                      | Coordina autorización, saneamiento, metadatos SQL y objetos privados. [Diseño de evidencias](design-docs/DD-003-evidencias.md)                                                                                        |
| `duplicates`                                      | Calcula candidatos sin modificar los reportes. La confirmación por personal global guarda un vínculo simétrico y audita al actor.                                                                                     |
| `maps`, `urban_context`                           | Mapas proyecta incidencias con autorización y privacidad mediante SQL nativo. El catálogo espacial emplea PostGIS y JTS. [Diseño territorial](design-docs/DD-004-territorio.md)                                       |
| `shared`                                          | Configuración de seguridad, errores saneados, correo, almacenamiento, paginación y cuotas en memoria.                                                                                                                 |

La [procedencia del contorno provincial, su licencia y la proyección local](design-docs/DD-004-territorio.md#procedencia-del-contorno-provincial) se conservan junto al diseño que utiliza esas constantes.

## Persistencia y operación

Flyway instala V1 y Hibernate valida los mapeos. Los datos sintéticos se incorporan después de Flyway, exclusivamente desde el Compose de desarrollo. En producción se prevén Neon PostgreSQL, almacenamiento de objetos y SMTP propios del entorno. El arranque local, las pruebas con Testcontainers y una conexión a un proveedor son comprobaciones distintas. [Desarrollo](development.md) · [Despliegue](deployment.md).

El modelo lógico contiene capacidades futuras. Las operaciones disponibles y sus permisos se consultan en el [contrato](api/README.md). Las dependencias entre módulos y sus reglas de integridad se documentan en el [modelo de dominio](domain-model.md).
