
# DD-01 — Microservicio de contexto urbano con actualización periódica de datos.
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

- **Situación actual verificable:** En el momento actual tenemos un módulo de contexto urbano basado en datos descargados del portal de Datos Abiertos del Ayuntamiento de Málaga. Estos datos no se actualizan periódicamente, pero han sido parcialmente normalizados y unificados en una misma tabla en la misma base de datos que el resto del sistema. Este modulo de contexto urbano utiliza PostGis para el almacenamiento de datos de geolocalización que brindan utilidad a estos datos en el sistema.
- **Problema:** La información de contexto urbano no es actualizada correctamente puesto que no se actualiza. Además, la base de datos presenta gran cantidad de tablas para diferentes tipos de objeto urbano que aglomeran la base de datos en el servicio de incidencias. 
- **Motivación:** Se realiza ahora puesto que ya se empieza a tener una implementación algo más completa del sistema y es momento de hacerlo escalable. Además esta necesidad de separación en microservicio se ha visto evidente tras la implementación del módulo de contexto urbano en el mismo servicio que incidencias. Urban Pulse trata de brindar el mejor servicio posible al usuario y es por ello que se está ante una oportunidad para mejorar la calidad de los datos proporcionados y la latencia de envío de estos.

## 3. Objetivos, no objetivos y alcance

**Objetivos**
- O1: Diseñar una arquitectura nueva para la gestión de los datos de contexto urbano en un nuevo microservicio separado de la funcionalidad principal de incidencias de UrbanPulse.
- O2: Implementar un sistema que mantenga los datos de contexto urbano del servidor actualizados correctamente de forma periódica y que sea lo suficientemente tolerante para resistir cambios en el formato de los datos o la presencia de estos.
- O3: Evaluar el sistema de comunicación a implementar entre el sistema de incidencias y el nuevo microservicio de contexto urbano.
- O4: Analizar la opción de separar las bases de datos para mantener el contexto urbano en una base de datos separada a la de incidencias, la principal.
- O5: Finalizar la implementación de un mejor módulo de contexto urbano puesto que el actual no cuenta con algunas funcionalidades como la separación por barrios o distritos de la ciudad malacitana.

**No objetivos**
- N1: No se aborda una revisión de los datos a utilizar para la aplicación. Los datos seguirán siendos aquellos brindados por Datos Abiertos del Ayuntamiento de Málaga a menos que se indique lo contrario por alguna situación excepcional.
- N2: No se aborda con este documento de diseño una modificación al "frontend" de la aplicación. 
- N3: No se aborda un sistema de modificación por parte de administradores de los datos en contexto urbano a menos que esta sea editando directamente los valores en las tablas de la base de datos puesto que no queda dentro del alcance de este documento. Se tratará de asegurar que los datos estén normalizados correctamente.

**Dentro del alcance:** Quedan alteradas las migraciones de flyway para la base de datos actual que tienen relación con contexto urbano. También queda alterada la base de datos actual puesto que los datos de contexto urbano serían migrados a otra alternativa o, en caso de elegir el mismo tipo de base de datos, a simplemente una base de datos nueva.  Se encuentra a su vez dentro del alcance la creación  del nuevo microservicio y la implementación de los nuevos protocolos de comunicación y la extinción de los antiguos. 
**Fuera del alcance:** Fuera del alcance queda el propio sistema de incidencias y la selección de los datos abiertos. Debe tenerse en cuenta también lo ya mencionado en el "no objetivo" número 2 y en el número 3.

## 4. Requisitos y drivers arquitectónicos

| ID | Tipo | Necesidad / escenario | Criterio de aceptación y evidencia | Prioridad |
|---|---|---|---|---|
| RF-01 | Funcional | Brindar al servicio del sistema un nuevo microservicio independiente para gestionar los datos de contexto urbano. | El servicio debe poder ejecutarse de forma independiente al resto del sistema. (Comprobable mediante una prueba de integración para comprobar la conexión con la base de datos para el contexto urbano. | Alta |
| RF-02 | Funcional | El sistema deberá poder obtener los datos de contexto urbano de los proveedores externos deseados y almacenar los datos en la base de datos. | El sistema puede descargar los datos del proveedor que se haya decidido y los datos serán normalizados correctamente. (Comprobable mediante una prueba de integración y pruebas de transformación) | Alta |
| RF-03 | Funcional | El sistema permitirá el acceso a la información de contexto urbano por otros servicios de la aplicación. Específicamente la conexión con el sistema de incidencias. | El servicio de incidencias puede consultar la información de contexto urbano a través de un protocolo de comunicación definido y documentado. (Comprobable mediante una prueba de integración) | Media |
| RF-04 | Funcional | El sistema será capaz de gestionar datos de geolocalización puesto que los datos descargados de contexto urbano contendrán información de geometría.| El sistema realiza la correcta gestión y traducción de formatos de los datos de geometría y localización, como podría ser la correcta gestión de datos "Geometry" de PostGis, como en la implementación actual de la aplicación, o  la traducción de formatos de, por ejemplo, EPSG:4326 a EPSG:25830. (Comprobable mediante pruebas geoespaciales: "geospatial testing") | Media |
| RQ-01 | Resiliencia | El sistema será capaz de continuar su funcionamiento normal aunque reciba datos con campos inesperados o no pueda descargar ciertos archivos. Este funcionamiento quedará dentro de lo razonablemente posible. | El sistema realiza la descarga y normalización de los datos tratando correctamente cualquier  situación excepcional. (Comprobable mediante pruebas de integración y de transformación que utilicen datos con campos inesperados o fallos simulados de descarga) | Baja |
| RQ-02 | Mantenibilidad |Las conexiones del microservicio de contexto urbano deberán ser propiamente documentadas y ser simples de entender y utilizar. | Se comprobará mediante la documentación de la interfaz. | Media |
| RQ-03 | Rendimiento |El microservicio deberá poder responder a las peticiones rápidamente. | Se comprobará mediante el uso de una serie de métricas base que serán probadas también al final. (BORRADOR: FALTA CALCULO DE RENDIMIENTO PARA EL BACKEND) | Media |
| RC-01 | Restricción | Mantener la compatibilidad con los datos geoespaciales de los datos abiertos del Ayuntamiento de Málaga. | Se comprobará mediante los tests geoespaciales mencionados previamente. Además, habrá que comprobar efectivamente que el sistema permite consultar y almacenar estos datos. | Alta |
| RC-02 | Restricción de Alcance | No se modificará el frontend. | Se comprobará que la implementación no requiere cambios en los components del frontend ni se realizen "commits" en esa parte de la aplicación. | Baja 
| RC-03 | Restricción | Las modificaciones de la base de datos deberán ser reproducibles en los procesos de normalización de los datos descargados. | Se comprobará con los tests de integración mencionados en los requisitos funcionales. | Alta |

## 5. Arquitectura actual y dependencias

- **C4 Contexto/Contenedores:** https://github.com/CeNtAuRoOo/urban-pulse-app/blob/main/docs/diagrams/images
- **Flujo actual:** Se obtienen los datos y estos son normalizados
- **Dependencias y restricciones:** Se depende del servicio provisto por el Ayuntamiento de Málaga y su portal de Datos Abiertos.

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

## 12. Revisión y resultado»

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
