# Documentación de la API REST UrbanPulse

> **Contrato vigente: OpenAPI `0.5.0`**. Los tags **Autenticación** e **Invitaciones**, y **Departamentos y Categorías** dentro de Catálogos, tienen endpoints backend implementados; las operaciones de los restantes tags describen el **diseño futuro** y no deben ofrecerse como rutas ejecutables. El backend tiene una arquitectura modular, pero el prefijo público sigue siendo `/api/v1`. Ni el contrato ni las pruebas aisladas implican un despliegue de V1 consolidada en Neon.

## 1. Fuente de verdad y artefactos derivados

| Ruta                                                                     | Función                                                                                                                                                   | Edición                        |
| ------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| [`openapi/base-openapi.yaml`](openapi/base-openapi.yaml)                 | Entrada principal de la especificación modular, metadatos, servidores y tags                                                                              | **Fuente editable**            |
| [`openapi/paths/`](openapi/paths/)                                       | Recursos y métodos por tag, entre ellos [`invitaciones.yaml`](openapi/paths/invitaciones.yaml) y [`autenticacion.yaml`](openapi/paths/autenticacion.yaml) | **Fuente editable**            |
| [`openapi/components/schemas/`](openapi/components/schemas/)             | DTO y esquemas de dominio y comunes                                                                                                                       | **Fuente editable**            |
| [`openapi/components/responses.yaml`](openapi/components/responses.yaml) | Respuestas de error/éxito compartidas                                                                                                                     | **Fuente editable**            |
| [`openapi.yaml`](openapi.yaml)                                           | Bundle monolítico con los `$ref` resueltos                                                                                                                | **Generado**: no editar a mano |
| [`openapi.html`](openapi.html)                                           | Documentación HTML renderizada                                                                                                                            | **Generado**: no editar a mano |

La especificación usa OpenAPI 3.0.3, `application/json` y errores `ProblemDetail`. Los identificadores internos públicos apropiados se expresan como UUID; los IDs externos pueden ser cadenas. Los esquemas DTO **no** replican las tablas JPA y pueden tener campos opcionales omitidos (no equivale a `null`). `x-roles` explica la autorización prevista, pero el servidor aplica reglas reales y contextuales.

## 2. Historial de versiones del contrato

- **`0.2.0`**: base documental de dominio compartido por tags (roles, organización, incidencias, asignaciones, identificadores, contexto).
- **`0.3.0`**: definición del núcleo de autenticación, registro con JWT, cambio de contraseña, invalidación por `tokenVersion` y permisos actuales desde PostgreSQL.
- **`0.4.0`**: verificación de correo, HMAC, confirmación/reenvío, SMTP local y política de acceso de cuentas pendientes.
- **`0.5.0` (vigente)**: implementación de Invitaciones con emisión, consulta/listado, revocación y canje, variantes disjuntas de DTO y `PageMetadata`; retirada del diseño de registro especial de personal. Incorpora los contratos implementados de Departamentos y Categorías, sin cambiar `/api/v1`.

La existencia de versiones históricas no implica que los 16 tags se hayan implementado. El frontend aún no consume una API completa de negocio.

## 3. Convenciones y reglas generales

- Rutas bajo `/api/v1`, sustantivos de recursos y operaciones HTTP explícitas; las acciones de dominio (revocación/canje) se modelan como recursos de operación.
- Autenticación Bearer JWT HS256, 7200 segundos, correo verificado para rutas de negocio; las autoridades globales reales se consultan en PostgreSQL y los permisos departamentales requieren contexto.
- Validación de JSON y DTO estrictos; `400` en cuerpo/tipos no interpretables, `422` en datos interpretables inválidos, `401`/`403` según autenticación/autorización, `409` para conflicto, `429 Retry-After`, `503` para DB indisponible y `500` genérico para fallos inesperados. No exponer detalles internos.
- `Cache-Control: no-store` en respuestas de invitaciones/identidad que contienen información sensible; `Location` en `201` de emisión con enlace al GET individual.
- La paginación de Invitaciones es por **número de página**, no por cursor: `page` desde 1, `pageSize` por defecto 20/máximo 100, filtros SQL, `PageMetadata`, orden `createdAt DESC, id DESC`.
- Los contratos de módulos todavía no implementados pueden contener requisitos revisables; no construir código por el mero hecho de que exista un `$ref`.

