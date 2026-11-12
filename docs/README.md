# Documentación de UrbanPulse

La documentación separa **funcionalidades implementadas**, **decisiones de diseño** y **validaciones ejecutadas**. El backend es un **monolito modular**: Autenticación, Usuarios, Invitaciones, Departamentos, Categorías, el núcleo de Incidencias y Evidencias están implementados. Tramitación e integraciones avanzadas siguen siendo diseño.

| Documento                                     | Propósito                                                                                                                                                   |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Arquitectura](architecture.md)               | Contenedores C4, arquitectura modular `auth`/`identity`/`invitations`/`organization`/`categories`/`incidents`/`audit`/`shared`, dependencias y estado real. |
| [Desarrollo](development.md)                  | Árbol actualizado, secretos de entorno, Maven/Testcontainers, evidencia histórica HTTP y comandos Redocly.                                                  |
| [Despliegue](deployment.md)                   | Entornos Neon, preparación de producción y límites acreditados.                                                                                             |
| [Diseño de autenticación](auth-design.md)     | JWT, identidad, correo, controles de abuso, limpieza y decisiones de seguridad.                                                                             |
| [Modelo de dominio](domain-model.md)          | Entidades y reglas de negocio; V1 consolidada implementada frente al modelo objetivo y sus funciones futuras.                                               |
| [Diagramas](diagrams/README.md)               | Inventario de fuentes `.drawio` actuales y exportaciones SVG históricas de C4, ER y clases.                                                                 |
| [Contrato dominio–API](api/domain-openapi.md) | Alineación de modelo lógico, rutas/DTO y contrato vigente OpenAPI `0.7.0`.                                                                                  |
| [Diseño de API](api/README.md)                | Fuentes YAML modulares, regeneración Redocly y tags implementados frente a los todavía diseñados.                                                           |
| [ADR](adr/README.md)                          | Justificación de las decisiones, incluida [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md), alcance y asuntos pendientes.                       |

## Estado actual

Autenticación, Usuarios, Invitaciones, Departamentos, Categorías, Incidencias y Evidencias están implementados. OpenAPI `0.7.0` conserva `/api/v1`. La única V1 crea catorce tablas funcionales con sus restricciones finales y cinco roles, sin departamentos ni categorías precargados.

La [guía de desarrollo](development.md#verificación-del-backend) reúne las pruebas reproducibles. Los catálogos requieren correo verificado para consultar y ADMIN global para escribir. Invitaciones conserva la concesión transaccional de roles y pertenencias, con auditoría propia.

Incidencias implementa registro, listados, detalle, corrección e historial con V1. Tramitación, equipos y auditoría administrativa completa RF18 siguen pendientes. Véase el [roadmap técnico de Incidencias](incidents-implementation-roadmap.md). La conectividad histórica con Neon no acredita el despliegue de V1. Las fuentes `.drawio` distinguen V1 consolidada y diseño futuro; las exportaciones históricas se describen en el inventario; la [validación de diagramas](diagrams/README.md#criterios-de-sincronización) distingue integridad XML de las fuentes actuales e inspección visual histórica de las siete páginas.

**Evidencias (11/11/2026):** cinco rutas anidadas, JPEG/PNG, 10 MiB, máximo cinco activas por incidencia, autorización del informante u OPERATOR/ADMIN y borrado lógico sin purgado físico. [ADR-0015](adr/0015-almacenamiento-evidencias.md) y [contrato HTTP](api/README.md#evidencias-implementadas).
