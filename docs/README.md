# Documentación de UrbanPulse

La documentación separa **funcionalidades implementadas**, **decisiones de diseño** y **validaciones ejecutadas**. El backend es un **monolito modular**: Autenticación, Usuarios, Invitaciones, Departamentos, Categorías, el núcleo de Incidencias, Evidencias, Mapas, Duplicados y la primera fase de Tramitación están implementados. Las fases posteriores de Tramitación y las integraciones avanzadas siguen siendo diseño.

| Documento                                     | Propósito                                                                                                                                                                             |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Arquitectura](architecture.md)               | Contenedores C4, arquitectura modular `auth`/`identity`/`invitations`/`organization`/`categories`/`incidents`/`processing`/`attachment`/`maps`/`duplicates`/`audit`/`shared`, dependencias y estado real. |
| [Desarrollo](development.md)                  | Desarrollo con Compose, PostgreSQL local, Mailpit, Object Storage de desarrollo, Flyway y CI.                                                                                         |
| [Despliegue](deployment.md)                   | Entornos Neon, preparación de producción y límites acreditados.                                                                                                                       |
| [Diseño de autenticación](auth-design.md)     | JWT, identidad, correo, controles de abuso, limpieza y decisiones de seguridad.                                                                                                       |
| [Modelo de dominio](domain-model.md)          | Entidades y reglas de negocio; V1 consolidada implementada frente al modelo objetivo y sus funciones futuras.                                                                         |
| [Diagramas](diagrams/README.md)               | Inventario de fuentes `.drawio` actuales y ocho exportaciones SVG manuales de C4, ER01–ER05 y clases.                                                                                 |
| [Contrato dominio–API](api/domain-openapi.md) | Alineación de modelo lógico, rutas/DTO y contrato vigente OpenAPI `0.8.0`.                                                                                                            |
| [Diseño de API](api/README.md)                | Fuentes YAML modulares, regeneración Redocly y tags implementados frente a los todavía diseñados.                                                                                     |
| [ADR](adr/README.md)                          | Justificación de las decisiones, incluida [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md), alcance y asuntos pendientes.                                                 |

La [CI](../.github/workflows/ci.yml) verifica backend, frontend, OpenAPI y Compose sin credenciales remotas. PostgreSQL 18 local es la única base de desarrollo; Neon PostgreSQL se reserva a producción y Neon Object Storage se utiliza en ambos entornos; [ADR-0016](adr/0016-ci-postgresql-local.md) documenta esta evolución de la decisión de Neon.

## Estado actual

Autenticación, Usuarios, Invitaciones, Departamentos, Categorías, Incidencias, Evidencias, Mapas, Duplicados y Tramitación fase 1 están implementados. OpenAPI `0.8.0` conserva `/api/v1`. V1 consolida dieciséis tablas funcionales, cinco roles, restricciones de Tramitación, Duplicados y funciones/índices de Mapas; sin usuarios ni catálogos precargados.

El [informe de integración](integration-reconciliation.md#validación-de-esta-integración) reúne los resultados actuales y la guía de desarrollo conserva los procedimientos e historia. Los catálogos requieren correo verificado para consultar y ADMIN global para escribir. Invitaciones conserva la concesión transaccional de roles y pertenencias, con auditoría propia.

Incidencias implementa registro, listados, detalle, corrección e historial con V1. Revisión, respuestas y prioridad manual están implementadas en processing; derivación, equipos y auditoría administrativa completa RF18 siguen pendientes. Véase el [roadmap técnico de Incidencias](incidents-implementation-roadmap.md). La conectividad histórica con Neon no acredita el despliegue de V1. Las fuentes `.drawio` distinguen V1 consolidada y diseño futuro; las ocho exportaciones históricas permanecen pendientes de regeneración manual; la [validación de diagramas](diagrams/README.md#criterios-de-sincronización) distingue integridad XML de las fuentes actuales e inspección visual histórica de las siete páginas.

**Evidencias (11/11/2026):** cinco rutas anidadas, JPEG/PNG, 10 MiB, máximo cinco activas por incidencia, autorización del informante u OPERATOR/ADMIN y borrado lógico sin purgado físico. [ADR-0015](adr/0015-almacenamiento-evidencias.md) y [contrato HTTP](api/README.md#evidencias-implementadas).

La primera fase de Tramitación está implementada: [reglas, SQL y límites](incidents-implementation-roadmap.md#tramitación-primera-fase-15112026), [diez operaciones REST](api/README.md#api-de-tramitación-implementada-080) y [verificación local](development.md). Las ocho exportaciones SVG, incluida ER05, proceden de la regeneración anterior del usuario y deben exportarse otra vez tras esta integración; véase [inventario](diagrams/README.md).

Backend Mapas y Duplicados implementados en esta integración sobre el contrato 0.8.0; RF08 sigue parcial por visor/capas y RF16 por semántica textual pendientes. No hay UI de ambos módulos, desvinculación ni prohibición de ciclos arbitrarios. [Matriz e informe de integración](integration-reconciliation.md).