## 4. Endpoints de Autenticación implementados

| Método | Ruta bajo `/api/v1`                 | Estado y comportamiento                                                       |
| ------ | ----------------------------------- | ----------------------------------------------------------------------------- |
| `POST` | `/auth/register`                    | `201 TokenDTO`, crea `CITIZEN` y cuenta pendiente de confirmar correo         |
| `POST` | `/auth/login`                       | `200 TokenDTO`; credenciales incorrectas `401` genérico                       |
| `POST` | `/users/me/password-changes`        | `204`, revoca JWT anteriores incrementando `tokenVersion`                     |
| `POST` | `/auth/email-verifications/confirm` | `204`; código decimal de ocho dígitos protegido por HMAC                      |
| `POST` | `/auth/email-verifications/resend`  | `202` solicitud de entrega, `204` si ya verificado, `410` si expiró la cuenta |

En el registro el JWT inicial es de acceso limitado hasta confirmar el correo. `EMAIL_VERIFIED` se obtiene de PostgreSQL en cada petición; el mismo JWT funciona tras la confirmación sin cambiar su versión. La verificación usa código de hasta diez minutos, cuenta no verificada hasta 24 horas y correo por `AFTER_COMMIT` con Mailpit exclusivamente local. Cinco POST auth con cuerpos de máximo 16 KiB y cuotas específicas de registro/login/verificación. Véase [autenticación](../auth-design.md).

## 5. Endpoints de Invitaciones implementados

| Método | Ruta bajo `/api/v1`                             | Autorización                  | Respuesta                       |
| ------ | ----------------------------------------------- | ----------------------------- | ------------------------------- |
| `POST` | `/staff-invitations`                            | `ADMIN` activo y verificado   | `201 InvitationDTO`, `Location` |
| `GET`  | `/staff-invitations`                            | `ADMIN` activo y verificado   | `200 InvitationPageDTO`         |
| `GET`  | `/staff-invitations/{invitationId}`             | `ADMIN` activo y verificado   | `200 InvitationDTO`             |
| `POST` | `/staff-invitations/{invitationId}/revocations` | `ADMIN` activo y verificado   | `204`                           |
| `POST` | `/users/me/staff-invitation-redemptions`        | `CITIZEN` activo y verificado | `200 InvitationRedemptionDTO`   |

`InvitationRequest` es `oneOf`: `GlobalInvitationRequest` asigna `OPERATOR`/`ADMIN` **sin campo** `departmentId` (tampoco `null`), y `DepartmentInvitationRequest` requiere `TECHNICIAN`/`DEPARTMENT_COORDINATOR` con `departmentId` existente/activo. El cliente no escoge `expiresAt`. Se emite una invitación para un correo aunque no exista aún usuario; no crea identidad ni concede permisos hasta canje.

Las respuestas administrativas `201/200` incluyen `Cache-Control: no-store`; sus DTO omiten los campos opcionales no presentes. Los filtros del listado se evalúan en SQL por estado, correo normalizado, rol y departamento; la paginación empieza en `page=1`, tiene `pageSize=20` por defecto (máximo 100), orden `createdAt DESC, id DESC` y metadatos `PageMetadata`. El código aleatorio Base64URL de **256 bits/43 caracteres** se entrega solo por correo, nunca en un DTO administrativo o SQL en claro; se guarda HMAC-SHA256 independiente de los otros secretos y vence a los siete días por defecto. El canje exige JWT y correo verificado coincidente, conserva `CITIZEN` y roles previos, concede `UserRole` o `DepartmentMembership` atómicamente junto al consumo y auditoría. Si el mismo permiso está activo, devuelve `409` y no consume la invitación; si el código es inválido/usado/revocado/caducado/ajeno, responde `422 INVALID_INVITATION_CODE` genérico. Departamento no válido: `422 INVALID_DEPARTMENT`; revocación de invitación no activa: `409 INVITATION_NOT_ACTIVE`.

