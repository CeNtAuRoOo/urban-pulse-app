# ADR-0017: Mapas provinciales, publicación y privacidad

- Fecha: 2026-11-10.
- Estado: implementado en `feature/implementacion-mapas`.
- Alcance: backend Mapas; no modifica alta, edición, validación, asignación, títulos
  públicos, evidencias ni frontend.

## Decisión

Crear `es.grupo18.urbanpulse.maps` con API, orquestación, políticas de visibilidad y
título, DTO propios y repositorio JDBC de proyecciones. `GET /api/v1/map/incidents`
exige JWT, CITIZEN base, actividad y correo verificado. Reutiliza la cadena de seguridad
sin modificarla. La política vuelve a leer `UserRepository.findSecurityState` y la
consulta se ejecuta en la misma transacción read-only REPEATABLE_READ, con permisos
y datos en una instantánea coherente. Una revocación confirmada antes de la siguiente
petición se aplica usando incluso el mismo JWT; un cambio posterior al comienzo de
la instantánea no cancela retroactivamente una respuesta en curso.

Únicamente `user_roles` OPERATOR/ADMIN concede acceso global y coordenadas exactas.
Los roles departamentales conservan las reglas del ciudadano: incidencias propias
exactas y ajenas solo si existe una revisión VALIDATE. No se infiere publicación del
estado; una validación histórica sobrevive a REJECTED, RESOLVED o CLOSED posteriores.
`EXISTS` impide duplicados con varias revisiones. Las dos ramas SQL, exacta y pública,
son disjuntas por titularidad y se unen con UNION ALL.

## Geografía y privacidad

El [contorno IGN versionado](../../backend/src/main/resources/maps/README.md) representa
toda la provincia, no un rectángulo ni solo la capital. La inclusión de la ubicación
original en ese contorno se evalúa en PostgreSQL, con fronteras inclusivas.

Se usa la proyección cilíndrica equidistante local definida en el recurso y una
cuadrícula fija de 100 m. El punto público es su centro. Si el centro queda fuera
del contorno (costa/frontera), se proyecta **el centro**, no el punto privado, al
segmento oficial más próximo, con desempate por orden del anillo. Toda ubicación
de una misma celda obtiene el mismo punto público; no depende del UUID, usuario,
viewport, estado, categoría o zoom. Así no existe un oráculo de posición privada
mediante rectángulos diminutos. La pertenencia a la provincia sigue siendo una
condición administrativa sobre la ubicación original.

Los originales permanecen en `incidents`; no se añade ni sobrescribe ninguna
coordenada ni columna de título. La rama pública filtra por el punto aproximado
y solo transporta esa ubicación a la proyección JDBC. Los grupos se
construyen después de ese filtrado. No se registran coordenadas ni cuerpos en logs.

No es anonimización: la celda, el historial, el contexto y las zonas costeras pueden
reducir considerablemente el área plausible. Tampoco se garantiza una distancia
mínima de 100 m entre punto original y público: 100 m es el lado de la celda. Los
marcadores públicos pueden coincidir y no deben desplazarse usando datos privados.

### Reutilización del punto público por celda (2026-11-15)

La consulta materializa candidatos autorizados y filtrados por estado/categoría,
selecciona un representante por celda de privacidad de 100 m y materializa su punto
público. Evalúa `map_public_point_v1` una sola vez por celda candidata y reutiliza
el resultado para todas sus incidencias. La función permanece intacta; la antigua V5 se integra en V1 en el merge del 16/11/2026; no se añaden tablas persistentes ni cachés entre peticiones. La rama
exacta de autores y usuarios globales conserva su comportamiento.

Para acotar candidatos se usa el índice GiST de ubicación exacta con el bbox
ampliado 150 m por eje en la proyección local. Es una preselección conservadora:
original a centro de celda <= 50√2 m; si el centro está fuera, la frontera más
próxima no está más lejos que el original interior. El desplazamiento completo
es <= 100√2 m (141,43 m), con margen para tolerancias numéricas. La inclusión
definitiva se decide **siempre por el punto público y el bbox solicitado**, antes
de agrupar y contar. La preselección no excluye posiciones públicas visibles aunque
su original esté fuera del viewport. Los CTE `MATERIALIZED` evitan reevaluaciones
por incidencia. El índice público, ahora integrado en V1, se conserva, incluido su coste de escritura.

