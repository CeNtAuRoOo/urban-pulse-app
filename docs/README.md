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
