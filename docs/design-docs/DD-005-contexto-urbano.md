

# DD-05 — Microservicio de contexto urbano con actualización periódica de datos.
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
| RF-05 | Funcional | El sistema será capaz de dar servicio al usuario acerca de la información disponible de contexto urbano aunque otros servicios no funcionen correctamente siempre y cuando sea posible la autenticación por parte del usuario y se mantenga la seguridad del servicio.| Se comprobará que el usuario puede consultar datos de contexto urbano incluso si otros servicios no están en funcionamiento. | Baja |
| RQ-01 | Resiliencia | El sistema será capaz de continuar su funcionamiento normal aunque reciba datos con campos inesperados o no pueda descargar ciertos archivos. Este funcionamiento quedará dentro de lo razonablemente posible. | El sistema realiza la descarga y normalización de los datos tratando correctamente cualquier  situación excepcional. (Comprobable mediante pruebas de integración y de transformación que utilicen datos con campos inesperados o fallos simulados de descarga) | Baja |
| RQ-02 | Mantenibilidad |Las conexiones del microservicio de contexto urbano deberán ser propiamente documentadas y ser simples de entender y utilizar. | Se comprobará mediante la documentación de la interfaz. | Media |
| RQ-03 | Rendimiento |El microservicio deberá poder responder a las peticiones rápidamente. | Se comprobará mediante el uso de una serie de métricas base que serán probadas también al final. (BORRADOR: FALTA CALCULO DE RENDIMIENTO PARA EL BACKEND) | Media |
| RC-01 | Restricción | Mantener la compatibilidad con los datos geoespaciales de los datos abiertos del Ayuntamiento de Málaga. | Se comprobará mediante los tests geoespaciales mencionados previamente. Además, habrá que comprobar efectivamente que el sistema permite consultar y almacenar estos datos. | Alta |
| RC-02 | Restricción de Alcance | No se modificará el frontend. | Se comprobará que la implementación no requiere cambios en los components del frontend ni se realizen "commits" en esa parte de la aplicación. | Baja 
| RC-03 | Restricción | Las modificaciones de la base de datos deberán ser reproducibles en los procesos de normalización de los datos descargados. | Se comprobará con los tests de integración mencionados en los requisitos funcionales. | Alta |

## 5. Arquitectura actual y dependencias

- **C4 Contexto/Contenedores:** https://github.com/CeNtAuRoOo/urban-pulse-app/blob/main/docs/diagrams/images
- **Flujo actual:** Se obtienen los datos y estos son normalizados. Tras esta normalización se almacenan en la base de datos general de incidencias. De esta manera cuando un usuario solicita la información el sistema de incidencias consulta la base de datos de incidencia y le entrega al usuario como respuesta la información de contexto urbano.
- **Dependencias y restricciones:** Se depende del servicio provisto por el Ayuntamiento de Málaga y su portal de Datos Abiertos.

## 6. Alternativas consideradas

 

