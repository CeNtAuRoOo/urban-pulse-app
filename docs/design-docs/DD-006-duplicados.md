# DD-016 — Asociación de incidencias con activos del contexto urbano

| Metadato | Valor |
|---|---|
| Estado | Borrador |
| Autoría | Grupo 18 - Nicolai Pirela |
| Fecha / última revisión | 2026-11-23 |
| Iteración | Iteración 2 - Diseño de asociación de incidencias con objetos del contexto urbano |
| Revisores | Mario Fernández Calderón |
| Enlaces | RF16, ADR-018, DD-004-territorio.md, OpenAPI: Contexto urbano y Duplicados |

## 1. Resumen ejecutivo

Se propone ampliar la funcionalidad de incidencias para permitir relacionar los reportes ciudadanos con activos existentes en el catálogo del contexto urbano. El operador podrá seleccionar directamente un activo desde el mapa o recibir sugerencias de activos próximos a la ubicación de la incidencia. Se reutilizará el catálogo espacial existente y su búsqueda por proximidad. La detección de duplicados entre reportes ciudadanos seguirá funcionando de forma independiente, conservando sus criterios actuales de distancia, tiempo y categoría. La propuesta requiere incorporar una asociación persistente entre incidencias y activos urbanos, así como resolver la evolución del identificador del activo y la separación futura del servicio de contexto urbano.

## 2. Contexto y problema

- **Situación actual verificable:**
  - El módulo `duplicates` permite detectar posibles incidencias duplicadas mediante distancia, diferencia temporal y categoría.
  - La distancia máxima actual es de 250 metros y la diferencia temporal máxima es de 72 horas.
  - La puntuación utiliza pesos del 60 % para ubicación, 30 % para tiempo y 10 % para categoría.
  - La confirmación de duplicados es manual y persiste relaciones en `duplicate_links`.
  - Existe el módulo `urban_context`, que consulta el catálogo espacial `cartografia.contexto_urbano`.
  - El catálogo almacena identificadores numéricos locales, origen, categoría, descripción y geometrías PostGIS en EPSG:25830.
  - `GET /api/v1/urban-assets` permite consultar activos por origen y proximidad, con un máximo de 500 resultados.
  - El catálogo comienza vacío y no tiene una ingesta real acreditada.
  - El modelo conceptual de OpenAPI contempla activos con UUID, identificador externo y asociaciones, pero no equivale al modelo actualmente implementado.

- **Problema:** una incidencia puede describir un problema relacionado con un activo urbano conocido, aunque no exista otro reporte ciudadano que pueda considerarse duplicado. La detección actual de duplicados no representa esa relación.

- **Motivación:** aprovechar los datos espaciales disponibles para identificar el elemento urbano afectado y mejorar la trazabilidad y gestión municipal de los reportes.

## 3. Objetivos, no objetivos y alcance

**Objetivos**

- O1. Permitir asociar una incidencia a un activo urbano existente.
- O2. Permitir seleccionar el activo directamente desde el mapa.
- O3. Ofrecer sugerencias de activos próximos utilizando la ubicación de la incidencia y la categoría.
- O4. Conservar separadas las relaciones entre incidencias duplicadas y las asociaciones con activos urbanos.
- O5. Registrar quién confirma la asociación y cuándo se realiza.
- O6. Mantener la compatibilidad con los criterios actuales de RF16.

**No objetivos**

- N1. No crear incidencias ficticias para representar activos urbanos.
- N2. No fusionar ni eliminar reportes ciudadanos.
- N3. No modificar los umbrales actuales de RF16.
- N4. No desarrollar de nuevo el catálogo espacial ya existente.
- N5. No implementar automáticamente en este cambio toda la separación del microservicio de contexto urbano, su ingesta y su base de datos independiente.

**Dentro del alcance:** diseño de la asociación, consulta de activos próximos, selección desde el mapa, persistencia de la relación, autorización, trazabilidad y tratamiento de indisponibilidad.

**Fuera del alcance:** ingesta de datos externos, selección definitiva de proveedores Open Data y AEMET, mantenimiento completo del inventario urbano y despliegue del microservicio independiente.

## 4. Requisitos y drivers arquitectónicos

