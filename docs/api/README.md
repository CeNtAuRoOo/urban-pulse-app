# Documentación de la API de UrbanPulse

## Estado: Autenticación y Departamentos implementados

UrbanPulse sigue el enfoque **design-first**: revisar casos de uso, modelo de dominio y contrato antes de desarrollar controladores, persistencia y frontend. El contrato OpenAPI 3.0.3 usa como versión de especificación de diseño **`0.4.0`** tras implementar verificación de correo. El resto del sistema permanece como diseño base de dominio v1: **los cinco endpoints del tag Autenticación están implementados en Java**; también existen las cuatro operaciones de Departamentos; los demás endpoints de negocio continúan pendientes.

El 18/10/2026 se alinearon los diagramas y OpenAPI `0.2.0`. El 25/10/2026 se acordó la siguiente revisión del tag de autenticación, recogida en [docs/auth-design.md](../auth-design.md), y sus ADR. **La implementación de autenticación ya existe en el backend**. Las vistas del dominio deben seguir sincronizadas con la migración SQL efectiva; el despliegue sobre Neon `development` aún no consta.

Los cambios incluyen registro ciudadano que devuelve JWT, nuevo cambio autenticado de contraseña, invalidación de todos los JWT previos con `tokenVersion`, consulta de cuenta y permisos actuales por acceso protegido, campo `User.emailVerified`, roles de ciudadanía como base de todas las cuentas, UUIDv7 generado por Hibernate y limitación de peticiones local. Además, se excluye `CITIZEN` de invitaciones para personal global. La recuperación de contraseña olvidada, refresh tokens y `GET fields` siguen aplazados.

**Ajustes generales anteriores:** los códigos de rol se restringen por ámbito (`GlobalRoleCode`/`DepartmentRoleCode`), los identificadores urbanos distinguen entidades UUID de IDs externos, y `StatusChangeDTO` admite que falten `fromStatus` y `changedBy`. Se desactivaron las dos operaciones de evaluación IA que presuponían un motor no acordado; sus archivos YAML permanecen como exploración no activa.

## Fuente editable y organización del contrato

```text
docs/api/
├── README.md
├── domain-openapi.md
├── openapi.yaml                  # Bundle: generado, nunca editar a mano
├── openapi.html                  # Documentación HTML: generada
└── openapi/
    ├── base-openapi.yaml         # Entrada modular; fuente de verdad
    ├── paths/                   # Operaciones por tag
    │   ├── autenticacion.yaml
    │   ├── usuarios.yaml
    │   ├── invitaciones.yaml
    │   ├── catalogos.yaml
    │   ├── incidencias.yaml
    │   ├── evidencias.yaml
    │   ├── tramitacion.yaml
    │   ├── duplicados.yaml
    │   ├── contexto-urbano.yaml
    │   ├── mapas.yaml
    │   ├── notificaciones.yaml
    │   ├── analitica.yaml
    │   ├── informes.yaml
    │   ├── asistencia-inteligente.yaml
    │   ├── conocimiento.yaml
    │   └── administracion.yaml
    └── components/
        ├── schemas/              # DTO por ámbito y comunes.yaml
        ├── parameters.yaml
        ├── responses.yaml
        └── security-schemes.yaml
```

La fuente de verdad es `openapi/base-openapi.yaml` y sus `$ref` a `paths`/`components`. **`openapi.yaml` no se actualiza automáticamente** cuando se edita el YAML modular. Tampoco debe actualizarse el HTML a mano. Ambos artefactos se generan al completar la revisión.

### Convenciones

- La organización se hace por funcionalidad (`tag`), no siempre por prefijo URL; `/users/me/password-changes` pertenece al tag **Autenticación**, aunque su ruta comience por `/users`.
- Las claves de primer nivel en `paths/*.yaml` son rutas completas y las referencias desde `base-openapi.yaml` usan punteros JSON con `/` escapado como `~1`.
- Reutilizar DTO, parámetros, seguridad y respuestas; cualquier cambio compartido se revisa en los demás tags.
- Orden editorial: `tags`, `summary`, `description`, `operationId`, `security`, `x-roles`, parámetros/cuerpo y respuestas.
- `x-roles` es **solo documental**; el backend aplica roles, pertenencia, titularidad, estado y políticas de datos. `BearerAuth` se aplica por defecto; las operaciones públicas declaran `security: []`.
- Los errores usan `application/problem+json` con `ProblemDetail`; registro/login validan tipos y reglas según el acuerdo `400` JSON ilegible y `422` datos inválidos. `401` identifica fallo de autenticación, sin enumerar correos.
- La primera especificación no utiliza `x-phase`, `x-requirements`, anclas/alias YAML ni selección dinámica `fields`.

## Flujo obligatorio: un tag y las entidades que usa

Cada tag es una unidad de **revisión e implementación**, no una obligación de construir el dominio completo.