| Alternativas de protocolos de comunicación | Descripción | Ventajas | Inconvenientes / riesgos | Evidencia disponible |
|---|---|---|---|---|
|Implementación híbrida de gRPC y REST | Se implementaría un protocolo de comunicación entre el servicio de incidencias y el servicio de contexto urbano mediante gRPC, pero se implementaría también un endpoint en el servicio de contexto urbano para las llamadas que no estén relacionadas con el servicio de incidencias. Este endpoint estaría implementado mediante REST y lo usaría directamente el usuario. | Optimiza el sistema puesto que las llamadas no tendrían que pasar por el sistema de incidencias a menos que tengan que ver con este y también mejoraría la comunicación interna entre servicios. | Presenta posiblemente un riesgo de seguridad puesto que el usuario tendría un servicio más expuesto a este. Además presenta mayor complejidad que otras opciones puesto que requiere implementación de tanto gRPC como de REST. | Los requisitos RF-03 y RQ-03 señalan que el sistema deberá ser eficiente y otros servicios deberán poder acceder a los datos de contexto urbano. El RF-05 urge al sistema a permitir un cierto nivel de funcionalidad para el usuario incluso si otros sistemas no están en funcionamiento.  |
| Implementación de gRPC | Se implementaría un protocolo de comunicación entre el servicio de incidencias y el servicio de contexto urbano que utilizase gRPC. Todos los traspasos de datos de contexto urbano se realizarían por esta vía. | La principal ventaja respecto a las implementaciones híbridas es la simplicidad. Además, es una perfecta oportunidad para aprender a utilizar este protocolo. | Los principales inconvenientes son la mayor complejidad de implementación respecto a mantener el uso de REST Api y el desconocimiento del equipo de desarrollo acerca de gRPC. Es también un inconveniente que toda solicitud de datos de contexto urbano debería pasar a través del servicio de incidencias, incluso si no tiene relación con incidencias. | Por el RF-05 si no mantenemos una vía directa para el usuario este no podría acceder a la información si servicios como el de incidencia tienen problemas.  |
| Implementación híbrida de connectRPC y REST o solamente de connectRPC | La implementación sería la misma que la implementación híbrida de gRPC y de REST, pero utilizaría connectRPC en vez de gRPC.| Utilizaría un protocolo de comunicación más moderno de más facilidad de uso para el frontend que soporta más funcionalidades Web. | ConnectRPC no tiene tanta trayectoria de desarrollo como gRPC por lo que tengo entendido.  | Puede ser interesante para el RQ-02 puesto que se entendería mejor el sistema y sería posiblemente más mantenible. |
| Mantener la implementación únicamente mediante REST |Se sigue utilizando REST para las comunicaciones entre servicios. | Facilidad de implementación. | No obtendríamos ninguna mejora en eficiencia y tendríamos que usar REST entre servicios. | Tal vez no cumple correctamente con el RQ-03 de rendimiento. |

| Alternativas de tecnologías para la implementación | Descripción | Ventajas | Inconvenientes / riesgos | Evidencia disponible |
|---|---|---|---|---|
|Implementación mediante Python para la descarga de los datos y C++ para la implementación de la lógica del servicio. | Se utilizaría Python para obtener los datos del portal de Datos Abiertos del Ayuntamiento de Málaga y relaizar su procesamiento inicial y su normalización e inserción en la base de datos elegida. La lógica del microservicio se dejaría a una implementación en C++ con interfaces de comunicación. |Python es un lenguaje ideal para el manejo de datos y en este caso es perfecto para este tipo de tareas por su especialización en descarga, transformación y procesamiento. Además C++ nos brinda un gran rendimiento y un control quirúrgico de la ejecución del servicio. | La combinación de los dos lenguajes trae una gran complejidad para el proyecto y C++ es mucho más complejo que otras opciones. Además realmente C++ podría no ser de gran utilidad puesto que no se requieren muchas tareas de procesamiento en el propio microservicio. | RQ-03 señala que el rendimiento es importante por lo que considerar C++ no es una locura. Especialmente cuando el rendimiento base del que partimos ya era bastante bueno y mejorarlo puede resultar difícil. RF-02 justifica que utilicemos Python para descarga. |
| Implementación mediante Python para la descarga de los datos y Java con Spring Boot para la implementación de la lógica del servicio. | Se utilizaría Python para lo mencionado en la anterior propuesta, pero se mantiene Spring Boot para las interfaces de comunicación y la lógica del microservicio. | La arquitectura de Spring Boot es perfecta para desarrollar microservicios y la programación orientada a objetos con Java es útil para manejar los tipos de contexto urbano. | La utilización de dos lenguajes implica mantener los dos entornos diferentes funcionando. Además, Spring Boot es algo más pesado que otras alternativas. | RF-01 y RQ-02 favorecen la arquitectura de esta forma modular. |
| Implementación completa mediante Python | Se implementarían también las interfaces de comunicación mediante Python con librerías para el procesamiento de los datos de geolocalización. | Permite un único lenguaje y además Python es perfecto para el manejo de los datos. | El rendimiento de Python es inferior al de otros lenguajes ya mencionados. | Deberá comprobarse el rendimiento nuevo para comprobar si se cumple el RQ-03. Se debe mencionar que RQ-02 favorece la simplicidad documental. |