| ID | Tipo | Necesidad / escenario | Criterio de aceptación y evidencia | Prioridad |
|---|---|---|---|---|
| RF-016-A | Funcional | Asociar una incidencia a un activo urbano existente. | La asociación identifica correctamente la incidencia y el activo. | Alta |
| RF-016-B | Funcional | Seleccionar un activo desde el mapa. | El operador puede seleccionar un activo y confirmar la asociación. | Alta |
| RF-016-C | Funcional | Sugerir activos próximos a una incidencia. | Los resultados respetan los filtros espaciales y de categoría acordados. | Alta |
| RF-016-D | Funcional | Mantener separadas las asociaciones de activos y los duplicados. | La asociación no crea ni modifica registros de `duplicate_links`. | Alta |
| RF-016-E | Funcional | Registrar la trazabilidad de la asociación. | Se conserva el activo relacionado, la incidencia, el usuario y la fecha. | Alta |
| RQ-016-A | Calidad | Tolerar la indisponibilidad temporal del catálogo urbano. | Las operaciones de incidencias y duplicados que no dependen del catálogo siguen funcionando. | Alta |
| RQ-016-B | Calidad | Evitar asociaciones ambiguas. | La interfaz muestra identificador, tipo, categoría y ubicación del activo. | Alta |
| RC-016-A | Restricción | Mantener los criterios actuales de RF16. | Las pruebas de distancia, tiempo y categoría siguen pasando sin cambios. | Alta |
| RC-016-B | Restricción | Respetar el modelo real del catálogo existente. | La integración no presupone que el identificador numérico actual sea un UUID de proveedor. | Alta |

## 5. Arquitectura actual y dependencias

- **C4 Contexto/Contenedores:** consultar `docs/architecture.md` y `docs/design-docs/DD-004-territorio.md`.
- **Flujo actual de duplicados:** el módulo `duplicates` obtiene candidatos de otras incidencias y calcula una puntuación. La confirmación humana crea una relación en `duplicate_links` y registra el evento de auditoría.
- **Flujo actual del contexto urbano:** el módulo `urban_context` consulta `cartografia.contexto_urbano` mediante PostGIS. El endpoint de búsqueda admite coordenadas WGS84 y radio en metros; la geometría almacenada utiliza EPSG:25830.
- **Dependencias:** módulos `incidents`, `duplicates`, `urban_context`, `maps`, persistencia PostgreSQL/PostGIS y futura interfaz de selección de activos.

**Diferencia arquitectónica pendiente:** el código actual integra `urban_context` en el monolito y utiliza la base de datos principal. El requisito académico contempla un microservicio independiente con base de datos propia. La separación deberá tratarse como una evolución arquitectónica y no como una capacidad ya existente.

## 6. Alternativas consideradas

| Alternativa | Descripción | Ventajas | Inconvenientes / riesgos | Evidencia disponible |
|---|---|---|---|---|
| A | Representar los activos urbanos como incidencias y reutilizar `duplicate_links`. | Reutilización parcial del modelo. | Mezcla reportes ciudadanos con elementos físicos; altera el significado de RF16. | No recomendada. |
| B | Crear una asociación independiente entre incidencia y activo urbano. | Mantiene separados los conceptos y conserva la trazabilidad. | Requiere nueva persistencia y contratos. | Propuesta preferida. |
| C | Guardar únicamente las coordenadas del activo seleccionado. | Implementación sencilla y poca dependencia del catálogo posterior. | No conserva una identidad estable ni permite resolver cambios del activo. | No recomendada como solución definitiva. |

**Criterios de comparación:** claridad del dominio, trazabilidad, consistencia, resiliencia, complejidad y compatibilidad futura con la separación del microservicio.

**Elección propuesta:** alternativa B. La incidencia y el activo urbano son entidades diferentes y deben relacionarse sin reutilizar la tabla de duplicados.

## 7. Diseño propuesto

### 7.1. Visión y diagramas C4

- **Contexto (L1):** el operador municipal trabaja desde la aplicación para consultar incidencias y activos urbanos.
- **Contenedores (L2):** en la arquitectura actual, `incidents`, `duplicates` y `urban_context` están integrados en el backend. La arquitectura objetivo deberá contemplar la posible extracción de contexto urbano a un servicio independiente.
- **Componentes (L3):** se propone incorporar un componente de aplicación para consultar activos candidatos y gestionar sus asociaciones, separado del cálculo de duplicados.
- **Secuencia propuesta:**
  1. El operador abre una incidencia.
  2. La aplicación consulta activos próximos a sus coordenadas o permite seleccionarlos directamente en el mapa.
  3. El backend consulta el catálogo y comprueba la existencia del activo.
  4. El operador confirma la asociación.
  5. El sistema persiste la relación y registra el usuario y la fecha.

### 7.2. Interfaces y contratos

Se propone reutilizar `GET /api/v1/urban-assets` para obtener activos cercanos, sin duplicar su funcionalidad.

Se deberán diseñar operaciones adicionales para gestionar asociaciones, por ejemplo:

- `GET /api/v1/incidents/{incidentId}/urban-asset-candidates`: consultar activos candidatos para una incidencia.
- `PUT /api/v1/incidents/{incidentId}/urban-asset`: asociar un activo a una incidencia.
- `DELETE /api/v1/incidents/{incidentId}/urban-asset`: retirar la asociación, si el modelo finalmente admite una única asociación activa.

