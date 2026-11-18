## Título

ADR-012: Gestión de usuarios, perfiles y administración

## Estado

Aceptado

## Fecha

2026-11-03 (ampliación aprobada el 04/11/2026).

## Ámbito

Gestión de usuarios, perfiles y administración

## Contexto

Separar edición del perfil propio y administración de otras cuentas.

## Opciones consideradas

El registro original distingue perfil y administración. No consta una evaluación comparativa formal.

## Decisión

El titular edita los campos permitidos del perfil. ADMIN consulta y administra cuentas mediante DTO específicos. Se normalizan nombres Unicode y se excluyen hash, códigos y metadatos de seguridad.

## Consecuencias

Las actualizaciones administrativas coordinan bloqueos y protegen al último administrador activo verificado, incluso con solicitudes concurrentes. [Contrato de Usuarios](../api/openapi/paths/usuarios.yaml).