## Agrupación y costes

La cuadrícula de grupos está anclada al mismo origen y tiene lado
`100 * 2^(17-zoom)` m para zoom 0..17. Son celdas anidadas. Desde zoom 18 se devuelve
un marcador por UUID, incluso si varias posiciones son idénticas. Un singleton de
cualquier zoom se representa como INCIDENT; el resto como CLUSTER. El identificador
`g1:zoom:columna:fila` es determinista. Conteo y pertenencia corresponden únicamente
al viewport/filtros/usuario actual, por lo que pueden cambiar al desplazar el mapa.
La posición es la del miembro con menor UUID lexicográfico; permanece dentro de
provincia y viewport y nunca promedia posiciones ocultas.

SQL aplica visibilidad, filtro espacial, estado y categoría antes de agrupar. Devuelve
como máximo 1001 filas de proyección (1000 + centinela); nunca entidades JPA completas.
La fila centinela produce 422 MAP_RESULT_LIMIT y no una respuesta truncada. `count`
es el número de gráficos y `totalIncidents` la suma de sus incidencias. El trabajo
interno de PostgreSQL sí depende del volumen visible: tiene timeout 5 s y puede usar
temporales para agregar. Cuota en memoria de 60 consultas por usuario y reposición
60/minuto, compartiendo el limitador acotado existente. No es una cuota distribuida.

El viewport debe tener amplitud por eje <= `min(360,1440/2^zoom)` grados. Se permiten
vistas amplias a zoom bajo, incluso el mundo, pero solo se consideran incidencias
provinciales. La cuadrícula y los límites evitan una respuesta individual ilimitada.

## PostgreSQL nativo en Mapas (independiente de PostGIS)

Los tipos nativos point, polygon, lseg y box permiten contención y proyección a la
frontera, y GiST sobre point permite filtrar tanto ubicaciones exactas como públicas.
**Este módulo `maps` no depende de PostGIS ni de JTS**: sus funciones e índices se
implementan con PostgreSQL nativo.

La base de datos del proyecto incorpora PostGIS por una decisión separada del módulo
`urban_context`, mediante `V1__initial_schema.sql` para el catálogo
`cartografia.contexto_urbano`. Esa extensión no se usa en el endpoint `/map/incidents`;
véase [PostGIS y contexto urbano](../postgis-urban-context.md). Los índices parciales
solo incluyen ubicaciones dentro de Málaga. Un índice parcial de incident_reviews
acelera EXISTS VALIDATE. Las funciones son IMMUTABLE y versionadas porque su contorno
y proyección son constantes. No acceden a tablas externas ni redes.
Las funciones compuestas fijan el search_path del esquema de instalación; funcionan
también al construir índices sobre datos existentes, cuando PostgreSQL restringe
el search_path del proceso, y cuando se instalan en un esquema distinto de public.

Costes: 12.339 vértices; comprobar contención y aproximar celdas costeras cuesta más
que un simple bounding box. Los índices trasladan parte del cálculo a la escritura
de coordenadas, sin cambiar reglas del módulo Incidencias. Para grandes volúmenes
se debe medir rendimiento y considerar optimizaciones del polígono; PostGIS no se
introduce como dependencia de este módulo aunque esté habilitado en otra parte del
proyecto; no se
promete rendimiento ilimitado. Los timeouts nunca deben convertirse en mapas vacíos.

