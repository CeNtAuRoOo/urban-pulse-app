# Diagramas de UrbanPulse

Las fuentes editables describen la V1 consolidada, incluidas Incidencias y Evidencias, distinguiendo estructuras base de tramitación y modelo futuro. La exportación documentada del 04/11/2026 se realizó con draw.io Desktop 32.6.1. La revisión correctiva del 05/11 valida fuentes y no genera ni modifica SVG.

Los diagramas representan **el dominio lógico global**, cuya evolución se valida por tag. El esquema implementado comprende una sola V1 con 14 tablas y restricciones finales; las demás entidades, asociaciones y servicios que aparecen en el modelo siguen siendo **diseño** hasta que tengan migraciones, código y pruebas.

## Inventario verificable

| Fuente editable                                            | Exportación                                                                   | Contenido                                                                                                                                                                                                                                |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`c4.drawio`](c4.drawio)                                   | [`c4-context.svg`](c4-context.svg) y [`c4-containers.svg`](c4-containers.svg) | C4 de contexto y de contenedores; contenedor API, almacenamiento y correo SMTP/Mailpit; alcance inicial de evidencias: fotografías.                                                                                                      |
| [`relacional.drawio`](relacional.drawio), **página 1**     | [`relacional-01.svg`](relacional-01.svg)                                      | Category en V1; Incident, Attachment y StatusChange base en V1; territorio y duplicados futuros.                                                                                                                                         |
| [`relacional.drawio`](relacional.drawio), **página 2**     | [`relacional-02.svg`](relacional-02.svg)                                      | AuditEvent V1; IncidentReview/PriorityDecision base V1; Assignment y operaciones futuras.                                                                                                                                                |
| [`relacional.drawio`](relacional.drawio), **página 3**     | [`relacional-03.svg`](relacional-03.svg)                                      | Identidad/organización: `User`, roles, `EmailVerification`, `StaffInvitation`, `Department` y `DepartmentMembership`; V1 consolidada en SQL.                                                                                             |
| [`relacional.drawio`](relacional.drawio), **página 4**     | [`relacional-04.svg`](relacional-04.svg)                                      | Territorio/contexto provisional; la nota distingue coordenadas V1 de geocodificación futura.                                                                                                                                             |
| [`clases-simplificado.drawio`](clases-simplificado.drawio) | [`clases-simplificado.svg`](clases-simplificado.svg)                          | Catálogo Java simplificado de entidades, DTO, enums y embebidos; estado de invitación derivado y atributos V1, núcleo de Incidencias y Evidencias, ocho estados y campos futuros de Location. Sin conectores UML por elección de diseño. |

El conjunto de exportaciones es **SVG, no PNG**. Los diez archivos enumerados (tres `.drawio` y siete `.svg`) existen en la rama; no se necesitan rutas alternativas ni instrucciones de cambiar extensiones. El C4 contiene ambas vistas en una fuente editable; el relacional, cuatro páginas.

## Vistas previas históricas

Los siete SVG son exportaciones históricas; no representan la revisión actual de las fuentes ni la consolidación y corrección de Evidencias del 11/11/2026.

### C4 — contexto y contenedores

![Contexto](c4-context.svg)

![Contenedores](c4-containers.svg)

### Relacional — página 1: incidencias

![Incidencias](relacional-01.svg)

### Relacional — página 2: tramitación y auditoría

![Tramitación](relacional-02.svg)

### Relacional — página 3: identidad y organización

![Identidad y organización](relacional-03.svg)

### Relacional — página 4: territorio y contexto

![Territorio y contexto](relacional-04.svg)

### Clases simplificadas

![Catálogo de clases](clases-simplificado.svg)

## Criterios de sincronización

La validación del **04/11/2026** comprueba XML válido en las tres fuentes y los siete SVG, identificadores únicos y referencias de conexiones válidas en cada página. Se inspeccionaron visualmente las siete páginas mediante renders PNG del mismo exportador: dos C4, cuatro ER y el catálogo de clases. Se corrigieron el recorte del texto de la base de datos en C4 y la proximidad de la nota de verificación a su entidad en ER03; las exportaciones finales muestran las etiquetas completas y separadas.

La leyenda de C4 y las cabeceras ER distinguen implementación y diseño futuro. Esta revisión no acredita despliegue en Neon, entrega SMTP externa ni implementación de las entidades futuras.

- **ER03:** las invitaciones tienen correo obligatorio (sin FK al destinatario), `codeHash` de 32 bytes, ámbito/rol/departamento, emisor, fechas, titular de canje y revocador; `DepartmentMembership` tiene unicidad activa parcial y conserva historial. La verificación de correo pertenece a V1.
- **ER02:** `AuditEvent` figura en la página de tramitación, **no se duplica** en ER03; el detalle es JSONB, con actor/recurso/fecha.
- **Clases:** `InvitationStatus` es derivado y no columna persistente; se mantienen nombres/campos actuales (`createdAt`, `isDepartmental`) y roles globales derivados de `UserRole`.
- **C4:** la API comparte transportes de correo y usa Mailpit **solo en desarrollo/pruebas**; el alcance de evidencias inicial es fotografías, no vídeos ni documentos.