Canje: cuotas locales independientes **10/900 s por usuario** y **30/900 s por IP**. Los tres POST de Invitaciones protegen el tamaño de cuerpo (16 KiB), con revocación sin cuerpo. `201` de emisión confirma persistencia, **no recepción SMTP**: el listener entrega `AFTER_COMMIT` y no hay reintentos/outbox. Se eliminaron del contrato `/auth/staff-registrations`, `StaffRegistrationRequest` e `InvitationCreatedDTO`. `UserDTO` no añade pertenencias de departamento hasta que se diseñe el tag Usuarios. [ADR-0007: reglas y limitaciones](../adr/0007-invitaciones-personal.md) y [Desarrollo: pruebas](../development.md).

### 5.1. Departamentos implementados (Catálogos)

| Método | Ruta bajo `/api/v1`           | Autorización              | Respuesta                                   |
| ------ | ----------------------------- | ------------------------- | ------------------------------------------- |
| GET    | `/departments`                | Correo verificado         | 200, activos/inactivos ordenados por nombre |
| GET    | `/departments/{departmentId}` | Correo verificado         | 200, unidad solicitada                      |
| POST   | `/departments`                | ADMIN y correo verificado | 201, DepartmentDTO y Location               |
| PATCH  | `/departments/{departmentId}` | ADMIN y correo verificado | 200, DepartmentDTO                          |

El DTO de respuesta contiene `id`, `name`, `code`, `description` y `active`. Las fechas quedan internas; no se exponen entidades JPA.

Nombre obligatorio de 2–120 puntos de código Unicode; código obligatorio de 2–20, exclusivamente letras ASCII, números, guion y guion bajo. Se aplica `trim()` de Java a nombre/código antes de validar; se conservan espacios interiores del nombre. El código se convierte a mayúsculas con Locale.ROOT. Nombre y código son únicos sin distinguir mayúsculas. Las restricciones de longitud del contrato se refieren a los valores normalizados. Descripción opcional de hasta 500 puntos de código Unicode, sin recortarla automáticamente. Alta con `active=true` cuando se omite o envía null.

En PATCH, omitir un campo conserva su valor; null explícito devuelve 400. Esto mantiene los campos PATCH no anulables del contrato. Una descripción vacía elimina su contenido, sin convertirlo a SQL NULL. Un objeto vacío, cadenas fuera de límites o códigos inválidos devuelven 422. `active=false` desactiva y conserva UUID e historial; no hay DELETE. Si los valores enviados son idénticos, Hibernate puede no emitir UPDATE ni cambiar updatedAt.

