# Documentación de UrbanPulse

La documentación separa **funcionalidades implementadas**, **decisiones de diseño** y **validaciones ejecutadas**. El backend es un **monolito modular**: Autenticación, Usuarios, Invitaciones, Departamentos, Categorías y el núcleo de Incidencias están implementados. Tramitación e integraciones avanzadas siguen siendo diseño.

| Documento                                     | Propósito                                                                                                                                                   |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Arquitectura](architecture.md)               | Contenedores C4, arquitectura modular `auth`/`identity`/`invitations`/`organization`/`categories`/`incidents`/`audit`/`shared`, dependencias y estado real. |
| [Desarrollo](development.md)                  | Árbol actualizado, secretos de entorno, Maven/Testcontainers, evidencia histórica HTTP y comandos Redocly.                                                  |
| [Despliegue](deployment.md)                   | Entornos Neon, preparación de producción y límites acreditados.                                                                                             |
| [Diseño de autenticación](auth-design.md)     | JWT, identidad, correo, controles de abuso, limpieza y decisiones de seguridad.                                                                             |
| [Modelo de dominio](domain-model.md)          | Entidades y reglas de negocio; V1/V2/V3 implementadas frente al modelo objetivo y sus funciones futuras.                                                    |
| [Diagramas](diagrams/README.md)               | Inventario de fuentes `.drawio` actuales y exportaciones SVG históricas de C4, ER y clases.                                                                 |
| [Contrato dominio–API](api/domain-openapi.md) | Alineación de modelo lógico, rutas/DTO y contrato vigente OpenAPI `0.6.0`.                                                                                  |
| [Diseño de API](api/README.md)                | Fuentes YAML modulares, regeneración Redocly y tags implementados frente a los todavía diseñados.                                                           |
| [ADR](adr/README.md)                          | Justificación de las decisiones, incluida [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md), alcance y asuntos pendientes.                       |

## Estado actual

Autenticación, Usuarios, Invitaciones, Departamentos, Categorías e Incidencias están implementados. OpenAPI `0.6.0` conserva `/api/v1`. V1 y V2 crean trece tablas funcionales; V3 refuerza restricciones, conservando las tablas y los cinco roles, sin departamentos ni categorías precargados.

La [guía de desarrollo](development.md#verificación-del-backend) reúne las pruebas reproducibles. Los catálogos requieren correo verificado para consultar y ADMIN global para escribir. Invitaciones conserva la concesión transaccional de roles y pertenencias, con auditoría propia.

Incidencias implementa registro, listados, detalle, corrección e historial con V2. Tramitación, equipos, fotografías y auditoría administrativa completa RF18 siguen pendientes. Véase el [roadmap técnico de Incidencias](incidents-implementation-roadmap.md). La conectividad histórica con Neon no acredita el despliegue de V1. Las fuentes `.drawio` distinguen núcleo V2 y diseño futuro; las exportaciones históricas se describen en el inventario; la [validación de diagramas](diagrams/README.md#criterios-de-sincronización) distingue integridad XML de las fuentes actuales e inspección visual histórica de las siete páginas.


**Implementación de evidencias (04/11/2026):** el tag **Evidencias** pasa de diseño a implementado. El contrato `incidents/{incidentId}/attachments` y `incidents/{incidentId}/attachments/{attachmentId}` (incluido `/content`) queda cubierto por `AttachmentService` y los endpoints asociados.