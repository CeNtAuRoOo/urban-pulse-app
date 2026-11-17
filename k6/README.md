# Pruebas de rendimiento con Grafana k6

**Estado: scripts implementados y comprobados offline; aún no ejecutados con k6 real. No hay mediciones k6, informes HTML ni capacidad acreditada.** La batería se prepara para un backend y una base de pruebas autorizados y aislados. No es una certificación de rendimiento ni sustituye las pruebas funcionales Java/MVC/PostgreSQL.

Se adapta el _Tutorial práctico de Grafana k6_ de Eduardo Guzmán de los Riscos (UMA, 17 páginas) al código de UrbanPulse: `/api/v1`, JWT verificado, paginación desde 1, alta con coordenadas y PATCH con ETag fuerte opaca. El ejemplo docente `/api/incidents` y sus cifras no constituyen un contrato ni un SLO del proyecto.

## Alcance contrastado

Revisión de referencia: `d3169cf0141408787e509ae2d71ebae4e445b8f4`, OpenAPI 0.8.0: **57 rutas, 77 operaciones referenciadas, 47 implementadas y 30 futuras**. Los ficheros modulares pueden contener diseños no referenciados; el inventario se obtiene desde [base-openapi.yaml](../docs/api/openapi/base-openapi.yaml), no contando todos los YAML indiscriminadamente.

Se contrastaron controladores, servicios, DTO, seguridad y configuración con el contrato modular y las suites `IncidentPostgresIT`, `ProcessingPostgresIT`, `MapPostgresIT`, `DuplicatePostgresIT`, `AttachmentServicePostgresIT`, autenticación y catálogos. Java 25/Spring Boot 4.1.2, PostgreSQL 18 y Hikari máximo 5 conexiones describen el entorno actual; cinco conexiones no equivalen a cinco usuarios ni garantizan capacidad. [Inventario completo del producto](../docs/integration-reconciliation.md).

La estructura separa seis ejecutables en `scenarios/`, configuración, autenticación, métricas, validadores, perfiles y recorridos en `lib/`, y [preparación de fixtures](fixtures/README.md). `read.js` comparte el recorrido entre cuatro perfiles para evitar duplicar scripts que solo cambian el patrón temporal.

## Matriz de cobertura

Todas las rutas de las tablas son relativas a `/api/v1`. Todos los actores requieren cuenta activa, correo verificado y CITIZEN base; OPERATOR/ADMIN son permisos globales adicionales.

