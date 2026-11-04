# Diagramas de UrbanPulse

Este directorio contiene las **fuentes editables en diagrams.net / draw.io** y sus exportaciones para revisión en GitHub. Se distinguen los diagramas de arquitectura C4 del **modelo de dominio v1**, que es una base compartida sincronizada con OpenAPI `0.2.0`, todavía pendiente de validación exhaustiva e implementación por tag, **salvo el núcleo de identidad/auth, Department y Category ya implementados**.

## Inventario

| Fuente editable                                            | Exportación para lectura                                                                                                                                                  | Propósito y estado                                                                                                                                                                                                                                                                                              |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`c4.drawio`](c4.drawio)                                   | [`c4-context.svg`](c4-context.svg) · [`c4-containers.svg`](c4-containers.svg)                                                                                             | Arquitectura C4, contexto y contenedores; diagramas ya disponibles. Se incorpora ahora la fuente `.drawio` independiente.                                                                                                                                                                                       |
| [`relacional.drawio`](relacional.drawio)                   | [`relacional-01.svg`](relacional-01.svg) · [`relacional-02.svg`](relacional-02.svg) · [`relacional-03.svg`](relacional-03.svg) · [`relacional-04.svg`](relacional-04.svg) | **Modelo relacional lógico v1** en cuatro páginas: incidencias, tramitación, organización, territorio/contexto. El núcleo identidad (`users`, `roles`, `user_roles`, `email_verifications`) ya tiene la migración V1 ampliada; `departments` está implementada mediante V2 y `categories` mediante V3; las demás tablas siguen propuestas. |
| [`clases-simplificado.drawio`](clases-simplificado.drawio) | [`clases-simplificado.svg`](clases-simplificado.svg)                                                                                                                      | **Catálogo de clases Java (núcleo auth, Department y Category implementados) v1**, una sola página: atributos, tipos, enums, embeddables y DTO auxiliares. Sin líneas UML intencionadamente: consultar relaciones en el ER.                                                                                               |

> **Nota de nombres:** las cuatro exportaciones `relacional-01` a `relacional-04` se enlazan aquí como `.svg`, siguiendo el patrón de las demás exportaciones. Si en la copia local se exportaron en `.png`, hay que adaptar los enlaces a las **extensiones reales** antes del commit. La fuente y sus SVG deben quedar sincronizados en cada PR del tag.

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

**Fuera del tag auth:** validación de tipos/capacidades de los proveedores del contexto urbano, diagramas de despliegue de producción o secuencia completa de fotografías. **Dentro de auth ya existen la V1 y entidades JPA mínimas**, aunque no se dibujan los componentes técnicos (filtros, repositorios, DTO HTTP) en el catálogo de dominio. Siguen siendo potencialmente útiles `incident-lifecycle.drawio` y `incident-flow.drawio`, pero están pendientes/no incluidos: no se pretende que existan.

**Revisión C4 pendiente de esta tarea:** `c4-containers.svg` sigue mencionando vídeos/documentos en la descripción de evidencias; el alcance inicial decidido es **solo fotografías** (ADR-0004, aceptado). No se han redibujado diagramas C4 en este PR.

### Cambios visuales pendientes por el sistema de correo (28/10/2026)

Los diagramas de esta carpeta **todavía no se han redibujado** para la nueva entidad y el servicio SMTP. Cambios obligatorios antes de dar por sincronizada la documentación gráfica:

1. En `relacional.drawio`, página **ER 03 (identidad/organización)**, añadir `email_verifications` con `user_id UUID` como PK/FK a `users(id)`, `code_hash BYTEA(32)`, `created_at TIMESTAMPTZ` y `expires_at TIMESTAMPTZ`, relación **User 1 : EmailVerification 0..1**, restricción de hash/caducidad e índices de expiración y usuarios sin verificar. Actualizar `relacional-03.svg`.
2. En `clases-simplificado.drawio`, añadir `EmailVerification` y relación `@MapsId` con `User` en los detalles del catálogo JPA, y la nueva capa de servicio si se representan componentes técnicos. Actualizar `clases-simplificado.svg`.
3. En `c4.drawio`, página de **contenedores**, incorporar la dependencia del backend respecto de un servidor SMTP configurable y **Mailpit solo para desarrollo**; no representarlo como servicio de producción ni insinuar un proveedor real contratado. Actualizar `c4-containers.svg`.

Las fuentes `.drawio` son editables; **no sustituirlas solo modificando el texto de un SVG**. Se conservan sin cambios en este paquete para evitar desincronizar una fuente no regenerada con su exportación. El resto de las entidades de otros tags permanece en estado de diseño.

## Revisión Category (02/11/2026)

Se actualizan la celda Category de ER01 y la del catálogo de clases: UUIDv7 Hibernate, nombre recortado 2–100 y único con `lower(name)`, descripción nullable hasta 500 y active obligatorio. Se conservan cuatro atributos, sin código ni fechas. Incident admite categoría opcional y sus relaciones siguen siendo diseño futuro; V3 no crea Incident ni FK.

`export-category.ps1` regenera exclusivamente los fragmentos Category de `relacional-01.svg` y `clases-simplificado.svg` leyendo ambas fuentes UTF-8. Conserva geometría y relaciones, como el exportador de Department. Comando: `powershell -NoProfile -ExecutionPolicy Bypass -File docs/diagrams/export-category.ps1` (política limitada al proceso). La exportación completa futura desde diagrams.net puede sustituir estos fragmentos.

El 02/11 se parsearon los XML y se renderizaron los dos SVG con Sharp, inspeccionando las celdas como PNG sin cortes ni solapamientos. **Permanece pendiente la comprobación visual completa en diagrams.net tras los cambios de Category:** las cuatro páginas del relacional y la página del catálogo de clases. La revisión manual anterior de Department no cubre estos cambios posteriores. La reorganización documental del 03/11 no modifica ni regenera diagramas ni repite su inspección visual. Los pendientes anteriores de correo, SMTP y C4 permanecen fuera del alcance.

## Revisión Department (30/10/2026)

Se actualizan las celdas Department de las dos fuentes: UUIDv7, nombre y código únicos normalizados, descripción opcional, activo y fechas. `DepartmentMembership` ya se implementa mediante Flyway V4 y entidad JPA; la clase del diagrama refleja `user`, `department`, `role`, `joinedAt` y `leftAt`. La regla de una pertenencia activa por usuario/departamento/rol se aplica con el índice parcial `uk_department_memberships_active`. Las relaciones con equipos e incidencias siguen representando el diseño futuro y no se han implementado en esta entrega.

`export-department.ps1` regenera el fragmento Department de los dos SVG a partir del texto de las fuentes, conservando las coordenadas y los demás elementos. Se usa texto vectorial para evitar conservar la imagen alternativa antigua de esa celda. Para regenerar: `powershell -File docs/diagrams/export-department.ps1`. Una exportación completa posterior desde diagrams.net puede sustituir estos fragmentos.

Se verificó el XML de las fuentes y se renderizaron localmente los SVG; las dos celdas Department se inspeccionaron como PNG sin recortes de texto ni solapamientos.

Posteriormente, se completó la revisión visual manual en diagrams.net: se abrieron las cuatro páginas de `relacional.drawio` y la página de `clases-simplificado.drawio`, comprobando que se visualizan correctamente, sin textos cortados ni elementos solapados. De esta forma, se completó la comprobación manual exigida por `CONTRIBUTING.md`.

Los pendientes relativos a `EmailVerification`, SMTP y C4 enumerados anteriormente son previos y ajenos al módulo Department. Permanecen documentados y no se consideran resueltos por esta revisión visual.
