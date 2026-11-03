# Diagramas de UrbanPulse

Las fuentes editables conservan los cambios de Invitaciones y añaden Department completo y la V1 consolidada. Los SVG permanecen **idénticos al estado previo de feature/invitaciones-backend** por instrucción expresa; las vistas previas siguientes muestran esa versión anterior. La exportación manual queda pendiente.


Los diagramas representan **el dominio lógico global**, cuya evolución se valida por tag. El esquema realmente migrado se limita a Flyway V1 consolidada; las demás entidades, asociaciones y servicios que aparecen en el modelo siguen siendo **diseño** hasta que tengan migraciones, código y pruebas.

## Inventario verificable

| Fuente editable | Exportación | Contenido |
| --- | --- | --- |
| [`c4.drawio`](c4.drawio) | [`c4-context.svg`](c4-context.svg) y [`c4-containers.svg`](c4-containers.svg) | C4 de contexto y de contenedores; contenedor API, almacenamiento y correo SMTP/Mailpit; alcance inicial de evidencias: fotografías. |
| [`relacional.drawio`](relacional.drawio), **página 1** | [`relacional-01.svg`](relacional-01.svg) | Incidencias y relaciones iniciales, aún en diseño. |
| [`relacional.drawio`](relacional.drawio), **página 2** | [`relacional-02.svg`](relacional-02.svg) | Tramitación y auditoría: `AuditEvent`, detalles JSONB y referencias históricas conforme al modelo vigente. |
| [`relacional.drawio`](relacional.drawio), **página 3** | [`relacional-03.svg`](relacional-03.svg) | Identidad/organización: `User`, roles, `EmailVerification`, `StaffInvitation`, `Department` y `DepartmentMembership`; V1 consolidada en SQL. |
| [`relacional.drawio`](relacional.drawio), **página 4** | [`relacional-04.svg`](relacional-04.svg) | Territorio y contexto urbano, todavía modelo provisional. |
| [`clases-simplificado.drawio`](clases-simplificado.drawio) | [`clases-simplificado.svg`](clases-simplificado.svg) | Catálogo Java simplificado de entidades, DTO, enums y embebidos; estado de invitación derivado y atributos V1 consolidada. Sin conectores UML por elección de diseño. |

El conjunto de exportaciones es **SVG, no PNG**. Los diez archivos enumerados (tres `.drawio` y siete `.svg`) existen en la rama; no se necesitan rutas alternativas ni instrucciones de cambiar extensiones. El C4 contiene ambas vistas en una fuente editable; el relacional, cuatro páginas.

## Vistas previas

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

- **ER03:** las invitaciones tienen correo obligatorio (sin FK al destinatario), `codeHash` de 32 bytes, ámbito/rol/departamento, emisor, fechas, titular de canje y revocador; `DepartmentMembership` tiene unicidad activa parcial y conserva historial. La verificación de correo pertenece a V1.
- **ER02:** `AuditEvent` figura en la página de tramitación, **no se duplica** en ER03; el detalle es JSONB, con actor/recurso/fecha.
- **Clases:** `InvitationStatus` es derivado y no columna persistente; se mantienen nombres/campos actuales (`createdAt`, `isDepartmental`) y roles globales derivados de `UserRole`.
- **C4:** la API comparte transportes de correo y usa Mailpit **solo en desarrollo/pruebas**; el alcance de evidencias inicial es fotografías, no vídeos ni documentos.

Estos puntos están recogidos en el estado gráfico actual; se citan como **criterios de mantenimiento**, no como lista de tareas sin hacer. Para la definición SQL exacta, el código y las invariantes, consultar [`V1__initial_schema.sql`](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) y [ADR-0007](../adr/0007-invitaciones-personal.md) y [modelo de dominio](../domain-model.md). Las relaciones de otras áreas siguen sujetas a revisión incremental.

## Cómo mantener los modelos

1. Abrir el `.drawio` correspondiente y seleccionar la página adecuada.
2. Contrastar requisitos, [modelo de dominio](../domain-model.md), [contrato OpenAPI](../api/domain-openapi.md), migraciones reales y [ADR](../adr/README.md).
3. Cambiar la fuente `.drawio` y revisar ambas vistas de entidades compartidas. En esta integración los SVG no se editan: el responsable los exportará manualmente después de revisar las fuentes.
4. Revisar los vínculos Markdown y las vistas previas; el SVG no sustituye al archivo editable.
5. Documentar qué parte está implementada y qué parte sigue siendo solo propuesta. Una clase dibujada no autoriza añadir automáticamente toda su persistencia.

**Diagramas nuevos potenciales, aún no existentes:** C4 Deployment de hosting definitivo, flujo/secuencia de subida de fotografías y máquina completa de estados/permisos de incidencias (`incident-lifecycle.drawio`/`incident-flow.drawio` no están en este inventario). No confundir esos diseños futuros con una supuesta desactualización de ER02, ER03, clases o C4 ya editados.

## Integración de Departamentos — 02/11/2026

Se revisaron las dos páginas C4, las cuatro ER y la página de clases. Department incorpora código obligatorio único, nombre único y fechas internas; las pertenencias/invitaciones conservan relaciones e historial. C4 identifica el backend modular y los módulos actuales sin convertirlos en microservicios. Las etiquetas de migración describen la única V1.

La rama de Departamentos verificó visualmente sus diagramas en octubre y aportó `export-department.ps1`. Esa evidencia es histórica: el script se conserva, pero no se ejecuta en esta integración porque modifica SVG. La validación estructural actual comprueba XML, páginas, identificadores por página, conectores y geometrías; la revisión visual final y la exportación manual se indicarán separadamente.

**Comprobación de esta ejecución:** XML válido en las siete páginas (C4: 2; ER: 4; clases: 1), sin identificadores duplicados por página ni referencias rotas. Todos los identificadores y atributos de geometría coinciden con la rama original. No se ha verificado visualmente la apertura/renderizado final; no se localizó una instalación de draw.io en las rutas habituales del equipo. Los ocho SVG del repositorio, incluido el icono del frontend, coinciden con los blobs originales de HEAD.
