# Documentación de UrbanPulse

La documentación del proyecto separa **funcionalidades implementadas**, **decisiones de diseño** y **validaciones realmente ejecutadas**. El backend actual (rama `feature/invitaciones-backend`) es un **monolito modular**. Autenticación, Invitaciones y Departamentos están implementados; la API de incidencias y el resto de tags se mantienen como propuestas incrementales.

| Documento | Propósito |
| --- | --- |
| [Arquitectura](architecture.md) | Contenedores C4, arquitectura modular `auth`/`identity`/`invitations`/`organization`/`audit`/`shared`, dependencias y estado real. |
| [Desarrollo](development.md) | Árbol actualizado, secretos de entorno, Maven/Testcontainers, evidencia histórica HTTP y comandos Redocly. |
| [Despliegue](deployment.md) | Entornos Neon, preparación de producción y límites acreditados. |
| [Diseño de autenticación](auth-design.md) | JWT, identidad, correo, controles de abuso, limpieza y decisiones de seguridad. |
| [Departamentos](departments-finalization.md) | Estado integrado, contrato y evidencias históricas del cierre de main. |
| [Modelo de dominio](domain-model.md) | Entidades y reglas de negocio; V1 consolidada implementadas frente a modelo futuro de incidencias. |
| [Diagramas](diagrams/README.md) | Inventario de `.drawio` y SVG conservados para exportación manual de C4, ER y clases. |
| [Contrato dominio–API](api/domain-openapi.md) | Alineación de modelo lógico, rutas/DTO y contrato vigente OpenAPI `0.5.0`. |
| [Diseño de API](api/README.md) | Fuentes YAML modulares, regeneración Redocly y tags implementados frente a los todavía diseñados. |
| [ADR](adr/README.md) | Justificación de las decisiones, incluida [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md), alcance y asuntos pendientes. |

## Estado y cronología

- **02/11/2026:** integración de Departamentos e Invitaciones en V1 para bases vacías, sin semillas departamentales; resultados actuales en [Desarrollo](development.md).
- **30–31/10/2026, rama de Departamentos:** 80 pruebas históricas (55 unitarias/MVC + 25 IT), documentación y revisión visual descritas en [su informe](departments-finalization.md).

- **13–15/10/2026:** frontend Expo Web, arranque Compose y conectividad JDBC/S3 con Neon `development` comprobados; esto ocurrió antes de la migración V1 consolidada actual.
- **25–28/10/2026:** autenticación JWT y verificación de correo implementadas; evidencias anteriores de 23 y 55 pruebas.
- **Informe fechado el 30/10/2026:** se comunicaron 90 pruebas sobre PostgreSQL efímero, un recorrido HTTP aislado con Mailpit y contrato OpenAPI `0.5.0` validado (dos avisos no bloqueantes). La fecha del informe no determina por sí sola el orden real de ejecución de los commits.
- **Orden de commits hasta el 31/10/2026:** reorganización Java por módulos (`c393418`) **antes** de Invitaciones (`81d8d85`), pruebas aisladas (`0bb0ca3`) y documentación (`56fc44d`), con diagramas `.drawio`/SVG actualizados. Se registraron **90 pruebas superadas** y un recorrido HTTP satisfactorio; los archivos originales del runner HTTP no aparecen en el volcado local proporcionado.

No consta migración de V1 consolidada en Neon `development`, recepción de correo por un proveedor externo ni despliegue en producción. Una ruta existente en OpenAPI no implica endpoint ejecutable si pertenece a otro tag; una clase dibujada no implica entidad persistida.

Los `.drawio` incorporan el modelo integrado; los SVG se conservan por instrucción expresa y se exportarán manualmente. Siguen pendientes diagramas nuevos de despliegue y flujos futuros.

El [README principal](../README.md) resume instalación y ejecución. La [guía de contribución](../CONTRIBUTING.md) establece cómo mantener sincronizados contrato, código, dominio, diagramas, pruebas y ADR sin incorporar secretos.