Estas rutas son propuestas, no contratos implementados.

El contrato de asociación deberá identificar el activo con el identificador que corresponda al modelo vigente. En la primera fase será el ID numérico local del catálogo. Si se extrae el servicio y se adopta el modelo conceptual con UUID y `externalId`, deberá definirse una estrategia de transición.

El contrato de candidatos podrá incluir:

- Identificador del activo.
- Origen (`sourceTable`).
- Nombre y categoría disponibles.
- Geometría o coordenadas adecuadas para el mapa.
- Distancia calculada respecto a la incidencia.
- Método de selección o asociación.

Las operaciones de lectura masiva deberán tener límites y paginación o un límite explícito, manteniendo el comportamiento acotado del catálogo actual.

### 7.3. Modelo y gestión de datos

Se propone una nueva relación independiente de `duplicate_links`, con los siguientes datos conceptuales:

- Identificador de la asociación.
- UUID de la incidencia.
- Identificador del activo urbano.
- Fecha de asociación.
- Usuario que confirmó la asociación.
- Método de asociación: selección explícita o confirmación de una sugerencia.
- Motivo opcional.

La relación debe mantener la identidad de la incidencia y no copiar el activo como una nueva incidencia.

En la primera fase, la referencia al activo utilizará su ID numérico local y deberá acompañarse de la identificación de su origen si fuera necesario para evitar ambigüedades.

Si contexto urbano se extrae posteriormente a una base de datos independiente, no podrá dependerse de una clave foránea PostgreSQL entre ambas bases. Se deberá utilizar una referencia lógica estable y definir cómo se resuelven los activos retirados, actualizados o no disponibles.

También deberá decidirse si se conserva una instantánea de los datos descriptivos del activo para mantener la consulta histórica.

### 7.4. Criterios de detección y sugerencia

**Duplicados entre incidencias:** se conserva el comportamiento actual:

- Distancia máxima: 250 metros.
- Diferencia temporal máxima: 72 horas.
- Peso de ubicación: 60 %.
- Peso temporal: 30 %.
- Peso de categoría: 10 %.
- Máximo de 20 sugerencias.

**Sugerencias de activos urbanos:** se propone:

- Utilizar la ubicación de la incidencia como punto de referencia.
- Reutilizar la consulta espacial de `urban_context`.
- Comparar la categoría de la incidencia con la categoría o tipo del activo, definiendo previamente un mapeo.
- Mostrar la distancia entre la incidencia y el activo.
- Permitir que el operador seleccione directamente un activo desde el mapa.

No se aplicará automáticamente el límite temporal de 72 horas a los activos urbanos estáticos. Si existen datos temporales relevantes en una fuente concreta, se estudiarán como una regla adicional.

La selección explícita del operador debe prevalecer sobre las sugerencias automáticas.

### 7.5. Fallos, concurrencia y resiliencia

- Si el catálogo urbano no está disponible, la creación, consulta y gestión ordinaria de incidencias debe continuar.
- La consulta de sugerencias podrá fallar de forma controlada, indicando que el contexto urbano no está disponible.
- No se confirmará una asociación con un activo cuya identidad no se pueda validar, salvo que se defina expresamente una política de caché y validación diferida.
- Se utilizarán timeouts y reintentos acotados.
- Se evitarán asociaciones duplicadas para una misma incidencia y activo.
- La retirada de un activo del catálogo no deberá borrar automáticamente la asociación histórica.

### 7.6. Seguridad

- Las operaciones de asociación estarán limitadas a los roles autorizados para gestionar incidencias.
- Se comprobará la autorización del usuario sobre la incidencia.
- El identificador del activo deberá validarse en el backend.
- Se registrarán las acciones de asociación y retirada para mantener trazabilidad.
- No se expondrán credenciales de servicios externos en el frontend.

### 7.7. Operación y despliegue

La primera implementación podrá aprovechar la infraestructura PostgreSQL/PostGIS actual. La extracción posterior de contexto urbano deberá diseñarse con su propia base de datos y mecanismos de comunicación.

Se deberán medir la latencia de consulta espacial, los errores del catálogo y el número de asociaciones realizadas. Los datos externos deberán tener una política de actualización y procedencia documentada.

## 8. Decisiones y trade-offs

