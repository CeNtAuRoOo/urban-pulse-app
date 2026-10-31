# Diagramas de UrbanPulse

Este directorio contiene las fuentes editables `.drawio` (diagrams.net) y sus **exportaciones SVG** para lectura en GitHub. **Las modificaciones correspondientes a Autenticación, Invitaciones V2 y su soporte de organización/auditoría ya están aplicadas a las fuentes y a las exportaciones** en la rama `feature/invitaciones-backend`, incluido el commit de documentación `56fc44d`. No queda pendiente «actualizar manualmente los diagramas de Invitaciones».

Los diagramas representan **el dominio lógico global**, cuya evolución se valida por tag. El esquema realmente migrado se limita a Flyway V1/V2; las demás entidades, asociaciones y servicios que aparecen en el modelo siguen siendo **diseño** hasta que tengan migraciones, código y pruebas.

## Inventario verificable

| Fuente editable | Exportación | Contenido |
| --- | --- | --- |
| [`c4.drawio`](c4.drawio) | [`c4-context.svg`](c4-context.svg) y [`c4-containers.svg`](c4-containers.svg) | C4 de contexto y de contenedores; contenedor API, almacenamiento y correo SMTP/Mailpit; alcance inicial de evidencias: fotografías. |
| [`relacional.drawio`](relacional.drawio), **página 1** | [`relacional-01.svg`](relacional-01.svg) | Incidencias y relaciones iniciales, aún en diseño. |
| [`relacional.drawio`](relacional.drawio), **página 2** | [`relacional-02.svg`](relacional-02.svg) | Tramitación y auditoría: `AuditEvent`, detalles JSONB y referencias históricas conforme al modelo vigente. |
| [`relacional.drawio`](relacional.drawio), **página 3** | [`relacional-03.svg`](relacional-03.svg) | Identidad/organización: `User`, roles, `EmailVerification`, `StaffInvitation`, `Department` y `DepartmentMembership`; V1/V2 en SQL. |
| [`relacional.drawio`](relacional.drawio), **página 4** | [`relacional-04.svg`](relacional-04.svg) | Territorio y contexto urbano, todavía modelo provisional. |
| [`clases-simplificado.drawio`](clases-simplificado.drawio) | [`clases-simplificado.svg`](clases-simplificado.svg) | Catálogo Java simplificado de entidades, DTO, enums y embebidos; estado de invitación derivado y atributos V1/V2. Sin conectores UML por elección de diseño. |

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

Estos puntos están recogidos en el estado gráfico actual; se citan como **criterios de mantenimiento**, no como lista de tareas sin hacer. Para la definición SQL exacta, el código y las invariantes, consultar [`V2__create_staff_invitations.sql`](../../backend/src/main/resources/db/migration/V2__create_staff_invitations.sql) y [ADR-0007](../adr/0007-invitaciones-personal.md) y [modelo de dominio](../domain-model.md). Las relaciones de otras áreas siguen sujetas a revisión incremental.

## Cómo mantener los modelos

1. Abrir el `.drawio` correspondiente y seleccionar la página adecuada.
2. Contrastar requisitos, [modelo de dominio](../domain-model.md), [contrato OpenAPI](../api/domain-openapi.md), migraciones reales y [ADR](../adr/README.md).
3. Cambiar la fuente `.drawio` y **volver a exportar el SVG correspondiente** dentro del mismo PR; si una entidad compartida cambia, comprobar ambas vistas de dominio.
4. Revisar los vínculos Markdown y las vistas previas; el SVG no sustituye al archivo editable.
5. Documentar qué parte está implementada y qué parte sigue siendo solo propuesta. Una clase dibujada no autoriza añadir automáticamente toda su persistencia.

**Diagramas nuevos potenciales, aún no existentes:** C4 Deployment de hosting definitivo, flujo/secuencia de subida de fotografías y máquina completa de estados/permisos de incidencias (`incident-lifecycle.drawio`/`incident-flow.drawio` no están en este inventario). No confundir esos diseños futuros con una supuesta desactualización de ER02, ER03, clases o C4 ya editados.
