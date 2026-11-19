# DD-005 — Microservicio de contexto urbano con actualización periódica de datos.
| Metadato | Valor |
|---|---|
| Estado | Borrador |
| Autoría | Mario Fernández Calderón |
| Fecha / última revisión | 2026-11-19 |
| Iteración |1ª |
| Revisores | Miembros de UrbanPulse y el profesor |
| Enlaces | Issue #30: "Evaluación de alternativas para mejorar el acceso a los datos abiertos de contexto urbano" |

## 1. Resumen ejecutivo

[En 4–6 líneas: problema, solución propuesta, razón principal y principal riesgo.]

## 2. Contexto y problema

- **Situación actual verificable:** [Arquitectura/comportamiento existente y fuentes].
- **Problema:** [Consecuencia observable para usuarios/operación/equipo].
- **Motivación:** [Por qué ahora; dependencia de la iteración].

## 3. Objetivos, no objetivos y alcance

**Objetivos**
- O1. [Resultado funcional o técnico verificable].

**No objetivos**
- N1. [Lo que expresamente NO se abordará].

**Dentro del alcance:** [Servicios, contratos y datos afectados].
**Fuera del alcance:** [Servicios o migraciones excluidas].

## 4. Requisitos y drivers arquitectónicos

| ID | Tipo | Necesidad / escenario | Criterio de aceptación y evidencia | Prioridad |
|---|---|---|---|---|
| RF-... | Funcional | [Comportamiento] | [Prueba observable] | [Alta/Media/Baja] |
| RQ-... | Calidad | [Carga/fallo/condición] | [Métrica + umbral + herramienta] | [...] |
| RC-... | Restricción | [Tecnología, plazo, presupuesto, cumplimiento] | [Cómo se comprueba] | [...] |

> Para requisitos de calidad, describir escenario + condiciones + métrica + umbral + método de validación. Los umbrales deben proceder del enunciado, negociación o medición; no inventarlos como hechos.

## 5. Arquitectura actual y dependencias

- **C4 Contexto/Contenedores:** [Enlace a diagrama vigente].
- **Flujo actual:** [Resumen].
- **Dependencias y restricciones:** [Servicios internos/externos].

## 6. Alternativas consideradas

| Alternativa | Descripción | Ventajas | Inconvenientes / riesgos | Evidencia disponible |
|---|---|---|---|---|
| A | [...] | [...] | [...] | [...] |
| B | [...] | [...] | [...] | [...] |
| C (opcional) | [...] | [...] | [...] | [...] |

**Criterios de comparación:** [Complejidad, coste, disponibilidad, latencia, evolución, seguridad...].
**Elección propuesta y justificación:** [Por qué supera las otras opciones respecto a los drivers prioritarios].

## 7. Diseño propuesto

### 7.1. Visión y diagramas C4

- **Contexto (L1):** [Enlace o «sin cambios»].
- **Contenedores (L2):** [Enlace y diferencias].
- **Componentes (L3):** [Solo si ayuda].
- **Secuencias / flujo de eventos:** [Enlace o descripción].

### 7.2. Interfaces y contratos

[Endpoints, eventos, versiones, esquema JSON, errores, idempotencia, compatibilidad. Referenciar OpenAPI/AsyncAPI cuando proceda.]

### 7.3. Modelo y gestión de datos

[Entidades afectadas, titularidad, consistencia, migraciones, retención, recuperación, privacidad.]

### 7.4. Fallos, concurrencia y resiliencia

[Timeouts, reintentos, duplicados, orden, circuit breakers, degradación, recuperación.]

### 7.5. Seguridad

[Identidad, autorización, secretos, amenazas, auditoría, datos sensibles.]

### 7.6. Operación y despliegue

[Topología, AWS si aplica, IaC, observabilidad, escalado, reversión, costes.]

## 8. Decisiones y trade-offs

| Decisión propuesta | Beneficio | Coste / consecuencia | ¿Requiere ADR? |
|---|---|---|---|
| [Ejemplo] | [...] | [...] | Sí / No |

[Enlazar ADR aceptados, diferenciándolos de propuestas abiertas.]

## 9. Plan de implementación y migración

1. [Cambio incremental y entrega demostrable].
2. [Pruebas y cambio de contrato].
3. [Despliegue controlado, banderas de funcionalidad o transición si procede].
4. [Plan de reversión y limpieza].

**Dependencias:** [...]. **Impacto en versiones anteriores:** [...].

## 10. Estrategia de verificación y evaluación

| Requisito | Prueba/experimento | Entorno y datos | Métrica / umbral | Evidencia esperada |
|---|---|---|---|---|
| RQ-... | [k6, integración, fallo inyectado...] | [...] | [...] | [Informe, trazas, logs, test CI] |

Explicar qué solo puede validarse en cloud real frente a local o simulado.

## 11. Riesgos, hipótesis y cuestiones abiertas

| ID | Tipo | Descripción | Impacto | Acción y responsable |
|---|---|---|---|---|
| H-1 | Hipótesis | [...] | [...] | [Experimento] |
| R-1 | Riesgo | [...] | [...] | [Mitigación] |
| Q-1 | Pregunta | [...] | [...] | [Decisión pendiente] |

## 12. Revisión y resultado

- **Comentarios de revisión:** [Enlaces o resumen de objeciones relevantes].
- **Resolución:** [Aceptado / pendiente / descartado].
- **Decisiones registradas:** [ADR].
- **Diferencias observadas tras implementar:** [Qué cambió respecto al diseño, por qué y dónde se documentó].

## Checklist de aprobación

- [ ] Problema y alcance delimitados.
- [ ] Drivers y restricciones documentados, sin atribuir al enunciado requisitos inexistentes.
- [ ] Al menos dos alternativas comparadas o justificación de por qué no hay alternativas viables.
- [ ] Diagramas, datos y contratos coherentes.
- [ ] Fallos, seguridad, operación y costes considerados en proporción al cambio.
- [ ] Criterios de aceptación verificables y plan de pruebas reproducible.
- [ ] Riesgos y cuestiones abiertas explícitos.
- [ ] Revisión técnica realizada; ADR enlazados cuando corresponde.
