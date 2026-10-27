# Documentación de la API de UrbanPulse

## Estado: contrato del tag Autenticación implementado; demás tags en diseño

UrbanPulse sigue el enfoque **design-first**: revisar casos de uso, modelo de dominio y contrato antes de desarrollar controladores, persistencia y frontend. El contrato OpenAPI 3.0.3 usa como versión de especificación de diseño **`0.3.0`** tras revisar el tag Autenticación. El resto del sistema permanece como diseño base de dominio v1: **los tres endpoints del tag Autenticación están implementados en Java**; el resto de endpoints de negocio continúa pendiente.

El 18/10/2026 se alinearon los diagramas y OpenAPI `0.2.0`. El 25/10/2026 se acordó la siguiente revisión del tag de autenticación, recogida en [docs/auth-design.md](../auth-design.md), y sus ADR. **La implementación de autenticación ya existe en el backend**. Las vistas del dominio deben seguir sincronizadas con la migración SQL efectiva; el despliegue sobre Neon `development` aún no consta.

Los cambios incluyen registro ciudadano que devuelve JWT, nuevo cambio autenticado de contraseña, invalidación de todos los JWT previos con `tokenVersion`, consulta de cuenta y permisos actuales por acceso protegido, campo `User.emailVerified`, roles de ciudadanía como base de todas las cuentas, UUIDv7 generado por Hibernate y limitación de peticiones local. Además, se excluye `CITIZEN` de invitaciones para personal global. La verificación obligatoria por correo, recuperación de contraseña olvidada, refresh tokens y `GET fields` siguen aplazados.

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

**Comportamiento HTTP real del tag:** registro `201` sin `Location` hasta implementar el GET de perfil; login `200`, cambio `204`. Los tres limitan el cuerpo a 16 KiB (`400` en exceso); solo registro/login aplican cuota `429` y `Retry-After`. La indisponibilidad tipada de PostgreSQL devuelve `503`, no `401`; errores SQL de programación son `500`, con `ProblemDetail`. Usuarios sin rol `CITIZEN` no pueden autenticarse. El contrato debe declarar `503` en las tres operaciones y no anunciar `429` para el cambio de contraseña.

El contrato dominio–OpenAPI anterior armonizó roles globales/departamentales, estados `ASSIGNED_TO_DEPARTMENT`/`WORK_ASSIGNED`, operador neutral, derivación versus trabajo, aceptación y reasignación, invitaciones, duplicados y UUID.

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

Son comandos para regenerar artefactos derivados tras editar el YAML modular; **la actualización del backend no sustituye esta generación**. No se afirma que se hayan ejecutado en la última revisión. Regenerar y revisar ambos diffs antes de confirmar el contrato, comprobando `info.version=0.3.0` y que no hay referencias rotas. Swagger Editor puede abrir el bundle único generado.

## Cuestiones aún abiertas

- Matriz de transiciones de incidencias y permisos por operación/ámbito fuera del primer núcleo de identidad.
- Reglas completas de adscripción, invitaciones (correo vinculado, entrega, caducidad, canje), equipos y permisos departamentales.
- Límites de fotografías, EXIF, privacidad y consistencia SQL/Object Storage.
- Contexto geográfico: fuentes externas, caducidad y modelo espacial.
- Semántica y visibilidad de DTO compartidos; `fields` **no está aprobado** y se investigará aparte, incluida la posibilidad de anotaciones `OWNER`/`ADMIN`/`NONE`.
- Mejora futura: refresh tokens, verificación de correo obligatoria para avanzar y recuperación por email. El módulo ya concreta HS256, BCrypt coste 12 y cuotas locales documentadas en [auth-design.md](../auth-design.md). La rotación de secretos, proxy confiable y requisitos globales de limitación quedan para despliegue.

## Referencias

- [Diseño acordado de autenticación](../auth-design.md).
- [Modelo de dominio y revisión por tag](../domain-model.md).
- [Diagramas](../diagrams/README.md).
- [Contribución](../../CONTRIBUTING.md).
- [Arquitectura](../architecture.md).
- [ADR-0005](../adr/0005-jwt-spring-security.md), [ADR-0007](../adr/0007-invitaciones-personal.md), [ADR-0009](../adr/0009-rbac-departamentos-equipos.md), [ADR-0011](../adr/0011-identificadores-uuidv7.md).
