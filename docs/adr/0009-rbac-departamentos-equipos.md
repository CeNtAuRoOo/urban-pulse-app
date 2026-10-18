# ADR-0009: Roles globales y departamentales, pertenencias y equipos

- **Estado:** Aceptado como decisión de diseño; implementación pendiente
- **Fecha:** 2026-10-18
- **Ámbito:** Dominio, autorización y organización municipal

## Contexto

El enunciado diferencia ciudadanía, operadores municipales, técnicos y administradores, pero no concreta cómo se distribuyen dentro de un departamento los trabajos ya derivados. Se necesita distinguir un operador **municipal neutral**, un coordinador de departamento y técnicos adscritos a uno o más departamentos; además, conservar el historial organizativo sin exigir un equipo ficticio por cada trabajo individual.

## Alternativas consideradas

1. Un solo rol `OPERATOR` con una bandera o posición en `DepartmentMembership` para el coordinador.
2. Roles diferenciados por función, asignados por ámbito; departamentos/equipos reales y temporales.
3. Tablas de usuarios distintas para operadores, coordinadores y técnicos.
4. Un departamento ficticio `General` para recibir todos los avisos.

## Decisión

- Una sola entidad `User` para ciudadanos y personal; sin herencia de subclases por rol.
- Catálogo persistente `Role` y relación `UserRole` para **roles globales** (`CITIZEN`, `OPERATOR`, `ADMIN`). Se admiten varios roles globales en la misma cuenta; no se trata de permisos administrables arbitrariamente.
- Roles **departamentales** (`DEPARTMENT_COORDINATOR`, `TECHNICIAN`) mediante `DepartmentMembership` con rol, departamento, incorporación y finalización. `Role.scope` indica `GLOBAL` o `DEPARTMENT`; la aplicación debe validar que cada tipo de vínculo use el ámbito correcto.
- `OPERATOR` no precisa pertenencia departamental para revisar, priorizar y derivar informes al departamento competente. `DEPARTMENT_COORDINATOR` gestiona los trabajos de sus departamentos; `TECHNICIAN` ejecuta/acepta únicamente trabajos autorizados.
- `Team` pertenece a un `Department` y existe de manera **estable y reutilizable**; un coordinador autorizado puede crearlo, modificarlo y **desactivarlo**, sin borrar las referencias históricas.
- `TeamMembership` asocia el equipo con una `DepartmentMembership` y registra fechas de pertenencia. La pertenencia debe referirse a un técnico autorizado del **mismo departamento** que el equipo.
- No crear equipos artificiales de una persona para asignaciones individuales. Tampoco introducir por ahora tablas genéricas `Permission`/`RolePermission`: Spring Security y los servicios aplican las reglas de autorización por rol y contexto.
- `ANALYST` y la parte analítica avanzada marcada como opcional en el enunciado se posponen.

## Consecuencias

**Ventajas:** separa tramitación transversal y gestión departamental; permite usuarios con funciones múltiples, control de acceso por ámbito e historial de pertenencias; evita construir departamentos organizativamente ficticios.

**Costes:** un rol por sí solo no basta para autorizar actuaciones; hacen falta consultas de pertenencias activas, comprobación de equipo/departamento, controles de concurrencia y auditoría. Hay que evitar privilegios cruzados accidentales cuando un usuario pertenece a varios departamentos.

## Verificaciones pendientes por tag

- Matriz de operaciones autorizadas para cada rol/ámbito; reglas de alta, revocación y promoción.
- Si se permite más de una pertenencia activa del mismo rol a un departamento y qué restricciones SQL impiden duplicados.
- Reglas de desactivación y conservación de integrantes antiguos, transferencias de técnicos y asignaciones abiertas.
- Mapeo JPA, FK/índices y DTO de roles/pertenencias; actualizar OpenAPI `usuarios`, `administracion`, `invitaciones`, `tramitacion` y `catalogos` cuando se implemente su alcance.

**Estado de entrega:** el modelo está documentado en `docs/diagrams` y `docs/domain-model.md`; no hay entidades JPA ni permisos implementados.
