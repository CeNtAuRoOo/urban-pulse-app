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

**Estado a 15/10/2026:** frontend web validado; frontend y backend arrancan juntos con Docker Compose. La integración del backend con Neon PostgreSQL 18 y el acceso al bucket privado de Object Storage se han comprobado mediante `NeonConnectivityIT` en `development`. **Siguen sin existir endpoints de negocio, migraciones de esquema, pruebas de subida/descarga completa, despliegues de producción ni CI/CD ejecutable**.

El [README principal](../README.md) ofrece los pasos mínimos de puesta en marcha.
