# Diagramas de UrbanPulse

Este directorio contiene las **fuentes editables en diagrams.net / draw.io** y sus exportaciones para revisión en GitHub. Se distinguen los diagramas de arquitectura C4 del **modelo de dominio v1**, que es una base compartida sincronizada con OpenAPI `0.2.0`, todavía pendiente de validación exhaustiva e implementación por tag.

## Inventario

| Fuente editable                                            | Exportación para lectura                                                                                                                                                  | Propósito y estado                                                                                                                                                                   |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [`c4.drawio`](c4.drawio)                                   | [`c4-context.svg`](c4-context.svg) · [`c4-containers.svg`](c4-containers.svg)                                                                                             | Arquitectura C4, contexto y contenedores; diagramas ya disponibles. Se incorpora ahora la fuente `.drawio` independiente.                                                            |
| [`relacional.drawio`](relacional.drawio)                   | [`relacional-01.svg`](relacional-01.svg) · [`relacional-02.svg`](relacional-02.svg) · [`relacional-03.svg`](relacional-03.svg) · [`relacional-04.svg`](relacional-04.svg) | **Modelo relacional lógico v1** en cuatro páginas: incidencias, tramitación, organización, territorio/contexto. PK/FK, campos y cardinalidades, sin tipos SQL ni migración generada. |
| [`clases-simplificado.drawio`](clases-simplificado.drawio) | [`clases-simplificado.svg`](clases-simplificado.svg)                                                                                                                      | **Catálogo de clases Java propuestas v1**, una sola página: atributos, tipos, enums, embeddables y DTO auxiliares. Sin líneas UML intencionadamente: consultar relaciones en el ER.  |

> **Nota de nombres:** las cuatro exportaciones `relacional-01` a `relacional-04` se enlazan aquí como `.svg`, siguiendo el patrón de las demás exportaciones. Si en la copia local se exportaron en `.png`, hay que adaptar los enlaces a las **extensiones reales** antes del commit. En GitHub `main` aún no figuran las fuentes/exports nuevas hasta que se suba el PR.

## Vistas previas del modelo de dominio

### Relacional: página 1 — Incidencias

![Incidencias y trazabilidad](relacional-01.svg)

### Relacional: página 2 — Tramitación

![Tramitación municipal](relacional-02.svg)

### Relacional: página 3 — Identidad y organización

![Usuarios, roles, departamentos y equipos](relacional-03.svg)

### Relacional: página 4 — Territorio y contexto

![Territorio y contexto urbano provisional](relacional-04.svg)

### Clases Java simplificadas

![Clases del dominio, enums, embeddables y DTO](clases-simplificado.svg)

## Cómo revisar y mantener los modelos

1. Abrir el `.drawio` correspondiente y cambiar de página (el relacional tiene cuatro; el simplificado de clases tiene una).
2. Revisar primero [**el proceso de validación incremental**](../domain-model.md) y el [contrato dominio–API unificado](../api/domain-openapi.md). **No implementar todas las clases por el mero hecho de estar dibujadas**.
3. Al desarrollar un tag, contrastar en conjunto requisitos del enunciado, contratos OpenAPI, relaciones/PK/FK del ER, atributos/tipos de las clases y decisiones ADR.
4. Actualizar **las dos vistas** si cambia una entidad compartida, incluso aunque en el UML simplificado no aparezcan líneas de relación; exportar otra vez las imágenes editadas.
5. Documentar en el PR qué parte del dominio se revisó y qué puntos siguen pendientes. Las imágenes no sustituyen al `.drawio` fuente; conservar ambos.

**No son parte de esta entrega:** migración SQL, entidades JPA, validación de tipos/capacidades de los proveedores del contexto urbano, diagramas de despliegue de producción o secuencia completa de fotografías. Siguen siendo potencialmente útiles `incident-lifecycle.drawio` y `incident-flow.drawio`, pero están pendientes/no incluidos: no se pretende que existan.

**Revisión C4 pendiente de esta tarea:** `c4-containers.svg` sigue mencionando vídeos/documentos en la descripción de evidencias; el alcance inicial decidido es **solo fotografías** (ADR-0004, aceptado). No se han redibujado diagramas C4 en este PR.
