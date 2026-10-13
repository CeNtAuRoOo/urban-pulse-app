# Documentación de UrbanPulse

Este directorio recoge la documentación **viva** del proyecto. Distingue los componentes ya inicializados y probados de las funcionalidades y despliegues todavía previstos.

| Documento                       | Contenido                                                                                     |
| ------------------------------- | --------------------------------------------------------------------------------------------- |
| [Arquitectura](architecture.md) | Arquitectura objetivo, C4, estructura real inicial de backend/frontend y decisiones técnicas. |
| [Desarrollo](development.md)    | Instalación, variables, comandos reales, verificaciones y problemas frecuentes.               |
| [Despliegue](deployment.md)     | Imágenes independientes, exportación web, distribución nativa y despliegue futuro.            |
| [API](api/README.md)            | Contrato OpenAPI futuro; sin endpoints implementados.                                         |
| [ADR](adr/README.md)            | Decisiones aceptadas, propuestas y pendientes.                                                |
| [Diagramas](diagrams/README.md) | Diagramas C4 disponibles y otros diagramas de diseño pendientes.                              |

**Estado a 13/10/2026:** frontend universal inicializado; su desarrollo web, lint, TypeScript, exportación y Docker Compose (solo frontend) se han comprobado. Backend estructurado, pero **sin API funcional ni integración con Neon probadas**. No existen despliegues de producción ni workflows de CI/CD ejecutables todavía.

El [README principal](../README.md) ofrece los pasos mínimos de puesta en marcha.
