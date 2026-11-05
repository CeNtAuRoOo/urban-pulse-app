# ADR-0013: Gestión de pertenencias de usuarios a departamentos

- **Estado:** Aceptado
- **Fecha:** 2026-11-03
- **Ampliación aprobada:** 2026-11-04; integración modular, V1 única y cuentas activas/verificadas.
- **Decisores:** Equipo de desarrollo de Urban Pulse
- **Relacionado con:** ADR-0009 y ADR-0012

## 1. Contexto

Urban Pulse necesita representar la relación entre los usuarios y los departamentos de la plataforma.

Un usuario puede pertenecer a varios departamentos y desempeñar diferentes roles dentro de ellos. Además, es necesario conservar el historial de estas relaciones para poder conocer las pertenencias que han estado vigentes en el pasado.

El modelo de roles globales de usuario no es suficiente para representar esta situación, ya que los permisos y responsabilidades pueden depender del departamento al que pertenece el usuario.

Por este motivo, se introduce la entidad `DepartmentMembership`, que representa una pertenencia de un usuario a un departamento con un rol determinado.

## 2. Decisión

Se implementa `DepartmentMembership` como una entidad independiente, relacionada con `User` y `Department`.

Cada registro representa la asignación de un usuario a un departamento con un rol específico.

Se establecen las siguientes reglas:

1. Un usuario puede pertenecer a varios departamentos.
2. Un usuario puede tener varios roles dentro de un mismo departamento.
3. Para una misma combinación de usuario, departamento y rol, solo puede existir una pertenencia activa.
4. Cuando una pertenencia finaliza, se registra su fecha de finalización mediante `leftAt`.
5. Las pertenencias finalizadas no se eliminan físicamente, ya que forman parte del historial.
6. Si un usuario vuelve a incorporarse al mismo departamento con el mismo rol, se crea un nuevo registro en lugar de reactivar o sobrescribir el anterior.

La pertenencia se considera activa cuando no tiene registrada una fecha de finalización (`leftAt`).

## 3. Modelo de datos

La entidad `DepartmentMembership` contempla la relación entre:

- **User:** usuario al que pertenece la asignación.
- **Department:** departamento al que se incorpora.
- **Role:** rol que desempeña dentro de ese departamento.
- **Fecha de incorporación:** momento en el que comienza la pertenencia.
- **Fecha de finalización (`leftAt`):** momento en el que termina la pertenencia. Su ausencia indica que sigue activa.

La base de datos debe garantizar la regla de unicidad de pertenencias activas para cada combinación de usuario, departamento y rol.

El historial se conserva mediante registros independientes, sin eliminar las asignaciones anteriores.

## 4. Integración con la autenticación y los usuarios

Las pertenencias departamentales se incorporan a la información devuelta sobre los usuarios mediante `UserDTO`.

También se incluyen en las respuestas de autenticación que devuelven los datos del usuario, como el registro y el inicio de sesión.

Cuando un usuario no tiene pertenencias departamentales, el campo `departmentMemberships` se devuelve como una colección vacía (`[]`), evitando valores nulos o la ausencia de la propiedad.

Esta información complementa los roles globales del usuario, sin sustituirlos.

## 5. Persistencia y migraciones

La tabla ya forma parte de `V1__initial_schema.sql`, migración inicial bajo la premisa autorizada de destino vacío. No existe una V4 adicional ni una segunda entidad. `organization/model/DepartmentMembership`, su repositorio y `organization/service/DepartmentMembershipService` son canónicos. La V1 contiene el índice parcial `department_memberships_active_unique` sobre `(user_id, department_id, role_code)` con `left_at IS NULL`, las FK históricas RESTRICT, roles departamentales y orden de fechas. No hay departamentos precargados; los tests crean sus unidades.

Toda alta exige usuario activo con correo verificado y departamento activo. El servicio bloquea usuario y después departamento; Invitaciones coordina el mismo orden (usuario → invitación → departamento), conserva su error de permiso duplicado y crea la pertenencia en la misma transacción que consumo y auditoría. Una infracción 23505 del índice parcial se traduce a conflicto; otras infracciones no se ocultan.

La baja interna consulta primero solo el ID del titular, bloquea usuario y después pertenencia y registra `leftAt >= joinedAt`. Repetirla se rechaza y nunca sobrescribe el periodo histórico. El siguiente ingreso crea un UUID nuevo. No se añaden endpoints de altas/bajas: la API de Usuarios conserva sus cinco operaciones. La exposición HTTP de retirada de permisos y los equipos siguen fuera de alcance.

La limpieza de cuentas no verificadas de más de 24 horas conserva íntegramente las cuentas referenciadas por pertenencias (incluso históricas), actores de invitaciones o auditoría. Se excluyen antes del límite del lote y se revalidan bajo bloqueo para no detener el barrido de cuentas eliminables. No se añade CASCADE, anonimización ni borrado de historia. Los códigos de verificación caducados mantienen su política independiente de limpieza.

Después de aplicar V1 en un entorno conservado, cualquier cambio posterior requiere una migración nueva; esta consolidación no autoriza reescribir esquemas desplegados.

## 6. Consecuencias

### Positivas

- Se permite que un usuario pertenezca a varios departamentos.
- Se pueden asignar distintos roles a un mismo usuario dentro de un departamento.
- Se conserva el historial de incorporaciones y salidas.
- Los reingresos no destruyen información histórica.
- Se separan los roles globales de los roles asociados a departamentos.
- La información departamental queda disponible en los DTO de usuario y autenticación.

### Consideraciones

- Las operaciones de alta y finalización de pertenencias deben respetar las restricciones de integridad.
- Las consultas deben distinguir entre pertenencias activas e históricas.
- Las futuras operaciones administrativas deberán utilizar la lógica de negocio correspondiente para evitar asignaciones duplicadas.
- Los permisos departamentales deben evaluarse de forma diferenciada de los roles globales.

## 7. Alternativas consideradas

### Eliminar las pertenencias al finalizar

Se descarta porque impediría conservar el historial de las relaciones entre usuarios y departamentos.

### Reactivar registros históricos

Se descarta porque sobrescribiría la información temporal de pertenencias anteriores y dificultaría la trazabilidad de los distintos periodos de actividad.

### Utilizar exclusivamente roles globales

Se descarta porque no permite representar correctamente que un usuario pueda tener responsabilidades diferentes según el departamento.

## 8. Relación con decisiones anteriores

Este ADR complementa el ADR-0009 en lo relativo a la conservación del historial de pertenencias.

También amplía el modelo de usuarios documentado en el ADR-0012, incorporando las relaciones departamentales sin sustituir los roles globales.

## 9. Referencias

- ADR-0009.
- ADR-0012.
- Entidades `User`, `Department` y `DepartmentMembership`.
- Migraciones de Flyway.
- `UserDTO`.
- Servicio de gestión de pertenencias departamentales.