| Archivo / escenario                                  | Tipo, operaciones y rol                                                                                                            | Fixtures                                                                               | Carga por defecto y activación                                                                                                                           | Checks y umbrales                                                                                                                     | Riesgo y estado                                                                                                                  |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `scenarios/smoke-read.js` / `smoke`                  | Smoke ciudadano o personal: perfil, catálogos, propias, detalle/historial, resultado público y mapa; listado general solo personal | Token del `ACTOR` y su `*_INCIDENT_ID`; viewport válido                                | 1 VU, 1 iteración, máximo 3 min; 8 peticiones nominales ciudadano / 9 personal                                                                           | HTTP 200, JSON, rol, titularidad, página, ID concreto, no-store, ETag, privacidad de mapa; cero fallos HTTP/semánticos y 100 % checks | Una consulta de mapa; sin mutaciones. **Script implementado; aún no ejecutado con k6 real**                                      |
| `scenarios/read.js` / `load`                         | Load de lecturas ponderadas; CITIZEN/OPERATOR/ADMIN                                                                                | Token, `*_INCIDENT_ID`, dataset S/M/L preparado                                        | 3 VUs constantes, 60 s, pausa 1 s; `READ_PROFILE=load`                                                                                                   | Página, filtros, detalle propio/autorizado, historial, catálogos; p95 por operación, errores y checks                                 | Sin Mapas/S3/login ni escrituras. **Script implementado; aún no ejecutado con k6 real**                                          |
| `scenarios/read.js` / `stress`                       | Stress incremental de las mismas lecturas                                                                                          | Igual que load                                                                         | `READ_PROFILE=stress`, `K6_ALLOW_HEAVY=1`; rampas/mesetas 2→5→10 VUs, descenso 0; 7 tramos de 30 s, total 210 s                                          | Criterios comunes diagnósticos; observar p95, errores y throughput por tramo                                                          | Puede saturar API/BD local. **Script implementado; aún no ejecutado con k6 real**                                                |
| `scenarios/read.js` / `spike`                        | Pico y recuperación de lectura                                                                                                     | Igual que load                                                                         | `READ_PROFILE=spike`, `K6_ALLOW_HEAVY=1`; base 1 VU 30 s, subida 5 s a 10, meseta 30 s, bajada 5 s a 1, recuperación 30 s, descenso 5 s a 0; total 105 s | Mismos checks; comparar series antes/durante/después, no solo p95 agregado                                                            | No demuestra resiliencia antes de medir. **Script implementado; aún no ejecutado con k6 real**                                   |
| `scenarios/read.js` / `soak`                         | Carga sostenida moderada                                                                                                           | Igual que load y JWT con vigencia suficiente                                           | `READ_PROFILE=soak`, `K6_ALLOW_HEAVY=1`; 3 VUs, 900 s, pausa 1 s                                                                                         | Criterios comunes y tendencia temporal; correlación con memoria/conexiones si se observan externamente                                | Sin renovación automática del JWT. **Script implementado; aún no ejecutado con k6 real**                                         |
| `scenarios/arrival-rate-read.js` / `arrival`         | Tasa fija; GET `/incidents/mine` para el actor verificado                                                                          | Solo token; dataset para interpretar volumen                                           | 2 iteraciones/s, 60 s, 2 VUs preasignados, máximo 5; sin sleep                                                                                           | Una petición/iteración, página, `dropped_iterations count==0` y criterios comunes                                                     | Capacidad del generador también limita. **Script implementado; aún no ejecutado con k6 real**                                    |
| `scenarios/map-read.js` / `map`                      | Lectura de Mapas independiente; cualquier actor                                                                                    | Token; viewport; ID esperado opcional a zoom >=18                                      | 1 VU fijo, 60 s, pausa 5 s; nunca pool compartido                                                                                                        | HTTP 200, variantes, conteos, privacidad, ID si se declara, no-store; criterios comunes                                               | Cuota 60 por usuario, reposición 60/min; 422/429/503 son fallos nominales. **Script implementado; aún no ejecutado con k6 real** |
| `scenarios/staff-read.js` / `processing`             | P2 opt-in al seleccionar este archivo; colecciones internas y resultado de Tramitación, OPERATOR/ADMIN                             | Token de personal y `*_INCIDENT_ID`; datos tramitados para medir colecciones no vacías | 1 VU, 30 s, cuatro GET/iteración y pausa 5 s; `STAFF_FLOW=processing`                                                                                    | Paginación, relación padre, decisión/prioridad, resultado institucional; criterios comunes                                            | No crea revisiones ni cambia estado. **Script implementado; aún no ejecutado con k6 real**                                       |
| `scenarios/staff-read.js` / `duplicates`             | P2 opt-in; candidatos heurísticos, OPERATOR/ADMIN                                                                                  | Token, `*_INCIDENT_ID`, `DUPLICATE_CANDIDATE_ID` opcional                              | 1 VU, 30 s, un GET/iteración, pausa 5 s; `STAFF_FLOW=duplicates`                                                                                         | <=20 candidatos, UUID, score 0..1, presencia si se declara; criterios comunes                                                         | Consulta SQL con timeout 5 s; no confirma vínculos. **Script implementado; aún no ejecutado con k6 real**                        |
| `scenarios/smoke-incidents-write.js` / `write_smoke` | Smoke mutativo CITIZEN: POST, GET, PATCH, historial                                                                                | Token ciudadano puro; `RUN_ID`; categoría activa opcional                              | `K6_ENABLE_WRITES=1`; 1 VU, 1 iteración, máximo 3 min; exactamente un POST y hasta un PATCH, cuatro peticiones nominales                                 | 201/200, Location, ID/titular, estado REPORTED, ETag/If-Match, versión creciente, eventos CREATED/UPDATED; cero fallos y 100 % checks | Deja incidencia y auditoría; sin DELETE ni reintentos. **Script implementado; aún no ejecutado con k6 real**                     |

Los escenarios temporizados tienen `gracefulStop=15s`; stress/spike también `gracefulRampDown=15s`. Los smoke usan el margen predeterminado de k6 (30 s). `setup` tiene máximo 90 s y emite una lectura de perfil, más detalle si hay fixture obligatorio y categoría si se especifica en escritura. Son peticiones de preflight, fuera de los thresholds nominales; no hay login ni creación masiva en setup. Para smoke/read, setup devuelve el ID del informante obtenido del detalle: permite comprobar el actor del historial también cuando quien consulta es personal, sin añadir peticiones nominales ni identidades a las métricas.

### Operaciones y autorización exactas

