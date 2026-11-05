# Documentación de UrbanPulse

La documentación del proyecto separa **funcionalidades implementadas**, **decisiones de diseño** y **validaciones realmente ejecutadas**. El backend actual (rama `feature/invitaciones-backend`) es un **monolito modular**. Autenticación, Usuarios, Invitaciones, Departamentos y Categorías están implementados; la API de incidencias y el resto de tags se mantienen como propuestas incrementales.

| Documento                                     | Propósito                                                                                                                                       |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| [Arquitectura](architecture.md)               | Contenedores C4, arquitectura modular `auth`/`identity`/`invitations`/`organization`/`categories`/`audit`/`shared`, dependencias y estado real. |
| [Desarrollo](development.md)                  | Árbol actualizado, secretos de entorno, Maven/Testcontainers, evidencia histórica HTTP y comandos Redocly.                                      |
| [Despliegue](deployment.md)                   | Entornos Neon, preparación de producción y límites acreditados.                                                                                 |
| [Diseño de autenticación](auth-design.md)     | JWT, identidad, correo, controles de abuso, limpieza y decisiones de seguridad.                                                                 |
| [Modelo de dominio](domain-model.md)          | Entidades y reglas de negocio; V1 consolidada implementada frente a modelo futuro de incidencias.                                               |
| [Diagramas](diagrams/README.md)               | Inventario de fuentes `.drawio` y SVG regenerados con draw.io Desktop de C4, ER y clases.                                                       |
| [Contrato dominio–API](api/domain-openapi.md) | Alineación de modelo lógico, rutas/DTO y contrato vigente OpenAPI `0.5.0`.                                                                      |
| [Diseño de API](api/README.md)                | Fuentes YAML modulares, regeneración Redocly y tags implementados frente a los todavía diseñados.                                               |
| [ADR](adr/README.md)                          | Justificación de las decisiones, incluida [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md), alcance y asuntos pendientes.           |

## Estado actual

Autenticación, Usuarios, Invitaciones, Departamentos y Categorías están implementados en el backend modular. El contrato OpenAPI `0.5.0` conserva `/api/v1`. La única migración `V1__initial_schema.sql` crea nueve tablas funcionales y cinco roles, sin departamentos ni categorías precargados.

La [guía de desarrollo](development.md#verificación-del-backend) reúne las pruebas reproducibles. Los catálogos requieren correo verificado para consultar y ADMIN global para escribir. Invitaciones conserva la concesión transaccional de roles y pertenencias, con auditoría propia.

Incidencias, equipos, fotografías y auditoría administrativa completa RF18 siguen pendientes. La conectividad histórica con Neon no acredita el despliegue de V1. Las fuentes `.drawio` y sus SVG regenerados con draw.io Desktop incluyen los catálogos vigentes; la [validación de diagramas](diagrams/README.md#criterios-de-sincronización) incluye integridad XML e inspección visual de las siete páginas.
| [Diseño de autenticación](auth-design.md) | **Implementación del tag Autenticación**: registro, login, cambio de contraseña, roles, JWT, pruebas y tareas futuras claramente diferenciadas. |
| [Modelo de dominio v1](domain-model.md) | Entidades, roles, ciclo de vida, asignaciones, limitaciones y revisión obligatoria por tag antes de implementar. |
| [Diagramas](diagrams/README.md) | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones. |
| [Arquitectura](architecture.md) | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento. |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; tag Autenticación implementado, resto revisable por tag. |
| [Diseño de API](api/README.md) | OpenAPI `0.3.0` (tag Autenticación implementado; restante contrato en diseño); evolución conjunta por tag. |
| [ADR](adr/README.md) | Decisiones aceptadas, propuestas y asuntos pendientes. En autenticación se revisan los ADR-0005, 0007, 0009 y 0011. |
| [Desarrollo](development.md) | Entorno y comandos de instalación, ejecución y verificaciones comprobadas. |
| [Despliegue](deployment.md) | Objetivo de contenedores independientes y despliegue futuro. |

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde backend de desarrollo. **Existen cinco endpoints de autenticación, las entidades JPA de identidad y la migración Flyway V1 ampliada probada en PostgreSQL efímero**. Siguen pendientes los endpoints del dominio de incidencias, otras entidades, flujo de fotografías, despliegue y CI/CD.

**Modelo compartido (18/10/2026):** las dos vistas de dominio y OpenAPI `0.2.0` constituyeron una base inicial revisable. Cada equipo revisa conjuntamente ambas vistas y el contrato del tag antes de implementar. Una clase dibujada o una ruta documentada no demuestra que ya exista código. Ver [CONTRIBUTING](../CONTRIBUTING.md).

**Revisión adicional de autenticación (25/10/2026):** se documenta una propuesta de contrato `0.3.0` que incorpora registro con JWT, cambio de contraseña con invalidación mediante `tokenVersion`, lectura de permisos vigentes por petición, emailVerified, rol base `CITIZEN`, UUIDv7 desde Hibernate y rate limiting local. Se distingue en el [documento específico](auth-design.md) entre **funcionalidad pendiente de esta rama** y **mejoras futuras**. **La autenticación ya se ha implementado localmente**, con tests unitarios/MVC e integración aislada. Las demás operaciones de negocio mantienen su carácter de diseño.

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. La autenticación dispone de suite propia de pruebas con PostgreSQL efímero. La evidencia anterior del 25/10/2026 fue de 23 pruebas; la última ampliación del correo cuenta con 55 pruebas comunicadas por Codex (35 unitarias/MVC y 20 PostgreSQL efímero). Las pruebas de conectividad de infraestructura siguen siendo distintas.

**Actualización de autenticación (28/10/2026):** el backend incluye cinco POST de auth, confirmación/reenvío por código HMAC-SHA256 y servicio SMTP configurable (Mailpit solo en desarrollo), además de limpieza de cuentas sin verificar tras 24 h. Documentación funcional en [auth-design.md](auth-design.md); contratos fuente en [api/openapi/base-openapi.yaml](api/openapi/base-openapi.yaml). Los diagramas de identidad y C4 requieren actualizar sus fuentes/editables y exportar los SVG correspondientes para reflejar la nueva entidad `EmailVerification` y Mailpit.

**Implementación de evidencias (04/11/2026):** el tag **Evidencias** pasa de diseño a implementado. El contrato `incidents/{incidentId}/attachments` y `incidents/{incidentId}/attachments/{attachmentId}` (incluido `/content`) queda cubierto por `AttachmentService` y los endpoints asociados.
