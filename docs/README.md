# Documentación de UrbanPulse

Este directorio contiene documentación **viva**, separando las decisiones de diseño acordadas de la funcionalidad realmente implementada.

| Documento                                     | Contenido                                                                                                                                                                              |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Diseño de autenticación](auth-design.md)     | **Implementación del tag Autenticación**: registro, login, cambio de contraseña, roles, JWT, pruebas y tareas futuras claramente diferenciadas.                                        |
| [Modelo de dominio v1](domain-model.md)       | Entidades, roles, ciclo de vida, asignaciones, limitaciones y revisión obligatoria por tag antes de implementar.                                                                       |
| [Diagramas](diagrams/README.md)               | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones.                                                    |
| [Arquitectura](architecture.md)               | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento.                                                                                                       |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; implementados Autenticación y Department dentro de Catálogos. Los demás recursos siguen en diseño y se revisan por tag.                          |
| [Diseño de API](api/README.md)                | OpenAPI `0.4.0`: implementados el tag Autenticación y las operaciones de Department del tag Catálogos; el resto del contrato permanece en diseño y se revisa incrementalmente por tag. |
| [ADR](adr/README.md)                          | Decisiones aceptadas, propuestas y asuntos pendientes. En autenticación se revisan los ADR-0005, 0007, 0009 y 0011.                                                                    |
| [Desarrollo](development.md)                  | Entorno y comandos de instalación, ejecución y verificaciones comprobadas.                                                                                                             |
| [Despliegue](deployment.md)                   | Objetivo de contenedores independientes y despliegue futuro.                                                                                                                           |

## Estado actual del proyecto (31/10/2026)

El backend cuenta con dos funcionalidades implementadas y verificadas localmente:

- **Autenticación:** registro, inicio de sesión, cambio de contraseña, verificación de correo, JWT y gestión de permisos.
- **Departamentos (tag Catálogos):** listado, consulta, creación y actualización parcial de departamentos, incluida la desactivación lógica. Las operaciones de escritura requieren el rol global `ADMIN`.

El contrato OpenAPI se encuentra en la versión `0.4.0`. Se han actualizado las dos vistas del modelo de dominio y la documentación correspondiente a Department. El resto de las operaciones de Catálogos y los endpoints de incidencias siguen pendientes de implementación.

La última verificación completa documentada finalizó con **80 pruebas superadas**: 55 unitarias/MVC y 25 de integración PostgreSQL mediante Testcontainers. Las migraciones Flyway V1 y V2 se verificaron en PostgreSQL efímero, pero no consta su aplicación en Neon `development` ni un despliegue de producción.

Los resultados y límites de esta implementación se detallan en el [informe de cierre de departamentos](departments-finalization.md).

**Nota sobre el historial:** Los apartados fechados que aparecen a continuación documentan etapas anteriores del proyecto. Algunas referencias a funcionalidades pendientes, versiones antiguas de OpenAPI y cifras de pruebas corresponden a esas fechas. Para conocer el estado vigente de la implementación, debe consultarse la sección «Estado actual del proyecto (31/10/2026)» y el informe de cierre de departamentos.

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde backend de desarrollo. **Existen cinco endpoints de autenticación, las entidades JPA de identidad y la migración Flyway V1 ampliada probada en PostgreSQL efímero**. Siguen pendientes los endpoints del dominio de incidencias, otras entidades, flujo de fotografías, despliegue y CI/CD.

**Modelo compartido (18/10/2026):** las dos vistas de dominio y OpenAPI `0.2.0` constituyeron una base inicial revisable. Cada equipo revisa conjuntamente ambas vistas y el contrato del tag antes de implementar. Una clase dibujada o una ruta documentada no demuestra que ya exista código. Ver [CONTRIBUTING](../CONTRIBUTING.md).

**Revisión adicional de autenticación (25/10/2026):** se documenta una propuesta de contrato `0.3.0` que incorpora registro con JWT, cambio de contraseña con invalidación mediante `tokenVersion`, lectura de permisos vigentes por petición, emailVerified, rol base `CITIZEN`, UUIDv7 desde Hibernate y rate limiting local. Se distingue en el [documento específico](auth-design.md) entre **funcionalidad pendiente de esta rama** y **mejoras futuras**. **La autenticación ya se ha implementado localmente**, con tests unitarios/MVC e integración aislada. Las demás operaciones de negocio mantienen su carácter de diseño.

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. La autenticación dispone de suite propia de pruebas con PostgreSQL efímero. La evidencia anterior del 25/10/2026 fue de 23 pruebas; la última ampliación del correo cuenta con 55 pruebas comunicadas por Codex (35 unitarias/MVC y 20 PostgreSQL efímero). Las pruebas de conectividad de infraestructura siguen siendo distintas.

**Actualización de autenticación (28/10/2026):** el backend incluye cinco POST de auth, confirmación/reenvío por código HMAC-SHA256 y servicio SMTP configurable (Mailpit solo en desarrollo), además de limpieza de cuentas sin verificar tras 24 h. Documentación funcional en [auth-design.md](auth-design.md); contratos fuente en [api/openapi/base-openapi.yaml](api/openapi/base-openapi.yaml). Los diagramas de identidad y C4 requieren actualizar sus fuentes/editables y exportar los SVG correspondientes para reflejar la nueva entidad `EmailVerification` y Mailpit.
