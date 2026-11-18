## Título

ADR-013: Gestión de pertenencias de usuarios a departamentos

## Estado

Aceptado

## Fecha

2026-11-03 (ampliación aprobada el 04/11/2026).

## Ámbito

Gestión de pertenencias de usuarios a departamentos

## Contexto

Las responsabilidades del mismo usuario varían por departamento y por período.

## Opciones consideradas

Eliminar al finalizar pierde el historial. Reactivar la misma fila sobrescribe el período previo. Los roles globales solos no expresan el departamento.

## Decisión

Una fila por período de pertenencia, con usuario, departamento y rol departamental. La baja fija left_at y el reingreso abre otra fila. Un índice único parcial impide dos altas activas iguales.

## Consecuencias

Se preservan concurrencia, adscripciones simultáneas e historia. Las altas requieren usuario activo y verificado y departamento activo. La API de perfil devuelve pertenencias sin elevarlas a permisos globales. [Dominio](../domain-model.md).