Estos puntos describen la revisión gráfica histórica y se conservan como criterios de mantenimiento. Para la definición SQL exacta, el código y las invariantes, consultar [`V1__initial_schema.sql`](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) y [ADR-0007](../adr/0007-invitaciones-personal.md) y [modelo de dominio](../domain-model.md). Las relaciones de otras áreas siguen sujetas a revisión incremental.

## Cómo mantener los modelos

1. Abrir el `.drawio` correspondiente y seleccionar la página adecuada.
2. Contrastar requisitos, [modelo de dominio](../domain-model.md), [contrato OpenAPI](../api/domain-openapi.md), migraciones reales y [ADR](../adr/README.md).
3. Cambiar la fuente `.drawio` y revisar ambas vistas de entidades compartidas, conservando páginas, identificadores, geometrías y conexiones ajenas al cambio.
4. Exportar cada página a SVG desde diagrams.net y revisar los vínculos Markdown y las vistas previas; el SVG no sustituye al archivo editable.
5. Documentar qué parte está implementada y qué parte sigue siendo solo propuesta. Una clase dibujada no autoriza añadir automáticamente toda su persistencia.

**Diagramas nuevos potenciales, aún no existentes:** C4 Deployment de hosting definitivo, flujo/secuencia de subida de fotografías y máquina completa de estados/permisos de incidencias (`incident-lifecycle.drawio`/`incident-flow.drawio` no están en este inventario). No confundir esos diseños futuros con una supuesta desactualización de ER02, ER03, clases o C4 ya editados.

## Catálogos implementados

Department incluye nombre/código únicos, estado y fechas internas. Category incluye UUIDv7, nombre recortado único de 2–100 puntos Unicode, descripción nullable de hasta 500 y active; no añade código ni fechas. Ambos pertenecen a V1 y no tienen semillas. Las relaciones de Department con pertenencias/invitaciones son persistentes; la asociación opcional Category–Incident se incorpora en V1 con FK RESTRICT.

C4 mantiene un solo backend con nueve módulos. Para reproducir una exportación con draw.io Desktop (páginas numeradas desde 1):

```bash
drawio --disable-update -x --timeout 45 --theme light --embed-svg-fonts false -b 15 -p 1 -f svg -o docs/diagrams/c4-context.svg docs/diagrams/c4.drawio
```

Aplicar el mismo procedimiento a la página 2 de C4, las páginas 1–4 del relacional y la página 1 de clases, usando los nombres del inventario. Las imágenes PNG de revisión son auxiliares; los artefactos versionados siguen siendo SVG.

## Revisión histórica de fuentes de Incidencias (05/11/2026)

En aquella revisión, ER01 identificaba Incident y StatusChange base V2, preservando relaciones conceptuales de evidencias, duplicados y territorio; ER02 distingue revisión/prioridad base de operaciones futuras y alta/corrección mediante AuditEvent. ER04 aclara que dirección/barrio/distrito no están persistidos en V2. El catálogo de clases conserva ocho estados, fromStatus obligatorio y Location con coordenadas actuales y atributos geocodificados futuros.

La comprobación estructural de ambas fuentes verifica XML, unicidad de IDs (incluidos wrappers de objetos), referencias source/target/parent y conservación de geometría, estilos, páginas y conexiones. C4 actualiza únicamente las notas de alcance y el inventario de módulos. La revisión V2 modifica etiquetas existentes; no acredita una nueva inspección visual de exportaciones. [Modelo objetivo y roadmap](../incidents-implementation-roadmap.md).

La antigua V3 no cambiaba entidades, campos ni relaciones: refuerza motivos y justificaciones según ADR-0010. Las fuentes mantienen su geometría y conexiones; no se añaden elementos gráficos para duplicar CHECK de detalle. Los SVG se exportan manualmente desde estas fuentes conforme al procedimiento permanente anterior.

## Revisión de fuentes de Evidencias (11/11/2026)

Se modifican únicamente etiquetas de las tres fuentes drawio: alcance V1 y Evidencias en C4; columnas uploaded_by/storage_key y estado implementado de Attachment en ER01; atributos y límites reales de Attachment y ContentType JPEG/PNG en clases. Las asociaciones existentes con Incident/User se conservan. El inventario de tablas y restricciones finales se documenta en SQL/ADR; no se duplican CHECK detallados en cada dibujo.

La comparación estructural antes/después confirma XML válido, IDs únicos por página, referencias parent/source/target válidas y conservación exacta de páginas, geometrías, estilos y conexiones. No hay reserialización masiva ni cambios de SVG. No se acredita una nueva inspección visual mediante el editor gráfico; las etiquetas se ajustan dentro de las cajas existentes.