| Decisión propuesta | Beneficio | Coste / consecuencia | ¿Requiere ADR? |
|---|---|---|---|
| Separar `duplicate_links` de las asociaciones con activos. | Modelo de dominio coherente. | Nueva relación y contratos. | Sí, si se modifica el modelo persistente. |
| Reutilizar el catálogo espacial existente. | Evita duplicar el almacenamiento y las consultas PostGIS. | Dependencia del modelo actual. | No, si se mantiene la arquitectura existente. |
| Usar el ID numérico actual en la primera fase. | Compatible con el catálogo implementado. | Requiere evolución si se adopta el UUID conceptual. | Sí, si se cambia la identidad del activo. |
| Mantener la selección explícita del operador. | Reduce asociaciones incorrectas. | Requiere interacción en el mapa. | No. |
| Separar contexto urbano a futuro. | Independencia de despliegue y persistencia. | Mayor complejidad de integración y resiliencia. | Sí. |

## 9. Plan de implementación y migración

1. Validar con el cliente la necesidad de asociar incidencias a activos urbanos y las reglas de selección.
2. Definir si una incidencia podrá relacionarse con uno o varios activos.
3. Definir la correspondencia entre categorías de incidencias y categorías de activos.
4. Diseñar la relación persistente y los contratos OpenAPI.
5. Implementar consulta de candidatos y selección en el mapa.
6. Implementar confirmación, retirada y trazabilidad de asociaciones.
7. Añadir pruebas de integración, autorización e indisponibilidad del catálogo.
8. Actualizar diagramas y documentación.

**Dependencias:** catálogo espacial, módulo de incidencias, permisos de operador, frontend de mapas y decisión sobre la arquitectura futura de contexto urbano.

**Impacto en versiones anteriores:** RF16 debe conservar su contrato y comportamiento. La asociación de activos se añade como funcionalidad independiente.

## 10. Estrategia de verificación y evaluación

| Requisito | Prueba/experimento | Entorno y datos | Métrica / umbral | Evidencia esperada |
|---|---|---|---|---|
| RF-016-A | Asociar un activo existente a una incidencia. | PostgreSQL/PostGIS de integración. | Asociación recuperable con los identificadores correctos. | Test de integración. |
| RF-016-B | Seleccionar un activo desde el mapa. | Entorno funcional con catálogo de prueba. | El activo seleccionado coincide con el persistido. | Prueba funcional. |
| RF-016-C | Consultar activos cercanos. | Datos espaciales de prueba. | Resultados dentro del radio solicitado y ordenados por distancia. | Tests de consulta espacial. |
| RF-016-D | Crear una asociación y consultar duplicados. | Entorno de integración. | No se modifica `duplicate_links`. | Test de regresión. |
| RF-016-E | Confirmar y retirar una asociación. | Entorno de integración. | Actor y fecha conservados. | Test de auditoría. |
| RQ-016-A | Interrumpir el catálogo urbano. | Servicio o repositorio simulado. | Incidencias y duplicados siguen operativos. | Prueba de resiliencia. |

La integración con el catálogo actual podrá validarse localmente con PostgreSQL/PostGIS. La disponibilidad y calidad de datos de proveedores reales solo podrán verificarse cuando exista una ingesta real.

## 11. Riesgos, hipótesis y cuestiones abiertas

| ID | Tipo | Descripción | Impacto | Acción y responsable |
|---|---|---|---|---|
| H-1 | Hipótesis | Los activos tienen identificadores locales únicos y geometrías utilizables. | Alto | Verificar con el modelo y la fuente de datos. |
| H-2 | Hipótesis | Las categorías de incidencias pueden mapearse a las de activos. | Medio | Definir correspondencias con el cliente. |
| R-1 | Riesgo | Varios activos están próximos a una misma incidencia. | Alto | Mostrar identificadores y permitir selección explícita. |
| R-2 | Riesgo | Los activos cambian o desaparecen del catálogo. | Medio | Definir conservación de asociaciones históricas. |
| R-3 | Riesgo | La extracción futura del microservicio rompe referencias locales. | Alto | Definir identidad estable y migración antes de separarlo. |
| Q-1 | Pregunta | ¿Una incidencia puede asociarse a varios activos? | Alto | Validar con el cliente. |
| Q-2 | Pregunta | ¿Qué radio se utilizará para sugerir activos? | Alto | Acordar umbral y pruebas. |

## 12. Revisión y resultado

- **Comentarios de revisión:** pendientes.
- **Resolución:** pendiente de validación del cliente.
- **Decisiones registradas:** ADR-018 documenta el catálogo espacial existente; las nuevas decisiones de asociación deberán registrarse cuando se acepten.
- **Diferencias observadas tras implementar:** completar después de la implementación.

## Checklist de aprobación

- [ ] Problema y alcance delimitados.
- [ ] Drivers y restricciones documentados.
- [ ] Alternativas comparadas.
- [ ] Diagramas, datos y contratos coherentes.
- [ ] Fallos, seguridad, operación y costes considerados.
- [ ] Criterios de aceptación verificables.
- [ ] Riesgos y cuestiones abiertas explícitos.
- [ ] Revisión técnica realizada.
- [ ] Propuesta validada por el cliente.