1. **Requisitos:** identificar alcance del enunciado y apartados opcionales aplazados (IA entrenada, RAG, hotspots predictivos).
2. **Contrato:** leer `openapi/paths/<tag>.yaml`, esquemas de entrada/salida, rutas, códigos HTTP, validaciones, filtros, paginación y visibilidad.
3. **Dominio:** abrir **ambos** diagramas: `relacional.drawio` (PK/FK, cardinalidades) y `clases-simplificado.drawio` (atributos Java, enumeraciones, objetos embebidos, DTO). Verificar entidades compartidas.
4. **Decisiones:** contrastar `docs/domain-model.md`, ADR y, en autenticación, `docs/auth-design.md`. No copiar límites o campos del borrador sin verificar.
5. **Acordar el cambio:** coordinar divergencias en el mismo PR, modificando YAML modular, fuentes de diagramas, exportaciones y ADR que procedan; no anticipar tablas de otros tags.
6. **Validar e implementar:** ejecutar lint del contrato y regenerar bundle/HTML; JPA, repositorios, Flyway, servicios, controladores, pruebas funcionales/de seguridad/contrato y persistencia aislada solo para el alcance aprobado. Nunca ejecutar experimentos contra Neon `production`.
7. **Describir en PR:** qué se revisó, qué quedó pendiente, qué se implementó y qué verificaciones realmente se ejecutaron. Aprobar un tag no valida el dominio completo.

## Alineación entre OpenAPI y dominio

**Comportamiento HTTP real del tag:** registro `201` sin `Location`, login `200`, cambio `204`, confirmación `204` y reenvío `202`/`204`/`410`. Los cinco limitan el cuerpo a 16 KiB (`400` en exceso); registro/login y confirmación/reenvío tienen cuotas independientes `429`/`Retry-After`, pero cambio de contraseña no. Un JWT de cuenta no verificada sirve solo en auth y las rutas de negocio responden `403 EMAIL_NOT_VERIFIED`. Indisponibilidad tipada de PostgreSQL → `503`, errores internos → `500`. La autenticación exige `CITIZEN`.

El contrato dominio–OpenAPI anterior armonizó roles globales/departamentales, estados `ASSIGNED_TO_DEPARTMENT`/`WORK_ASSIGNED`, operador neutral, derivación versus trabajo, aceptación y reasignación, invitaciones, duplicados y UUID.

Los módulos opcionales de IA entrenada, _hotspots_ predictivos y RAG permanecen como material exploratorio, fuera de las rutas de la especificación base activa. El tag Catálogos implementa exclusivamente `Department`, incluido el código corto único del DTO, su migración y sus endpoints; el resto continúa como diseño pendiente.

La revisión de autenticación concreta ahora:

| Aspecto              | Decisión de diseño                                                              | Contrato afectado                                                    |
| -------------------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Registro             | `CITIZEN`, `emailVerified=false`, preferencias en true; devuelve JWT            | `POST /auth/register` → `201 TokenDTO`                               |
| Login                | Credenciales BCrypt y cuenta activa; caducidad 7200 s                           | `POST /auth/login` → `200 TokenDTO`; `401` en credenciales inválidas |
| Cambio de contraseña | Incrementa `tokenVersion` y anula JWT previos                                   | `POST /users/me/password-changes` → `204`                            |
| Seguridad            | Consultar estado y roles vigentes en cada petición protegida                    | Esquema Bearer JWT y ADR-0005; `x-roles` documental                  |
| Protección antiabuso | Limitador local por instancia para login/registro                               | `429` y `Retry-After` cuando se conozca                              |
| Roles                | Todas las cuentas incluyen `CITIZEN`; invitaciones globales solo OPERATOR/ADMIN | `UserDTO`, `StaffGlobalRoleCode`, `GlobalInvitationRequest`          |
| Identificadores      | UUIDv7 generado en Hibernate                                                    | PK persistentes; API `format: uuid` sin asumir versión               |

Consulta también [contrato dominio–API](domain-openapi.md) y [diseño de autenticación](../auth-design.md).

## Validación y visualización

