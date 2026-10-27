# Documentación de UrbanPulse

Este directorio contiene documentación **viva**, separando las decisiones de diseño acordadas de la funcionalidad realmente implementada.

| Documento                                     | Contenido                                                                                                                                       |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| [Diseño de autenticación](auth-design.md)     | **Implementación del tag Autenticación**: registro, login, cambio de contraseña, roles, JWT, pruebas y tareas futuras claramente diferenciadas. |
| [Modelo de dominio v1](domain-model.md)       | Entidades, roles, ciclo de vida, asignaciones, limitaciones y revisión obligatoria por tag antes de implementar.                                |
| [Diagramas](diagrams/README.md)               | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones.             |
| [Arquitectura](architecture.md)               | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento.                                                                |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; tag Autenticación implementado, resto revisable por tag.                                                  |
| [Diseño de API](api/README.md)                | OpenAPI `0.3.0` (tag Autenticación implementado; restante contrato en diseño); evolución conjunta por tag.                                      |
| [ADR](adr/README.md)                          | Decisiones aceptadas, propuestas y asuntos pendientes. En autenticación se revisan los ADR-0005, 0007, 0009 y 0011.                             |
| [Desarrollo](development.md)                  | Entorno y comandos de instalación, ejecución y verificaciones comprobadas.                                                                      |
| [Despliegue](deployment.md)                   | Objetivo de contenedores independientes y despliegue futuro.                                                                                    |

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde backend de desarrollo. **Existen tres endpoints de autenticación, entidades JPA de identidad y la migración Flyway V1 probada en PostgreSQL efímero**. Siguen pendientes los endpoints del dominio de incidencias, otras entidades, flujo de fotografías, despliegue y CI/CD.

**Modelo compartido (18/10/2026):** las dos vistas de dominio y OpenAPI `0.2.0` constituyeron una base inicial revisable. Cada equipo revisa conjuntamente ambas vistas y el contrato del tag antes de implementar. Una clase dibujada o una ruta documentada no demuestra que ya exista código. Ver [CONTRIBUTING](../CONTRIBUTING.md).

**Revisión adicional de autenticación (25/10/2026):** se documenta una propuesta de contrato `0.3.0` que incorpora registro con JWT, cambio de contraseña con invalidación mediante `tokenVersion`, lectura de permisos vigentes por petición, emailVerified, rol base `CITIZEN`, UUIDv7 desde Hibernate y rate limiting local. Se distingue en el [documento específico](auth-design.md) entre **funcionalidad pendiente de esta rama** y **mejoras futuras**. **La autenticación ya se ha implementado localmente**, con tests unitarios/MVC e integración aislada. Las demás operaciones de negocio mantienen su carácter de diseño.

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. La autenticación dispone de suite propia de pruebas con PostgreSQL efímero. La evidencia anterior del 25/10/2026 fue de 23 pruebas; los últimos cambios requieren nueva ejecución antes del commit. Las pruebas de conectividad de infraestructura siguen siendo distintas.
