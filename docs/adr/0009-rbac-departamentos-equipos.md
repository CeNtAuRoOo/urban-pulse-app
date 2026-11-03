# ADR-0009: Roles globales y departamentales, pertenencias y equipos

> **Lectura histórica:** el alcance previo y las menciones a V2 corresponden a la entrega original. La revisión del 02/11/2026 al final recoge el estado integrado vigente.
- **Estado:** Aceptado; roles globales y pertenencias por invitación implementados; equipos pendientes.
- **Fecha:** 2026-10-18.
- **Revisión:** 2026-10-27.
- **Ámbito:** Dominio, autorización y organización municipal.

## Contexto

El enunciado diferencia ciudadanía, operadores municipales, técnicos y administradores sin precisar la organización de los trabajos ya derivados. Se necesita distinguir un operador municipal neutral, coordinadores departamentales y técnicos adscritos a departamentos, conservando historial y permitiendo que una misma cuenta mantenga sus capacidades ciudadanas.

## Alternativas consideradas

1. Rol único `OPERATOR` y marca de coordinación en `DepartmentMembership`.
2. **Roles diferenciados por función y ámbito**, departamentos/equipos estables y relaciones temporales (**elegida**).
3. Entidades de usuario independientes para cada rol.
4. Departamento ficticio `General` para toda incidencia.

## Decisión

- Una sola entidad `User` para ciudadanos y personal; sin herencia de subclases. **Toda cuenta contiene siempre `CITIZEN` como rol global base**, incluidas cuentas que se incorporan por invitación. Los roles municipales se añaden, nunca sustituyen automáticamente a `CITIZEN`.
- Catálogo persistente `Role` (`code`, `description`, sin columna `scope`) y relación explícita `UserRole` para roles globales (`CITIZEN`, `OPERATOR`, `ADMIN`), con clave primaria compuesta `(user_id, role_code)` implementada. Una cuenta puede tener varios roles globales; el cliente no puede elegirlos al registrarse.
- Roles departamentales `DEPARTMENT_COORDINATOR` y `TECHNICIAN` mediante `DepartmentMembership` con usuario, rol, departamento, fechas de alta/baja. La clasificación `GLOBAL`/`DEPARTMENT` se obtiene del propio `RoleCode` mediante métodos como `isGlobal()`/`isDepartmental()`; se elimina `RoleScope` y no se persiste una columna `scope`. El servidor y, donde proceda, las restricciones SQL impiden asignar roles departamentales a `UserRole` o globales a `DepartmentMembership`.
- `OPERATOR` tiene competencias municipales transversales de revisión, priorización y derivación sin necesitar pertenencia a un departamento. El coordinador distribuye los trabajos dentro de sus departamentos; el técnico acepta/ejecuta los trabajos autorizados.
- `Team` es estable, reutilizable y desactivable; preserva referencias históricas. `TeamMembership` relaciona `Team` con `DepartmentMembership` activa del rol técnico y del mismo departamento. No crear equipos ficticios de una sola persona para trabajos individuales.
- No se adoptan por ahora tablas genéricas `Permission`/`RolePermission`; las comprobaciones de rol y contexto se implementan en Spring Security y servicios.
- La autorización **implementada lee cuenta activa, versión del JWT y permisos globales actuales desde PostgreSQL en cada acceso protegido**, sin confiar en roles embebidos en el JWT. Incluye control de titularidad, pertenencia activa y estado de recursos cuando corresponda. La excepción funcional es la emisión de JWT en registro/login y rutas expresamente públicas.
- No se incluye `ANALYST` por estar aplazada la parte analítica avanzada del enunciado.

## Consecuencias

**Revisión del 25/10/2026:** se elimina la duplicación de ámbito en `Role.scope`/`RoleScope`: el catálogo fijo de códigos es la única fuente de verdad. Los checks de ámbito siguen siendo invariantes obligatorias del dominio.

**Ventajas:** seguridad por contexto, roles múltiples, separación de la gestión transversal/departamental y continuidad de uso ciudadano del personal.

**Costes:** lecturas de estado/permisos por petición y necesidad de evitar N+1; controlar pertenencias concurrentes, revocaciones y privilegios cruzados cuando un usuario pertenezca a varios departamentos. La persistencia de `UserRole` debe quedar coherente con cualquier colección `User.globalRoles` en JPA: no representar dos fuentes editables independientes.

## Verificaciones pendientes por tag

- Matriz fina de operaciones autorizadas, revocación y promoción de roles.
- No se permite más de una pertenencia activa del mismo usuario/rol/departamento: V2 impone un índice único parcial con left_at IS NULL.
- Desactivación de equipos, conservación de integrantes históricos y transferencias de técnicos con asignaciones abiertas.
- **Ya implementados para `User`/`Role`/`UserRole`:** mapeos JPA, PK/FK/CHECK, `RoleCode` sin scope persistido, proyección SQL de roles, validación de `CITIZEN` y DTO propio de auth. **Implementados en Invitaciones:** Department y DepartmentMembership mínimos, roles departamentales y unicidad activa. **Pendientes:** equipos, autorización de operaciones de incidencias, proyecciones y visibilidad contextual.
- Selección dinámica de campos `fields` y posible anotación por propiedad con condiciones `OWNER`/`ADMIN`/`NONE`: **propuesta en investigación, no adoptada aún ni incluida en esta implementación**.
- Restricción de roles globales invitables a `OPERATOR`/`ADMIN` según ADR-0007.

**Alcance implementado `auth`:** entidades `User`, `Role`, `UserRole`, preferencias embebidas, rol CITIZEN al registrar y verificación de su presencia al validar cada JWT. V2 implementa departamentos y pertenencias para canjes. Team y TeamMembership siguen diseñados y sin migraciones. PostgreSQL no obliga por sí sola a que todo usuario conserve CITIZEN; la cadena JWT lo exige y el alta lo concede transaccionalmente.

## Incorporación municipal implementada (30/10/2026)

[ADR-0007](0007-invitaciones-personal.md) concede permisos a una cuenta existente,
activa y verificada, sin modificar CITIZEN. Department contiene UUIDv7, name,
description y active, sin CRUD ni catálogo precargado. DepartmentMembership guarda
UUIDv7, usuario, departamento, rol departamental, joinedAt/leftAt. El índice parcial
solo permite un permiso activo idéntico; pueden existir varios históricos finalizados.
CHECK impide roles globales y bajas anteriores al alta. Las FK restringen borrados.
La baja de pertenencias y retirada de UserRole quedan fuera; no hay endpoints para ello.
La lectura de JWT sigue resolviendo roles globales; no convierte TECHNICIAN en autoridad
global ni implementa por anticipado la autorización contextual de otros tags.

## Revisión integrada — 02/11/2026

La revisión histórica de Departamentos (30/10, main) conservaba tabla y semillas en su segunda migración, con UUIDv7 Hibernate, consultas con EMAIL_VERIFIED y escrituras con ADMIN global ([informe](../departments-finalization.md)).

La integración aprobada conserva la seguridad, adopta nombre/código únicos y fechas internas, y comparte `Department` con Invitaciones y pertenencias. La V1 consolidada no precarga departamentos; `uuidv7()` queda como default SQL y Hibernate VERSION_7 como generador JPA. Equipos siguen pendientes. Esta revisión sustituye la política de semillas anterior; no altera las evidencias históricas. [Migración vigente](0006-flyway.md#integración-aprobada--02112026).