Desde la raíz, si se dispone de Node/npm y acceso a Redocly CLI:

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx @redocly/cli build-docs docs/api/openapi.yaml -o docs/api/openapi.html
```

Son comandos para regenerar artefactos derivados tras editar el YAML modular; **la actualización del backend no sustituye esta generación**. Ejecutados el 30/10/2026 con Redocly 2.16.0: lint válido (dos advertencias), bundle y HTML regenerados. Regenerar y revisar ambos diffs antes de confirmar el contrato, comprobando `info.version=0.4.0` y que no hay referencias rotas. Swagger Editor puede abrir el bundle único generado.

## Cuestiones aún abiertas

- Matriz de transiciones de incidencias y permisos por operación/ámbito fuera del primer núcleo de identidad.
- Reglas completas de adscripción, invitaciones (correo vinculado, entrega, caducidad, canje), equipos y permisos departamentales.
- Límites de fotografías, EXIF, privacidad y consistencia SQL/Object Storage.
- Contexto geográfico: fuentes externas, caducidad y modelo espacial.
- Semántica y visibilidad de DTO compartidos; `fields` **no está aprobado** y se investigará aparte, incluida la posibilidad de anotaciones `OWNER`/`ADMIN`/`NONE`.
- Mejora futura: refresh tokens, recuperación por email y entrega SMTP real (la verificación obligatoria ya está implementada localmente). El módulo ya concreta HS256, BCrypt coste 12 y cuotas locales documentadas en [auth-design.md](../auth-design.md). La rotación de secretos, proxy confiable y requisitos globales de limitación quedan para despliegue.

## Referencias

- [Diseño acordado de autenticación](../auth-design.md).
- [Modelo de dominio y revisión por tag](../domain-model.md).
- [Diagramas](../diagrams/README.md).
- [Contribución](../../CONTRIBUTING.md).
- [Arquitectura](../architecture.md).
- [ADR-0005](../adr/0005-jwt-spring-security.md), [ADR-0007](../adr/0007-invitaciones-personal.md), [ADR-0009](../adr/0009-rbac-departamentos-equipos.md), [ADR-0011](../adr/0011-identificadores-uuidv7.md).

## Verificación de correo implementada (28/10/2026)

El tag **Autenticación** incluye cinco operaciones ejecutables: registro `201`, login `200`, cambio de contraseña `204`, confirmación `POST /auth/email-verifications/confirm` (`204`) y reenvío `POST /auth/email-verifications/resend` (`202`/`204`, `410` si la cuenta pendiente ha caducado). El nuevo `ConfirmEmailRequest` solo acepta código ASCII de 8 dígitos; un código inválido, usado o caducado devuelve `422 INVALID_VERIFICATION_CODE`. La confirmación utiliza el JWT del titular y no requiere enviar el email en el cuerpo.

Las cuentas `emailVerified=false` obtienen JWT pero solo pueden acceder a las operaciones de autenticación permitidas; las rutas protegidas de negocio responden `403 EMAIL_NOT_VERIFIED`. Las cuotas de confirmación y reenvío (`429`) son buckets por usuario/IP en memoria. El envío SMTP se solicita después del commit, pero `201` y `202` **no garantizan recepción** del correo; Mailpit es solo una infraestructura local de pruebas. El resto de los tags permanecen diseños revisables.

Los archivos editables son `openapi/base-openapi.yaml`, `openapi/paths/autenticacion.yaml`, `openapi/components/schemas/autenticacion.yaml` y `openapi/components/responses.yaml`. Tras editarlos, ejecutar lint y regenerar `openapi.yaml` y `openapi.html` con Redocly; **no mantener estos dos derivados a mano**. La revisión del contrato pasa a `info.version: 0.4.0` (no cambia el prefijo HTTP `/api/v1`).

## Departamentos municipales — revisión 30/10/2026

El tag Catálogos implementa exclusivamente `Department`: GET listado/detalle en `/api/v1/departments`, POST y PATCH. GET exige identidad y correo verificado; escritura añade rol global ADMIN, consultado en PostgreSQL por la seguridad existente. Desactivar mediante `active=false` conserva el registro y su UUID; no existe DELETE.

Nombre (2–120) y código ASCII (2–20) se recortan con `trim()` antes de validar; se conservan espacios interiores del nombre y se convierte el código a mayúsculas. Unicidad sin distinguir mayúsculas. Descripción opcional hasta 500; alta activa por defecto. PATCH omite para conservar, rechaza null explícito (400), permite descripción vacía y exige algún campo (422). El listado incluye activos/inactivos ordenados por nombre según la collation de PostgreSQL. Los parches concurrentes bloquean la fila durante la transacción.

`Department` usa UUIDv7 Hibernate según ADR-0011. V2 crea `departments`, índices únicos y semillas fijas; se conserva V2 sin modificar su checksum. El default SQL uuidv7() cubre inserciones externas. Las fechas son internas, `createdAt` inmutable y `updatedAt` cambia al modificar datos. No se crean FK hacia módulos todavía no implementados.

Errores comunes ProblemDetail con `code`, `title`, `status`, `detail`, `instance`: 400 entrada no interpretable/UUID inválido, 401 JWT, 403 permisos/verificación, 404 DEPARTMENT_NOT_FOUND, 409 DEPARTMENT_DUPLICATE, 422 validación, 500 fallo interno y 503 indisponibilidad tipada. Solo SQLSTATE 23505 de los dos índices conocidos se traduce a duplicado. Consulte [informe de cierre](../departments-finalization.md) para resultados y limitaciones.
