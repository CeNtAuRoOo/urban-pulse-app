# Documentación de UrbanPulse

Este directorio contiene documentación **viva**, separando las decisiones de diseño acordadas de la funcionalidad realmente implementada.

| Documento                               | Contenido                                                                                                                           |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| [Modelo de dominio v1](domain-model.md) | Entidades, roles, ciclo de vida, asignaciones, limitaciones y **revisión obligatoria por tag** antes de implementar.                |
| [Diagramas](diagrams/README.md)         | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones. |
| [Arquitectura](architecture.md)         | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento; decisiones relevantes de dominio.                  |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; flujo de revisión conjunta, sin implementar aún. |
| [Diseño de API](api/README.md)          | OpenAPI `0.2.0` alineado con el dominio v1 como diseño base; evolución conjunta por tag.                                     |
| [ADR](adr/README.md)                    | Decisiones aceptadas, propuesta técnica y asuntos pendientes.                                                                       |
| [Desarrollo](development.md)            | Entorno y comandos de instalación, ejecución y verificaciones comprobadas.                                                          |
| [Despliegue](deployment.md)             | Objetivo de contenedores independientes y despliegue futuro.                                                                        |

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde el backend de desarrollo. **No existen endpoints de negocio, entidades JPA, migraciones del esquema del dominio, flujo real de fotografías, despliegue de producción ni CI/CD ejecutable.**

**Novedad de diseño (18/10/2026):** los diagramas `relacional.drawio` y `clases-simplificado.drawio` representan una **primera versión del dominio compartido, sincronizada con OpenAPI**, no un modelo completamente ratificado. El equipo **no implementará el conjunto de entidades de golpe**: la persona responsable de cada tag deberá revisar simultáneamente el OpenAPI y las dos vistas del dominio, completar los detalles que necesite y versionar los cambios como parte de su PR. Véase [modelo de dominio](domain-model.md) y [guía de contribución](../CONTRIBUTING.md).

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. La existencia de diagramas/ADR/DTO en el contrato no equivale a tener funcionalidad implementada.