**Antecedente histórico de C `50d7ed7`, sustituido por V1 única:** tras integrar Tramitación de main (16/11/2026), la secuencia desde vacío es V1 → V2 → V5.
V1 consolida Evidencias, V2**processing_phase_one.sql incorpora información adicional
y restricciones de revisión; V5 conserva íntegramente la geografía. No se restauran
las antiguas V2**incidents.sql/V3 ni se crea V4. Se prueba V5 sobre V1/V2 con datos.
Para bases con V1/V5 ya aplicadas, Flyway rechaza V2 pendiente por defecto; véase
el procedimiento excepcional de [ADR-0006](0006-flyway.md#v2-recibida-después-de-v5).
No se cambia outOfOrder globalmente ni se accede a Neon.

Este ADR pasa de 0015 a 0017 al integrar los ADR-0015 de Evidencias y ADR-0016 de CI.

## Contrato con Tramitación

1. Tramitación conserva una fila `incident_reviews` con `decision=VALIDATE`, autor
   municipal real y fecha, confirmada en la misma transacción que valida. Mapas
   confía en ese historial autoritativo, no en permisos actuales del revisor ni en
   campos enviados por el cliente. Revocar posteriormente el rol del revisor no
   borra una validación histórica. REQUEST_INFORMATION/REJECT no publican.
2. Tramitación será propietaria del título aprobado, su versión/fecha/autor de
   aprobación y de su relación con la versión del título fuente. Debe proporcionar
   una proyección legible por lote `incidentId -> approvedPublicTitle` que solo
   devuelva texto cuya aprobación siga vigente. Null/ausente significa fallback.
3. Adaptar `MapTitlePolicy` y la proyección del repositorio (o un único lote por
   respuesta) para consumir esa información. No introducir N+1 ni un fallback a
   `Incident.title`. La nueva política solo podrá usar título aprobado cuando exista
   validación efectiva y aprobación vigente; todo lo demás conserva `Incidencia UUID`.
4. Publicación y aprobación son hechos distintos. Aprobar un título sin VALIDATE
   no publica. No se implementa retirada retroactiva de una validación; si se acuerda
   esa función, definir su nuevo predicado de publicación y pruebas antes de cambiarlo.
5. El detalle privado conserva su autorización actual. Mapas no crea un detalle
   público completo ni amplía permisos departamentales.

## Verificación

Pruebas unitarias de parámetros, permisos y límites; pruebas PostgreSQL con JWT
firmado, roles y pertenencias reales, revisiones, límites geográficos, privacidad,
agrupaciones y JSON. Ver [guía de Mapas](../maps-backend.md) para comandos y resultados.

## Consolidación actual (16/11/2026)

Esquema inicial consolidado V1: 16 tablas de dominio, cinco roles y siete funciones geográficas; sin semillas de usuarios ni catálogos. Solo para bases vacías sin historial Flyway previo, conforme a la premisa del responsable. No acredita aplicación en Neon. Geometría, políticas, costes e índices conservados sin cambios. El generador verifica exclusivamente la región marcada de V1; no existe V5 ejecutable. [Evidencia de integración](../integration-reconciliation.md).

## Alternativas consideradas y criterios de revisión

| Alternativa                                               | Decisión y atributo prioritario                                               | Consecuencia / cuándo reconsiderar                                                                                     |
| --------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL nativo frente a PostGIS                        | Mantener nativo: sencillez operativa en el ámbito provincial medido           | Contorno de 12.339 vértices y coste de índices; reconsiderar PostGIS/subdivisión con medición de lecturas y escrituras |
| Cálculo por consulta frente a caché persistente           | Instantánea coherente y permisos actuales; materialización solo dentro de SQL | Recalcula entre peticiones; cachear exigiría invalidación, ámbito de autorización y pruebas de privacidad              |
| Cuadrícula pública fija frente a desplazamiento aleatorio | Estabilidad y ausencia de filtración por promediar consultas repetidas        | Reduce precisión, no anonimiza; conserva el ajuste del centro de celda en costa/frontera                               |

La incorporación de PostGIS a Contexto Urbano no migra las funciones ni índices nativos de Mapas. Las alternativas anteriores se conservan para futuras decisiones basadas en mediciones.

Pendientes previos a producción: carga concurrente representativa, p95/p99, errores, coste de escritura/índices y cuotas distribuidas si hay varias instancias. No son resultados acreditados por pruebas sintéticas aisladas. La retirada de publicación y los títulos aprobados requieren decisiones y contratos propios; se mantiene VALIDATE histórico.

**Validación preparada (17/11/2026):** la [batería k6](../../k6/README.md) dispone de scripts de lectura de Mapas con cuota delimitada y checks de privacidad. Están implementados y validados offline en esta integración; las mediciones anteriores siguen pendientes. No cambia la decisión arquitectónica ni acredita capacidad concurrente.