| Método/ruta                                                     | Uso en la batería y comprobación principal                                                                       | Rol mínimo / datos                                                                          |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| GET `/users/me`                                                 | Smoke y preflight; ID igual a `sub`, activo, verificado, rol declarado                                           | CITIZEN; si `ACTOR=CITIZEN`, se rechazan OPERATOR/ADMIN para no ocultar errores de permisos |
| GET `/categories`, `/departments`                               | Smoke y mezcla; array incluso vacío, UUID/nombre/active; departamento con code                                   | CITIZEN; sin fixtures de catálogo obligatorios                                              |
| GET `/categories/{categoryId}`                                  | Solo preflight de escritura con categoría; ID exacto y active=true                                               | CITIZEN y `CATEGORY_ID` real                                                                |
| GET `/incidents/mine?page=…&pageSize=…`                         | Smoke, mezcla y tasa fija; página coherente, DTO mínimo                                                          | Titular del JWT; no admite `userId` ni filtros de estado                                    |
| GET `/incidents?page=…&pageSize=…&status=…&sort=…`              | Solo personal en smoke/mezcla; estado y filtros coincidentes en cada fila                                        | OPERATOR/ADMIN; categoría/prioridad opcionales                                              |
| GET `/incidents/{incidentId}`                                   | Smoke/mezcla/preflight/escritura; UUID exacto, posición, versión, ETag fuerte                                    | Titular u OPERATOR/ADMIN; fixture real                                                      |
| GET `/incidents/{incidentId}/history?page=…&pageSize=…`         | Smoke/mezcla/escritura; página, eventos públicos y actor restringido al informante                               | Igual visibilidad que detalle                                                               |
| GET `/incidents/{incidentId}/review-outcome`                    | Smoke/processing; incidentId exacto, estado, informationPending, latestReview nullable e identidad institucional | Titular u OPERATOR/ADMIN                                                                    |
| GET `/incidents/{incidentId}/reviews`, `/priority-decisions`    | Processing; página, padre, decisión/prioridad válida                                                             | OPERATOR/ADMIN                                                                              |
| GET `/incidents/{incidentId}/information-responses`             | Processing; página, padre, reviewId y mensaje                                                                    | Titular u OPERATOR/ADMIN; batería la consulta con personal                                  |
| GET `/incidents/{incidentId}/duplicate-candidates`              | Variante duplicates; vacío válido o candidato declarado presente                                                 | OPERATOR/ADMIN y expediente existente                                                       |
| GET `/map/incidents?minLat=…&minLon=…&maxLat=…&maxLon=…&zoom=…` | Smoke/map; conteos completos y DTO sin detalle privado                                                           | CITIZEN; visibilidad depende del actor y VALIDATE histórico para ajenas                     |
| POST `/incidents`                                               | Alta propia única: title, description, coordinates y categoryId opcional                                         | CITIZEN puro de pruebas; estado/autor/prioridad los asigna el servidor                      |
| PATCH `/incidents/{incidentId}`                                 | Corrige description en REPORTED con If-Match obtenido del GET inmediatamente anterior                            | Titular; un 412 aborta el flujo, sin inventar ETag ni reintentar                            |

Se admite paginación vacía, incluyendo `totalPages=0` sin registros. Un UUID suministrado obliga a verificar ese recurso; no se sustituye por un UUID inventado. Los validadores comprueban las proyecciones actuales mediante listas de claves permitidas/obligatorias, tipos y reglas de negocio; no sustituyen un validador exhaustivo de OpenAPI ni las regresiones de seguridad Java. Mapas rechaza atributos extra tanto en INCIDENT/CLUSTER como en category/coordinates. Historial admite los details permitidos por evento y la ausencia de actor; solo el informante puede identificarse en CREATED/UPDATED/INFORMATION_RESPONDED. El resultado público admite los null de Jackson y únicamente la revisión institucional, sin identidades internas. El detalle privado valida reporter/location y los catálogos anidados; perfil, colecciones internas y duplicados también comprueban sus DTO. La lectura de prioridades acepta MANUAL y el valor histórico ASSISTED del enum, sin incorporar una operación de escritura asistida.

### Exclusiones deliberadas

- No se cargan registro/login, confirmaciones, reenvíos, contraseña, invitaciones ni modificaciones de usuarios/catálogos: cuotas, códigos de un solo uso, correo o permisos persistentes. Tokens provisionados una vez; no login por iteración.
- No hay POST de Tramitación ni `duplicate-links`: requieren transiciones, `expectedIncidentVersion`, revisor distinto del informante y/o enlaces sin operación normal de deshacer. Las lecturas sí están cubiertas. Un flujo mutativo adicional necesitaría expedientes independientes y aceptación específica.
- Evidencias se excluyen también del smoke: un entorno SQL local no aísla Neon Object Storage; subir/descargar genera tráfico y la baja lógica conserva objetos. El perfil Java `neon-storage` ya proporciona el ensayo funcional externo explícito y autorizado. No se añade otra vía de coste S3.
- No hay carga mixta de lectura/escritura ni escritura sostenida: no existe DELETE de incidencias y se evita crecimiento ilimitado de auditoría. El smoke verifica un recorrido real acotado.
- Negativas 401/403/404/428 y cuota 429 deliberada se dejan a las suites funcionales existentes: bajo valor adicional para medir rendimiento y riesgo de consumir buckets compartidos. No se mezclan respuestas negativas esperadas con el tráfico nominal. Los errores imprevistos siempre fallan.
- No se llaman rutas futuras: asignaciones, derivación, cambios generales de estado, comentarios, analítica, notificaciones, informes, contexto/activos, equipos ni funcionalidades de frontend no implementadas.