| Alternativas para el almacenamiento de los datos de contexto urbano | Descripción | Ventajas | Inconvenientes / riesgos | Evidencia disponible |
|---|---|---|---|---|
| Uso de PostGIS y JSONB | Se implementa una base de datos relacional con propiedades específicas para cada tipo de contexto urbano almacenadas en formato JSON. | PostGIS incorpora muchas funcionalidades de geometría que ya se utilizan en el sistema actual. Además el uso de JSONB ayudaría   a reducir la cantidad de tablas introduciendo las propiedades específicas como elementos del JSONB. | JSONB no tendría un esquema de columnas estricto por lo que puede ser más difícil de utilizar estas propiedades o parámetros y la lectura de estos es más complicada. Además Postgre con PostGIS es más pesado. | Importante para la RC-01 puesto que los datos del Ayuntamiento de Málaga se pueden cargar fácilmente en PostGIS. También influye la RF-04 para la gestión de datos de geometría. |
| Uso de PostGIS | Se utiliza una base de datos relacional con tablas específicas para cada tipo de contexto urbano y una tabla completa con claves foráneas a las otras. | Rígida de uso y permite las funcionalidades de geometría de PostGIS. | Creación de muchas tablas que en caso de cambio del formato de los datos puede ser un problema. Además muchos de los campos son de muy reducida utilidad en la aplicación por lo que la utilidad de muchas de estas subpropiedades es debatible. | Se cumple la RC-01 perfectamente, pero el RQ-01 de resiliencia es muy difícil de cumplir puesto que las tablas son muy sensibles a cambios de propiedades. |
| Uso de MongoDB | Crear tablas documentales para el contexto urbano. | Gran flexibilidad para los atributos y gran capacidad de adaptación a cambios. | Difícil de utilizar cuando se requiere una normalización estricta y habría que buscar una solución para almacenar los valores de geometría. | El RF-04 y el RQ-01 de resiliencia complican el uso de MongoDB ya que se necesitan funciones de geometría y se indica que se necesita una normalización de los datos igualmente.  |

**Criterios de comparación:** Los principales criterios de comparación serán la resiliencia, la latencia y la complejidad de implementación. Lo más prioritario es que el sistema responda rápidamente y trabaje con datos dispares correctamente. También se tendrá en cuenta la integración del frontend y el resto de microservicios con el microservicio de contexto urbano y la disponibilidad de este.
**Elección propuesta y justificación:** Tras haber considerado las diversas opciones presentadas en aqueste documento se ha procedido a la elección de la siguiente opción:

Se desarrollará un microservicio independiente al servicio principal de incidencias. Este microservicio se centrará en el manejo y obtención de los datos de contexto urbano y la lógica de este se implementará mediante Java y Spring Boot. Además, se utilizará un componente para el proceso de obtención, normalización e inserción en la base de datos de la información de contexto urbano mediante Python.
El almacenamiento en dicha base de datos se realizará utilizando PostGIS con JSONB para las propiedades variables del conjunto de los datos.
Para la comunicación y distribución de estos datos para la utilización de estos se implementará una arquitectura híbrida que utilice REST para el acceso de parte directa desde el frontend a la información de contexto urbano y el uso de gRPC para la comunicación interna entre servicios.
Esta propuesta está determinada por la búsqueda de un equilibrio entre eficiencia y complejidad de desarrollo, pero especialmente está fuertemente marcada por una apuesta por la resiliencia y la disponibilidad de la aplicación.

## 7. Diseño propuesto

### 7.1. Visión y diagramas C4

- **Contexto (L1):** Sin cambios.
- **Contenedores (L2):** Se incluye el contenedor diferenciado de microservicio de contexto urbano: (https://github.com/CeNtAuRoOo/urban-pulse-app/blob/documentacion-design-document-contexto-urbano/docs/diagrams/images/DD-005-propuesta-c4-containers.svg)
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
