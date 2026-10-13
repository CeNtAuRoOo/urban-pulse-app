# Diagramas de UrbanPulse

Los diagramas de contexto y contenedores **sí existen** y están versionados como SVG. Ambos contienen información editable de diagrams.net/draw.io embebida: es posible abrirlos desde draw.io para modificarlos. **Todavía no se han añadido archivos `.drawio` independientes**; si se generan, conservar tanto los originales editables como una exportación visible en GitHub.

| Recurso                                | Estado                                | Finalidad                                                        |
| -------------------------------------- | ------------------------------------- | ---------------------------------------------------------------- |
| [c4-context.svg](c4-context.svg)       | Disponible                            | C4 nivel 1: personas, UrbanPulse y sistemas externos             |
| [c4-containers.svg](c4-containers.svg) | Disponible                            | C4 nivel 2: frontend web/móvil, backend, PostgreSQL y evidencias |
| `deployment.drawio` y su exportación   | Pendiente                             | Despliegue cloud real cuando se elijan proveedores               |
| `er-database.drawio`                   | Pendiente de incorporación/adaptación | Esquema relacional PostgreSQL                                    |
| `incident-lifecycle.drawio`            | Pendiente                             | Máquina de estados de incidencia                                 |
| `incident-flow.drawio`                 | Opcional                              | Secuencia de registro y evidencias                               |
| Diagramas de clases/componentes        | Opcionales                            | Diseño detallado backend y frontend                              |

**Revisión pendiente:** la descripción del almacén de evidencias en `c4-containers.svg` aún menciona vídeos/documentos; la primera fase del proyecto solo contempla fotografías. El proveedor Neon Object Storage sigue como propuesta hasta su validación técnica.

Los C4 principales se muestran en [architecture.md](../architecture.md). Los diagramas ER y de clases deben enlazarse cuando estén disponibles, sin inventar archivos todavía inexistentes.
