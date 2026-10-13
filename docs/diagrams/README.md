# Diagramas

Se conservarán los originales **editables** de draw.io (`.drawio`) y, cuando se necesite visualizar directamente en GitHub, también una exportación `.svg` o `.png`. No incluir capturas sin el archivo fuente si es posible.

## Diagramas previstos

| Archivo sugerido                | Tipo y finalidad                                                                   | Estado                                                                  |
| ------------------------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `c4-context.drawio`             | **C4 nivel 1:** actores y relaciones externas                                      | Ya existe                                                               |
| `c4-containers.drawio`          | **C4 nivel 2:** web, móvil, API, PostgreSQL, almacenamiento y proveedores externos | Ya existe                                                               |
| `deployment.drawio`             | **C4 Deployment:** ubicación efectiva de contenedores y servicios cloud            | Pendiente: falta elegir hosting de web/backend                          |
| `incident-lifecycle.drawio`     | Máquina de estados para el ciclo de vida de una incidencia                         | Recomendado cuando se diseñe la lógica                                  |
| `incident-flow.drawio`          | Diagrama dinámico o secuencia: registro y gestión de una incidencia                | Opcional, útil si hay varias integraciones                              |
| `er-database.drawio`            | Modelo entidad-relación PostgreSQL                                                 | Ya existe un diagrama de relaciones; pendiente de adaptarlo             |
| `class-diagram-backend.drawio`  | UML de clases del backend                                                          | Opcional; junto a implementación                                        |
| `class-diagram-frontend.drawio` | UML/estructura del frontend                                                        | Opcional; React utiliza componentes y módulos, no necesariamente clases |

En [architecture.md](../architecture.md) dejaremos marcadores para los diagramas C4 principales. Los diagramas ER y de clases son documentación del **diseño detallado**, no reemplazan los niveles 1 y 2 de C4.