Los errores de ambos catálogos se describen en [Errores compartidos](#errores-compartidos-de-catálogos).

### Contrato de categorías

**Categorías:** `GET /api/v1/categories` y `GET /api/v1/categories/{categoryId}` devuelven 200 para usuarios verificados, incluidas categorías inactivas; listado completo por nombre ascendente según PostgreSQL y UUID como desempate. `POST` devuelve 201 con Location absoluta y `PATCH` devuelve 200; ambas requieren ADMIN global verificado. Se consultan los permisos actuales en BD.

`CategoryResponse` expone siempre `id`, `name`, `description` y `active`, sin `code`. En [el esquema OpenAPI](openapi/components/schemas/catalogos.yaml), `description` es una propiedad requerida y nullable: si no hay descripción, las cuatro operaciones devuelven explícitamente `"description": null`, sin omitir la clave. Este comportamiento está cubierto por `CategoryControllerTest`. Los límites y la normalización se mantienen en el [modelo de dominio](../domain-model.md#catálogo-category).

Creación: descripción omitida/null → null, active omitido/null → true; `false` se conserva. PATCH: omitir conserva, null explícito → 400, objeto vacío → 422, `description: ""` limpia sin convertir a null; active permite desactivar/reactivar sin borrar. Los DTO rechazan propiedades desconocidas, incluidas `code` o `roles`; los permisos provienen del usuario autenticado.

### Errores compartidos de catálogos

Los errores usan `application/problem+json` y el contrato común `ProblemDetail` (`code`, `title`, `status`, `detail`, `instance`):

| Estado | Código y condición                                                                                                                                        |
| ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | `INVALID_JSON`: JSON ilegible, tipos incorrectos, claves duplicadas, contenido sobrante o null explícito en PATCH; `INVALID_PARAMETER`: UUID mal formado. |
| 401    | `UNAUTHORIZED`: JWT ausente, inválido, caducado o revocado; incluye `WWW-Authenticate: Bearer`.                                                           |
| 403    | `EMAIL_NOT_VERIFIED` o `FORBIDDEN`, según verificación y permisos.                                                                                        |
| 404    | `CATEGORY_NOT_FOUND` o `DEPARTMENT_NOT_FOUND`, según recurso.                                                                                             |
| 409    | `CATEGORY_DUPLICATE`: nombre reservado por otra categoría; `DEPARTMENT_DUPLICATE`: nombre o código de otro departamento.                                  |
| 422    | `VALIDATION_ERROR` o `UNKNOWN_PROPERTY`.                                                                                                                  |
| 500    | `INTERNAL_ERROR`.                                                                                                                                         |
| 503    | `SERVICE_UNAVAILABLE`: indisponibilidad tipada de PostgreSQL.                                                                                             |

Los manejadores comunes no exponen SQL, restricciones internas, tokens ni secretos en las respuestas. La [arquitectura](../architecture.md#4-persistencia-y-transacciones) documenta la traducción selectiva de conflictos y la auditoría RF18 pendiente; la [guía de desarrollo](../development.md#verificación-del-backend), sus pruebas.

## 6. Revisión del contrato y del dominio por tag

1. Elegir los casos de uso y contrastar el enunciado, roles y [ADR](../adr/README.md).
2. Estudiar conjuntamente path/DTO del tag y ambos [diagramas existentes](../diagrams/README.md), comprobando tipos, cardinalidad, nulabilidad, PK/FK, estados y autorización.
3. Resolver divergencias antes de implementar; actualizar modelo, OpenAPI **modular**, fuentes `.drawio`, exportaciones y ADR cuando corresponda.
4. Implementar **solo el alcance del tag**, con Flyway/JPA, servicios, HTTP, tests y revisiones de seguridad. Las páginas de diseño no obligan a crear toda la base de datos anticipadamente.
5. Ejecutar lint, bundle y generación HTML, revisar diferencias y declarar qué se ha probado realmente.

## 7. Validación y visualización

Desde la **raíz** del repositorio:

```bash
npx --yes @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx --yes @redocly/cli build-docs docs/api/openapi.yaml -o docs/api/openapi.html
```

**Validación del 03/11/2026:** OpenAPI `0.5.0` válido, sin errores y con dos avisos conocidos (`info.license` sin definir y servidor localhost). Bundle YAML y HTML generados desde la fuente modular. Las cuatro operaciones de cada catálogo conviven con Autenticación e Invitaciones; los otros tags continúan como diseño. [Pruebas del backend](../development.md#verificación-del-backend).

## 8. Alcance futuro y referencias

Quedan fuera: perfil/consulta de usuarios, equipos, retirada de permisos, autorización de incidencias por ámbito/titularidad, fotografía, notificaciones de negocio, mapas/contexto y API de auditoría. Los proveedores externos, recuperación de contraseña y bootstrap del primer `ADMIN` requieren diseño específico.

- [Contrato dominio–OpenAPI](domain-openapi.md), [modelo de dominio](../domain-model.md), [diagramas actualizados](../diagrams/README.md).
- [Arquitectura modular](../architecture.md), [autenticación](../auth-design.md), [ADR-0007 (Invitaciones)](../adr/0007-invitaciones-personal.md) y [desarrollo](../development.md).
