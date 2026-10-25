# ADR-0009: Roles globales y departamentales, pertenencias y equipos

- **Estado:** Aceptado como decisión de diseño; implementación pendiente.
- **Fecha:** 2026-10-18.
- **Revisión:** 2026-10-25.
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
- Catálogo persistente `Role` (`code`, `scope`, `description`) y relación explícita `UserRole` para roles globales (`CITIZEN`, `OPERATOR`, `ADMIN`), con clave compuesta candidata. Una cuenta puede tener varios roles globales; el cliente no puede elegirlos al registrarse.
- Roles departamentales `DEPARTMENT_COORDINATOR` y `TECHNICIAN` mediante `DepartmentMembership` con usuario, rol, departamento, fechas de alta/baja. `Role.scope` diferencia `GLOBAL` y `DEPARTMENT` y el servidor comprueba el ámbito de las relaciones.
- `OPERATOR` tiene competencias municipales transversales de revisión, priorización y derivación sin necesitar pertenencia a un departamento. El coordinador distribuye los trabajos dentro de sus departamentos; el técnico acepta/ejecuta los trabajos autorizados.
- `Team` es estable, reutilizable y desactivable; preserva referencias históricas. `TeamMembership` relaciona `Team` con `DepartmentMembership` activa del rol técnico y del mismo departamento. No crear equipos ficticios de una sola persona para trabajos individuales.
- No se adoptan por ahora tablas genéricas `Permission`/`RolePermission`; las comprobaciones de rol y contexto se implementan en Spring Security y servicios.
- La autorización **lee cuenta activa y permisos actuales desde PostgreSQL en cada acceso protegido**, sin confiar en roles embebidos en el JWT. Incluye control de titularidad, pertenencia activa y estado de recursos cuando corresponda. La excepción funcional es la emisión de JWT en registro/login y rutas expresamente públicas.
- No se incluye `ANALYST` por estar aplazada la parte analítica avanzada del enunciado.

## Consecuencias

**Ventajas:** seguridad por contexto, roles múltiples, separación de la gestión transversal/departamental y continuidad de uso ciudadano del personal.

**Costes:** lecturas de estado/permisos por petición y necesidad de evitar N+1; controlar pertenencias concurrentes, revocaciones y privilegios cruzados cuando un usuario pertenezca a varios departamentos. La persistencia de `UserRole` debe quedar coherente con cualquier colección `User.globalRoles` en JPA: no representar dos fuentes editables independientes.

## Verificaciones pendientes por tag

- Matriz fina de operaciones autorizadas, revocación y promoción de roles.
- Si se permite más de una pertenencia activa del mismo rol por departamento y qué índice impide duplicados.
- Desactivación de equipos, conservación de integrantes históricos y transferencias de técnicos con asignaciones abiertas.
- Mapeos JPA, FK/índices, proyecciones de DTO y visibilidad de datos personales.
- Selección dinámica de campos `fields` y posible anotación por propiedad con condiciones `OWNER`/`ADMIN`/`NONE`: **propuesta en investigación, no adoptada aún ni incluida en esta implementación**.
- Restricción de roles globales invitables a `OPERATOR`/`ADMIN` según ADR-0007.

**Alcance inicial `auth`:** solo entidades `User`, `Role`, `UserRole`; las relaciones departamentales y los equipos se mantienen diseñados, sin migraciones ni implementación anticipada. El `CITIZEN` global se otorga al registrar toda cuenta nueva.
