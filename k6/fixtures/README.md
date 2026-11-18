# Fixtures de k6

[Cluster](maps-cluster.json) e [incidencia individual](maps-incident.json) son respuestas sintéticas para las pruebas offline. El [dataset de desarrollo](../../backend/dev/database/seed.sql) identifica de forma estable cuentas e incidencias para las peticiones reales del [lanzador](../run-all-k6.ps1).

Los UUID de esta tabla comparten el prefijo `029b0000-0000-7000-8000-000000000`. Añade los tres dígitos del sufijo.

| Sufijo                | Recurso                                                                                                |
| --------------------- | ------------------------------------------------------------------------------------------------------ |
| 001 / 003             | Cuentas `k6.citizen@urbanpulse.test` y `k6.operator@urbanpulse.test`                                   |
| 002 / 004 / 005 / 006 | Administración, segundo ciudadano, técnico y coordinación                                              |
| 007 / 008             | Cuenta pendiente de verificación e inactiva                                                            |
| 104                   | Categoría activa para búsquedas                                                                        |
| 401 / 402             | Expediente con revisión, respuesta y prioridad / candidato próximo sin vincular                        |
| 403                   | Incidencia propia corregida del ciudadano                                                              |
| 404–408               | Rechazo, solicitud pendiente, validación priorizada, solicitud respondida y otra validación priorizada |

Las cuentas históricas del cliente conservan el prefijo `019a0000-0000-7000-8000-`. Todas las cuentas sintéticas comparten la contraseña pública de desarrollo `demo-password`.
