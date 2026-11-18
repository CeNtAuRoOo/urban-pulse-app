## Título

ADR-009: Roles globales y departamentales, pertenencias y equipos

## Estado

Aceptado; roles globales y pertenencias por invitación implementados; equipos pendientes

## Fecha

2026-10-18

## Ámbito

Dominio, autorización y organización municipal

## Contexto

El enunciado diferencia ciudadanía, operadores municipales, técnicos y administradores sin precisar la organización de los trabajos ya derivados. Se necesita distinguir un operador municipal neutral, coordinadores departamentales y técnicos adscritos a departamentos, conservando historial y permitiendo que una misma cuenta mantenga sus capacidades ciudadanas.

## Opciones consideradas

1. Rol único `OPERATOR` y marca de coordinación en `DepartmentMembership`.
2. **Roles diferenciados por función y ámbito**, departamentos/equipos estables y relaciones temporales (**elegida**).
3. Entidades de usuario independientes para cada rol.
4. Departamento ficticio `General` para toda incidencia.

## Decisión

- Una sola entidad `User` para ciudadanos y personal; sin herencia de subclases. **Toda cuenta contiene siempre `CITIZEN` como rol global base**, incluidas cuentas que se incorporan por invitación. Los roles municipales se añaden, nunca sustituyen automáticamente a `CITIZEN`.
- Catálogo persistente `Role` (`code`, `description`, sin columna `scope`) y relación explícita `UserRole` para roles globales (`CITIZEN`, `OPERATOR`, `ADMIN`), con clave primaria compuesta `(user_id, role_code)` implementada. Una cuenta puede tener varios roles globales; el cliente no puede elegirlos al registrarse.
- Roles departamentales `DEPARTMENT_COORDINATOR` y `TECHNICIAN` mediante `DepartmentMembership` con usuario, rol, departamento, fechas de alta/baja. La clasificación `GLOBAL`/`DEPARTMENT` se obtiene del propio `RoleCode` mediante métodos como `isGlobal()`/`isDepartmental()`; se elimina `RoleScope` y no se persiste una columna `scope`. El servidor y, donde proceda, las restricciones SQL impiden asignar roles departamentales a `UserRole` o globales a `DepartmentMembership`.
- `OPERATOR` tiene competencias municipales transversales de revisión y priorización sin necesitar pertenencia a un departamento. El diseño de derivación y ejecución prevé que el coordinador distribuya los trabajos dentro de sus departamentos; el técnico acepte y ejecute los trabajos autorizados.
- En el diseño futuro, `Team` es estable, reutilizable y desactivable; preserva referencias históricas. `TeamMembership` relaciona `Team` con `DepartmentMembership` activa del rol técnico y del mismo departamento. No crear equipos ficticios de una sola persona para trabajos individuales.
- No se adoptan por ahora tablas genéricas `Permission`/`RolePermission`; las comprobaciones de rol y contexto se implementan en Spring Security y servicios.
- La autorización **implementada lee cuenta activa, versión del JWT y permisos globales actuales desde PostgreSQL en cada acceso protegido**, sin confiar en roles embebidos en el JWT. El canje comprueba titularidad de la invitación y ausencia de permisos duplicados; la autorización por pertenencia y estado de incidencias se concretará al implementar esos casos de uso. La excepción funcional es la emisión de JWT en registro/login y rutas expresamente públicas.
- No se incluye `ANALYST` por estar aplazada la parte analítica avanzada del enunciado.

## Consecuencias

**Revisión del 25/10/2026:** se elimina la duplicación de ámbito en `Role.scope`/`RoleScope`: el catálogo fijo de códigos es la única fuente de verdad. Los checks de ámbito siguen siendo invariantes obligatorias del dominio.

**Ventajas:** seguridad por contexto, roles múltiples, separación de la gestión transversal/departamental y continuidad de uso ciudadano del personal.

**Costes:** lecturas de estado/permisos por petición y necesidad de evitar N+1; controlar pertenencias concurrentes, revocaciones y privilegios cruzados cuando un usuario pertenezca a varios departamentos. La persistencia de `UserRole` debe quedar coherente con cualquier colección `User.globalRoles` en JPA: no representar dos fuentes editables independientes.

[Referencia relacionada](../pending.md#funcionalidad).
