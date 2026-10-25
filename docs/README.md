# Documentación de UrbanPulse

Este directorio contiene documentación **viva**, separando las decisiones de diseño acordadas de la funcionalidad realmente implementada.

| Documento                                     | Contenido                                                                                                                                                                                                    |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [Diseño de autenticación](auth-design.md)     | **Revisión documental del tag Autenticación (25/10/2026)**: contrato, dominio mínimo, roles, JWT, cambio de contraseña, limitación de peticiones, tareas futuras y aceptación. **Pendiente de implementar.** |
| [Modelo de dominio v1](domain-model.md)       | Entidades, roles, ciclo de vida, asignaciones, limitaciones y revisión obligatoria por tag antes de implementar.                                                                                             |
| [Diagramas](diagrams/README.md)               | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones.                                                                          |
| [Arquitectura](architecture.md)               | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento.                                                                                                                             |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; flujo de revisión conjunta, sin implementación.                                                                                                                        |
| [Diseño de API](api/README.md)                | OpenAPI `0.3.0` (revisión del diseño de autenticación sobre dominio v1); evolución conjunta por tag.                                                                                                         |
| [ADR](adr/README.md)                          | Decisiones aceptadas, propuestas y asuntos pendientes. En autenticación se revisan los ADR-0005, 0007, 0009 y 0011.                                                                                          |
| [Desarrollo](development.md)                  | Entorno y comandos de instalación, ejecución y verificaciones comprobadas.                                                                                                                                   |
| [Despliegue](deployment.md)                   | Objetivo de contenedores independientes y despliegue futuro.                                                                                                                                                 |

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde backend de desarrollo. **No existen endpoints de negocio, entidades JPA, migraciones del esquema del dominio, flujo real de fotografías, despliegue de producción ni CI/CD ejecutable.**

**Modelo compartido (18/10/2026):** las dos vistas de dominio y OpenAPI `0.2.0` constituyeron una base inicial revisable. Cada equipo revisa conjuntamente ambas vistas y el contrato del tag antes de implementar. Una clase dibujada o una ruta documentada no demuestra que ya exista código. Ver [CONTRIBUTING](../CONTRIBUTING.md).

**Revisión adicional de autenticación (25/10/2026):** se documenta una propuesta de contrato `0.3.0` que incorpora registro con JWT, cambio de contraseña con invalidación mediante `tokenVersion`, lectura de permisos vigentes por petición, emailVerified, rol base `CITIZEN`, UUIDv7 desde Hibernate y rate limiting local. Se distingue en el [documento específico](auth-design.md) entre **funcionalidad pendiente de esta rama** y **mejoras futuras**. Este cambio documental **no implica ninguna implementación**. Los diagramas se actualizarán manualmente en el mismo PR, antes de dar por finalizada la revisión de diseño.

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. Las pruebas citadas en documentos anteriores son pruebas históricas de infraestructura, no pruebas del nuevo sistema de autenticación.