## Preparación e instalación

Seguir [fixtures](fixtures/README.md) y [Desarrollo](../docs/development.md): PostgreSQL local exclusivo, cuentas confirmadas mediante Mailpit, permisos globales preparados sin presuponer ADMIN inicial y UUID reales. Los datasets S/M/L y las cuentas no los crea esta batería. Las pruebas pueden ejecutarse cuando estén provisionados; M/L y colecciones tramitadas requieren preparación adicional. No hay conexión a Neon productivo autorizada por esta documentación.

Instalar en una sesión futura el binario oficial de [Grafana k6 para Windows](https://grafana.com/docs/k6/latest/set-up/install-k6/), siguiendo el instalador o gestor descrito allí, y añadirlo a PATH. Registrar `k6 version` junto con la versión de backend y el hardware. No instalar un paquete npm llamado k6: los ejecutables importan el runtime `k6/http`, `k6`, `k6/metrics`, `k6/encoding` y `k6/execution`. Node se usa para analizar sintaxis y ejecutar regresiones offline con dobles de k6; no genera carga ni ejecuta el runtime real. No hay dependencias Node en los escenarios ni bundler/transpiler; las pruebas utilizan únicamente módulos integrados de Node.

### Guardas y parámetros

| Variable                                                               | Valor predeterminado / límite                                                                                                                                                  |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `BASE_URL`                                                             | `http://127.0.0.1:8080`; solo origen sin `/api/v1`, ruta, credenciales, query o fragmento                                                                                      |
| `K6_ENVIRONMENT`                                                       | Obligatoria: `isolated-test` en todos los ejecutables; confirma aislamiento real de API **y BD**                                                                               |
| `K6_ALLOW_REMOTE`, `K6_REMOTE_ORIGIN`                                  | Remoto bloqueado salvo `1`, origen HTTPS idéntico a `BASE_URL` y autorización del responsable; exclusivamente entorno de pruebas, nunca producción                             |
| `ACTOR`                                                                | `CITIZEN`; `staff-read.js` usa `OPERATOR` por defecto. Valores CITIZEN/OPERATOR/ADMIN; solo se necesita el token del elegido                                                   |
| `*_TOKEN`, `*_INCIDENT_ID`                                             | Según tabla de fixtures. JWT sin prefijo Bearer. Claims/exp se examinan localmente; firma, actividad y roles los valida la API                                                 |
| `HTTP_TIMEOUT_SECONDS`                                                 | 10; 1..30. No confundir con timeout SQL de 5 s de Mapas/Duplicados                                                                                                             |
| `PAGE`, `PAGE_SIZE`                                                    | 1 y 20; límites 1..10.000 y 1..100, más conservadores que el offset máximo de la API                                                                                           |
| `READ_PROFILE`                                                         | `load`; valores `load`, `stress`, `spike`, `soak` en `read.js`                                                                                                                 |
| `VUS`                                                                  | 3; 1..50 para load/soak; >10 exige `K6_ALLOW_HEAVY=1`                                                                                                                          |
| `DURATION_SECONDS`                                                     | load/arrival/map: 60, soak: 900, staff: 30. Mínimo 10; máximo 300 salvo soak 1800 y staff 120. Stress/spike usan STAGE_SECONDS                                                 |
| `PAUSE_SECONDS`                                                        | 1; 0,5..30 (escribir decimales con punto); solo mezcla de `read.js`                                                                                                            |
| `PEAK_VUS`, `STAGE_SECONDS`                                            | 10 (3..50) y 30 (10..120); solo stress/spike                                                                                                                                   |
| `K6_ALLOW_HEAVY`                                                       | `1` obligatorio para stress/spike/soak, load >10 VUs, arrival RATE>5 o MAX_VUS>10                                                                                              |
| `READ_WEIGHTS`                                                         | Seis enteros separados por comas que suman 100: mine, detail, history, categories, departments, staff_list. Ciudadano `40,30,15,10,5,0`; personal `25,25,15,10,5,20`           |
| `INCIDENT_STATUS`, `INCIDENT_SORT`                                     | REPORTED y `priority,desc`; estados del enum y `priority,asc`/`priority,desc`. Solo listado de personal                                                                        |
| `INCIDENT_PRIORITY`, `CATEGORY_ID`                                     | Opcionales; prioridad LOW/MEDIUM/HIGH/CRITICAL y UUID. No se aceptan filtros futuros. CATEGORY_ID también se usa en alta                                                       |
| `RATE`, `PREALLOCATED_VUS`, `MAX_VUS`                                  | 2 (1..20), 2 (1..50), 5 (>=preasignados, <=50); timeUnit fijo `1s`, solo arrival                                                                                               |
| `MAP_MIN_LAT`, `MAP_MIN_LON`, `MAP_MAX_LAT`, `MAP_MAX_LON`, `MAP_ZOOM` | 36, -6, 38, -3, 8; WGS84, min<max, zoom 0..22 y amplitud <=min(360,1440/2^zoom)                                                                                                |
| `MAP_INTERVAL_SECONDS`                                                 | 5; 2..60. Mapas fija un VU; máximo teórico 30 consultas/min con pausa mínima, excluyendo tiempo HTTP                                                                           |
| `MAP_EXPECTED_INCIDENT_ID`                                             | Opcional; solo se admite con zoom >=18                                                                                                                                         |
| `STAFF_FLOW`, `DUPLICATE_CANDIDATE_ID`                                 | `processing` o `duplicates`; candidato esperado opcional                                                                                                                       |
| `K6_ENABLE_WRITES`, `RUN_ID`                                           | Escritura solo con `1` e ID nuevo de 8..64 caracteres ASCII alfanuméricos/guion; UUID nuevo recomendado, no obligatorio. Título combina RUN_ID, `__VU` y `__ITER`              |
| `WRITE_LATITUDE`, `WRITE_LONGITUDE`                                    | 36.7213 y -4.4214; límites WGS84. Solo alta controlada                                                                                                                         |
| `P95_MS`, `ERROR_RATE`, `CHECK_RATE`                                   | 500 (1..60.000), 0.01 (>0..1), 0.99 (0..1); condiciones estrictas p95<valor, errores<valor y checks>valor en perfiles temporizados; smoke mantiene criterios funcionales fijos |

Los valores predeterminados solo se aplican si la variable está ausente. Un valor vacío o compuesto solo por espacios se rechaza cuando el escenario lee ese parámetro, incluidos choices, UUID opcionales y pesos. Para desactivar un opcional, retirar la variable del entorno, no asignarle una cadena vacía.

No usar overrides CLI `--vus`, `--duration`, `--iterations`, `--stage`, `--no-thresholds` ni variables nativas `K6_*` que cambien options, tags o redirecciones. Usar las variables de esta tabla. Las guardas previenen errores con esta configuración, no constituyen una frontera frente a quien modifica el script/CLI o configura un proxy/túnel local a producción. Verificar siempre el destino JDBC detrás de localhost. No ejecutar varias instancias sobre la misma cuenta de Mapas; no se necesita ni se implementa un pool de tokens para esta carga delimitada.

## Comandos PowerShell para una sesión futura

**No se han ejecutado estos comandos de carga en esta entrega.** Ejecutar desde la raíz en una consola dedicada, tras preparar fixtures y detener otros clientes que usen sus cuentas. `Read-Host -MaskInput` requiere PowerShell 7; evita poner el JWT literal en el historial de comandos. Los procesos aún reciben el secreto por entorno: usar una sesión local privada, sin transcripciones.

```powershell
$env:BASE_URL = 'http://127.0.0.1:8080'
$env:K6_ENVIRONMENT = 'isolated-test'
$env:ACTOR = 'CITIZEN'
$env:CITIZEN_TOKEN = Read-Host 'JWT ciudadano verificado' -MaskInput
$env:CITIZEN_INCIDENT_ID = Read-Host 'UUID real de incidencia propia'
k6 run .\k6\scenarios\smoke-read.js

$env:READ_PROFILE = 'load'
k6 run .\k6\scenarios\read.js
k6 run .\k6\scenarios\arrival-rate-read.js
k6 run .\k6\scenarios\map-read.js
```

El ciclo de mezcla recorre determinísticamente 100 posiciones, dispersas por iteración y desfasadas por VU. Las ponderaciones priorizan listados y detalle; historiales y catálogos se consultan menos. Solo hay una petición por iteración de mezcla, seguida de pausa; en muestras cortas la distribución efectiva puede diferir. Registrar los recuentos por operación. No se multiplica la carga añadiendo Mapas o Duplicados a la mezcla.

```powershell
# Personal: nueva sesión o variables coherentes con la cuenta seleccionada.
$env:ACTOR = 'OPERATOR'
$env:OPERATOR_TOKEN = Read-Host 'JWT operador de pruebas' -MaskInput
$env:OPERATOR_INCIDENT_ID = Read-Host 'UUID de expediente accesible al operador'
k6 run .\k6\scenarios\smoke-read.js
$env:STAFF_FLOW = 'processing'
k6 run .\k6\scenarios\staff-read.js
$env:STAFF_FLOW = 'duplicates'
k6 run .\k6\scenarios\staff-read.js
# ADMIN usa ACTOR=ADMIN, ADMIN_TOKEN y ADMIN_INCIDENT_ID en los mismos archivos.
```

Elegir un único perfil pesado por ensayo; conservar CPU, dataset y configuración:

```powershell
$env:ACTOR = 'CITIZEN'
$env:K6_ALLOW_HEAVY = '1'
$env:READ_PROFILE = 'stress' # o spike, o soak; no lanzar los tres a la vez
k6 run .\k6\scenarios\read.js
Remove-Item Env:K6_ALLOW_HEAVY
```

Antes de soak, retirar `DURATION_SECONDS` de un ensayo anterior para usar sus 900 s. La validación local exige que el JWT cubra duración máxima, setup, finalización y 60 s extra; no hay refresh ni login por VU. Soak máximo 30 min, inferior a las dos horas del token, pero un JWT antiguo puede no bastar. Revocación durante el ensayo sigue siendo un error real, aunque `exp` sea válida.

```powershell
# Escritura: una incidencia nueva. Requiere aislamiento ya confirmado.
$env:ACTOR = 'CITIZEN'
$env:K6_ENABLE_WRITES = '1'
$env:RUN_ID = [guid]::NewGuid().ToString()
k6 run .\k6\scenarios\smoke-incidents-write.js
Remove-Item Env:K6_ENABLE_WRITES, Env:RUN_ID
```

No hay retries: el POST puede haberse confirmado aunque falle la conexión; un 412 en PATCH se registra como fallo y aborta. El detalle se vuelve a consultar antes del PATCH para obtener la ETag vigente; nunca se deriva de `version`. Se exige el formato actual `"sha256-<64 hexadecimales minúsculos>"`, se rechazan ETag débiles/ausentes/obsoletas y se transmite literalmente la cabecera del GET como If-Match. La incidencia permanece en REPORTED. Consultar y conservar/restaurar únicamente la BD de pruebas según [limpieza](fixtures/README.md#residuos-y-limpieza).

Ejemplo de viewport individual válido (amplitud 0.002°, inferior al máximo de zoom 18); el ID debe estar realmente dentro:

```powershell
$env:MAP_MIN_LAT = '36.720'
$env:MAP_MAX_LAT = '36.722'
$env:MAP_MIN_LON = '-4.422'
$env:MAP_MAX_LON = '-4.420'
$env:MAP_ZOOM = '18'
$env:MAP_EXPECTED_INCIDENT_ID = $env:CITIZEN_INCIDENT_ID
k6 run .\k6\scenarios\map-read.js
```

La lista vacía es 200 legítimo; el ID esperado es una aserción adicional explícita. Un 422 exige reducir área o zoom, no asumir truncamiento exitoso. Un 429 significa cuota consumida (también por otras sesiones), y se inspecciona `Retry-After`; no prueba saturación SQL. Un 503 puede ser timeout de consulta o indisponibilidad. No se consulta el detalle de marcadores públicos ajenos.

## Hipótesis de aceptación y lectura de métricas

Para **GET mine, detalle, historial y catálogos**, con el dataset S preparado, una instancia local del backend, PostgreSQL 18, Hikari máximo 5 y hardware registrado: ensayo load de **3 VUs, 60 s, pausa 1 s**, hipótesis inicial **p95<500 ms, errores HTTP<1 %, checks>99 % y peticiones con fallo semántico<1 %**. Para personal se añade el listado filtrado con iguales condiciones. Son referencias docentes configurables, no SLO aprobados. Repetir con M/L solo después de prepararlos y registrar tamaño real; no extrapolar una base vacía.

Arrival propone el mismo criterio para mine a **2 iteraciones/s durante 60 s**, 2 VUs preasignados y máximo 5, más **cero dropped_iterations**. Mapas propone 1 VU/60 s/pausa 5 s con viewport y distribución registrados; Duplicados y Tramitación 1 VU/30 s/pausa 5 s. También utilizan inicialmente 500 ms para detectar diferencias en la línea base; el timeout SQL de 5 s no es una latencia aceptable. Los dos smoke exigen **cero errores HTTP, 100 % de checks y cero peticiones con fallo semántico**. Conservan las métricas y percentiles de latencia, pero no un threshold p95: una sola iteración verifica fiabilidad funcional y no ofrece una estimación estadística robusta. Esta separación no modifica los criterios p95, errores, checks ni dropped_iterations de load/arrival.

Stress/spike conservan esos límites como **alarmas diagnósticas**, sin `abortOnFail`: el incumplimiento da código de salida no satisfactorio, pero no impide por sí solo observar descenso y recuperación. No se debe interpretar un umbral cruzado como incumplimiento de un contrato de capacidad aprobado. Detener manualmente si el entorno de pruebas queda inestable; registrar hasta qué tramo se llegó. Soak propone 3 VUs/15 min y exige revisar la evolución, no solo el promedio final. Revisar los umbrales tras una línea base reproducible y un objetivo acordado; guardar junto a la ejecución los valores utilizados, sin relajarlos retrospectivamente para fabricar un éxito.

| Métrica                            | Interpretación                                                                                                                                                       |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `http_req_duration`                | Tiempo HTTP de envío, espera y recepción; p95 no incluye todo el tiempo bloqueado/conexión. Separar por `operation` y tramo temporal                                 |
| `http_req_failed`                  | Tasa de respuestas fuera del HTTP exacto esperado (200 o 201) o errores de transporte; no mide por sí sola semántica JSON                                            |
| `http_reqs`                        | Recuento y tasa HTTP; el total incluye setup. Filtrar `traffic:nominal` para comparar carga útil                                                                     |
| `iteration_duration`, `iterations` | Duración y cantidad de recorridos; incluye pausas y varias peticiones en smoke/processing. Revisar escenario, pues setup también puede influir en métricas agregadas |
| `vus`, `vus_max`                   | Ejecutores virtuales activos y máximo disponible; no personas ni conexiones del pool SQL                                                                             |
| `checks`                           | Proporción de aserciones correctas; un check fallido solo afecta al resultado global cuando hay threshold                                                            |
| `semantic_failures`                | Rate por petición nominal cuyo conjunto de checks falla; evita diluir una respuesta incorrecta entre muchas aserciones                                               |
| `unexpected_responses`             | Counter por operación y clase estable `rate_limit`, `precondition`, `unavailable`, `other`; no contiene cuerpos/UUID/tokens                                          |
| `dropped_iterations`               | Iteraciones que arrival no pudo iniciar con los VUs disponibles; criterio count==0                                                                                   |

VU es una unidad de ejecución, iteración es una llamada al recorrido, petición es una operación HTTP. En arrival hay **una única petición por iteración** y no sleep, redirecciones o retries, de modo que tasas ofrecidas de iteraciones/s y peticiones/s se corresponden. El setup queda fuera; el throughput realmente alcanzado puede ser menor. En otros recorridos iteraciones/s no equivale necesariamente a requests/s. [Ejecutor oficial](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/).

Si crecen dropped_iterations y latencia, observar CPU/memoria de **generador y servidor**, red, duración de iteraciones y ocupación de VUs. Puede faltar capacidad del generador o estar el servidor ralentizando cada petición; alcanzar `maxVUs` por sí solo no distingue ambos casos. Aumentar VUs asignados requiere respetar límites y repetir bajo las mismas condiciones; no esconder drops ni atribuirlos automáticamente a PostgreSQL.

`traffic=preflight` separa preparación, `traffic=nominal` filtra los thresholds HTTP/checks y `traffic=diagnostic` comprueba ProblemDetail/Retry-After cuando aparece un error. El error original sigue fallando el tráfico nominal. Cache-Control debe contener la directiva no-store real; substrings como x-no-store o no-store=false no la sustituyen. `name` usa plantillas de ruta y `operation` nombres estables; el tag de sistema `url` está desactivado para evitar UUID/query de alta cardinalidad. No activar `--http-debug`: puede revelar Authorization y datos de cuenta. [Tags de k6](https://grafana.com/docs/k6/latest/using-k6/tags-and-groups/).

### Comparación A/B/C/D

Proponer A=1, B=10, C=20, D=50 VUs, **60 s cada uno**, perfil load y misma identidad, snapshot, pesos, página, pausa, umbrales, versión, CPU/RAM y red. C/D requieren `K6_ALLOW_HEAVY=1`. Lanzar por separado, tras recuperación y con el mismo protocolo de calentamiento; no simultanear Maven/Expo builds ni otras cargas. Cambiar solo `VUS`; registrar p95 por operación, requests/s, errores, checks y recursos observables. Repetir con M/L como experimentos distintos. Más VUs sin más throughput, junto con mayor p95/errores, sugiere saturación; necesita correlación, no demuestra por sí solo un cuello SQL. A/B/C/D son propuestas; **no ejecutados, sin datos**.

## Dashboard e informes locales

El dashboard integrado no requiere Grafana Server. Se habilita al ejecutar; normalmente aparece en [localhost:5665](http://localhost:5665). El HTML se exporta al terminar una ejecución real; no se genera por preparar variables. Para una serie temporal con más muestras, usar load; el smoke puede acabar demasiado rápido para gráficos útiles. [Documentación oficial del dashboard](https://grafana.com/docs/k6/latest/results-output/web-dashboard/).

```powershell
New-Item -ItemType Directory -Force .\k6\results | Out-Null
$env:READ_PROFILE = 'load'
$env:K6_WEB_DASHBOARD = 'true'
$env:K6_WEB_DASHBOARD_HOST = '127.0.0.1'
$env:K6_WEB_DASHBOARD_EXPORT = '.\k6\results\load-read.html'
k6 run .\k6\scenarios\read.js
# Cerrar la pestaña del dashboard si mantiene el proceso abierto al finalizar.
```

Guardar HTML/JSON/CSV/logs y notas de ejecución bajo `k6/results/`, ignorado. `k6/local/` guarda manifiestos privados; `.env` ya está ignorado globalmente. No publicar tokens, volcados, IDs de usuarios reales ni informes sensibles. Al terminar, cerrar la consola dedicada o retirar sus variables (incluidos ROLE_TOKEN, fixtures, flags y parámetros); ejemplo para los secretos y activaciones:

```powershell
'CITIZEN_TOKEN','OPERATOR_TOKEN','ADMIN_TOKEN','K6_ENABLE_WRITES','K6_ALLOW_HEAVY',
'K6_ALLOW_REMOTE','K6_REMOTE_ORIGIN','K6_ENVIRONMENT','RUN_ID','K6_WEB_DASHBOARD',
'K6_WEB_DASHBOARD_HOST','K6_WEB_DASHBOARD_EXPORT' | ForEach-Object {
    Remove-Item -LiteralPath "Env:$_" -ErrorAction SilentlyContinue
}
```

k6 mide desde el cliente; una futura integración Actuator/Micrometer podría exponer métricas JVM, CPU, hilos y Hikari a Prometheus, y Grafana Server visualizarlas junto con k6 mediante un receptor Remote Write configurado. Requeriría dependencias, exposición protegida, colector, almacenamiento y dashboards: **no están desplegados ni se añaden en esta entrega**. Son herramientas complementarias; k6 no puede deducir conexiones retenidas o fugas de memoria a partir de latencia únicamente. [Arquitectura](../docs/architecture.md).

## Mantenimiento y comprobaciones sin tráfico

Desde la raíz, con PowerShell 7 y Node disponible:

```powershell
.\k6\check-syntax.ps1
node --experimental-vm-modules --test .\k6\tests\offline.test.mjs
git diff --check
```

`check-syntax.ps1` pasa cada `.js` y `.mjs` a `node --input-type=module --check`: analiza ESM **sin evaluar imports, init, setup ni funciones de escenario**. No acredita compatibilidad dinámica del runtime k6, validez de tokens reales ni resultados HTTP. No se instala k6 como dependencia del proyecto ni se añaden cargas a CI.

Las [regresiones offline](tests/offline.test.mjs) usan los dos ejemplos versionados de Mapas como controles positivos y mutaciones sintéticas para propiedades adicionales, campos ausentes, tipos, límites, identidades, detalles históricos, null/omisiones y ETag. También verifican configuración vacía, flags, caducidad JWT, separación de métricas y los recorridos de los seis escenarios con respuestas simuladas, incluida una única creación, If-Match literal y aborto sin retry. El [harness](tests/harness.mjs) enlaza imports locales y sustituye los cinco módulos de k6 por dobles sin transporte de red. Se ha comprobado con Node 24.15.0; `--experimental-vm-modules` puede emitir un aviso experimental. No requiere instalar paquetes ni credenciales reales. Estos dobles prueban nuestra lógica, no la implementación de métricas, ejecutores o HTTP del binario k6.

La revisión de esta entrega comprende tres pasadas: contrato/seguridad/datos, correctitud de scripts/guardas y coherencia editorial/enlaces; más comprobación final de Git. Se analiza sintaxis, se ejecutan regresiones sintéticas y se inspeccionan rutas/imports/thresholds; no se ha ejecutado `k6 run`, `k6 inspect`, la aplicación, pruebas Java ni consultas a bases/servicios externos para esta batería. k6 no está disponible en el PATH del entorno de revisión, por lo que la configuración de ejecutores y la semántica del runtime no se han comprobado en el binario real. Las evidencias Java/SQL anteriores mantienen sus fechas y no se atribuyen a k6.

Al extenderla, contrastar de nuevo cada operación con controlador/servicio, DTO, seguridad, OpenAPI `implemented` y tests, mantener tags estables y separar preflight/negativas/escrituras. Actualizar esta matriz y [los fixtures](fixtures/README.md) en el mismo cambio. No exigir stress en cada contribución; [CONTRIBUTING](../CONTRIBUTING.md).
