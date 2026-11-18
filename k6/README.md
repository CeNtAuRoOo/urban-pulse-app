# Batería local de Grafana k6

Prepara una BD desechable siguiendo la [guía de desarrollo](../docs/development.md). Desde la raíz, con Docker local, Node 24 y PowerShell 7, ejecuta `./k6/run-all-k6.ps1`. Si falta k6 en Windows, el lanzador ofrece instalarlo con winget. Los seis escenarios y sus cuatro perfiles se encuentran en [scenarios](scenarios) y [lib](lib).

El lanzador ejecuta primero las [pruebas offline](tests/offline.test.mjs). Comprueba el puerto publicado en loopback, el almacenamiento efímero de PostgreSQL, el resultado del seed y la conexión del backend con la BD del mismo Compose. Inicia sesión con las [cuentas y UUID de referencia](fixtures/README.md) y verifica el perfil. Los JWT se mantienen en el proceso. La configuración de los escenarios comprueba la vigencia para su duración máxima.

Con el entorno por defecto, soak dura 900 s. Las variables de escenario `DURATION_SECONDS` y `STAGE_SECONDS` permiten acortar una comprobación funcional sin cambiar el orden de la batería. Una sesión abreviada no mide capacidad sostenida.

La secuencia es smoke ciudadano, lectura load, tasa de llegada, mapas, stress, spike, soak, smoke operador, tramitación, duplicados y smoke de escritura. La última etapa crea y corrige una incidencia identificada por un RUN_ID nuevo. Recrear la BD permite comparar sesiones con la misma población inicial.

En `k6/results/<sesión>/` abre `index.html` o consulta `index.csv`. Cada escenario conserva el registro de consola, las muestras JSON y el dashboard de k6. Si la ejecución es demasiado corta para que k6 exporte el dashboard, el HTML del escenario muestra el resumen de consola y enlaza las muestras disponibles. La mediana y los percentiles p95/p99 describen latencias, las peticiones por segundo miden caudal y las tasas de error y checks identifican fallos de contrato o servicio. Interpreta cada umbral con su escenario y volumen. El código 99 señala incumplimiento de umbrales. Un fallo de preflight requiere corregir el entorno antes de interpretar rendimiento. Los resultados se excluyen de Git.

CI valida la sintaxis y ejecuta Node offline. Para repetir solo las regresiones desde la raíz: `node --experimental-vm-modules --test k6/tests/offline.test.mjs`.
