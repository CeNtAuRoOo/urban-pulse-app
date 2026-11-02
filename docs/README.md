# Documentación de UrbanPulse

Este directorio contiene documentación **viva**, separando las decisiones de diseño acordadas de la funcionalidad realmente implementada.

| Documento                                     | Contenido                                                                                                                                                                              |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Diseño de autenticación](auth-design.md)     | **Implementación del tag Autenticación**: registro, login, cambio de contraseña, roles, JWT, pruebas y tareas futuras claramente diferenciadas.                                        |
| [Modelo de dominio v1](domain-model.md)       | Entidades, roles, ciclo de vida, asignaciones, limitaciones y revisión obligatoria por tag antes de implementar.                                                                       |
| [Diagramas](diagrams/README.md)               | C4 con fuente `c4.drawio`; modelo relacional de cuatro páginas y catálogo de clases Java simplificado, con fuentes y exportaciones.                                                    |
| [Arquitectura](architecture.md)               | Arquitectura objetivo y estado real de frontend, backend, Neon y almacenamiento.                                                                                                       |
| [Contrato dominio–API](api/domain-openapi.md) | Decisiones y operaciones armonizadas; implementados Autenticación, Department y Category dentro de Catálogos. Los demás recursos siguen en diseño y se revisan por tag. |
| [Diseño de API](api/README.md)                | OpenAPI `0.4.0`: implementados Autenticación y los catálogos Department y Category; el resto del contrato permanece en diseño. |
| [Cierre de departamentos](departments-finalization.md) | Evidencia histórica de Department (30–31/10/2026), alcance y pendientes. |
| [Cierre de categorías](categories-finalization.md) | Implementación de Category y resultados actuales de la revisión final, incluida regresión de autenticación y Departamentos. |
| [ADR](adr/README.md)                          | Decisiones aceptadas, propuestas y asuntos pendientes. En autenticación se revisan los ADR-0005, 0007, 0009 y 0011.                                                                    |
| [Desarrollo](development.md)                  | Entorno y comandos de instalación, ejecución y verificaciones comprobadas.                                                                                                             |
| [Despliegue](deployment.md)                   | Objetivo de contenedores independientes y despliegue futuro.                                                                                                                           |

## Estado actual del proyecto (02/11/2026)

El backend cuenta con tres funcionalidades implementadas y verificadas localmente:

- **Autenticación:** registro, inicio de sesión, cambio de contraseña, verificación de correo, JWT y gestión de permisos.
- **Departamentos (tag Catálogos):** listado, consulta, creación y actualización parcial de departamentos, incluida la desactivación lógica. Las operaciones de escritura requieren el rol global `ADMIN`.
- **Categorías (tag Catálogos):** listado, consulta, creación y PATCH con desactivación/reactivación, nombre único y descripción opcional; sin código corto. Ambos catálogos exigen correo verificado y ADMIN global para escribir.

El contrato OpenAPI se encuentra en la versión `0.4.0`. Las dos vistas de dominio y la documentación reflejan Department y Category. UUIDv7 está implementado mediante Hibernate y probado para User, Department y Category. Incidencias, equipos, pertenencias departamentales y otros módulos continúan pendientes; RF18 (auditoría administrativa) no está resuelto.

La última revisión (02/11/2026, 11:36:57 Europe/Madrid) supera **109 pruebas: 78 unitarias/MVC y 31 PostgreSQL**, sin fallos, errores ni omisiones. Las migraciones Flyway V1, V2 y V3 se verifican conjuntamente en PostgreSQL efímero mediante Testcontainers; no consta su aplicación en Neon `development` ni un despliegue de producción. El [informe de categorías](categories-finalization.md#10-última-revisión-antes-de-commits) registra comandos, resultados y limitaciones. Las 80 pruebas de Department del 31/10 y las 108 de la primera implementación de Category son evidencias históricas.

Los resultados y límites actuales se detallan en el [informe de cierre de categorías](categories-finalization.md).

## Evidencias históricas

Los apartados siguientes documentan etapas anteriores. Sus referencias a funcionalidades pendientes, versiones OpenAPI y cifras de pruebas corresponden a esas fechas; el estado vigente se recoge arriba y en la última revisión del informe de categorías.

**Estado técnico comprobado a 15/10/2026:** frontend web y arranque conjunto de frontend/backend por Docker Compose; conectividad JDBC con Neon PostgreSQL 18 y acceso al bucket S3 privado desde backend de desarrollo. Esta evidencia precede a autenticación y catálogos y no valida el despliegue de sus migraciones. Los cinco endpoints de autenticación y la V1 ampliada corresponden a las entregas posteriores de octubre.

**Modelo compartido (18/10/2026):** las dos vistas de dominio y OpenAPI `0.2.0` constituyeron una base inicial revisable. Cada equipo revisa conjuntamente ambas vistas y el contrato del tag antes de implementar. Una clase dibujada o una ruta documentada no demuestra que ya exista código. Ver [CONTRIBUTING](../CONTRIBUTING.md).

**Revisión adicional de autenticación (25/10/2026):** se documenta una propuesta de contrato `0.3.0` que incorpora registro con JWT, cambio de contraseña con invalidación mediante `tokenVersion`, lectura de permisos vigentes por petición, emailVerified, rol base `CITIZEN`, UUIDv7 desde Hibernate y rate limiting local. Se distingue en el [documento específico](auth-design.md) entre **funcionalidad pendiente de esta rama** y **mejoras futuras**. **La autenticación ya se ha implementado localmente**, con tests unitarios/MVC e integración aislada. Las demás operaciones de negocio mantienen su carácter de diseño.

El [README principal](../README.md) recoge los pasos mínimos de puesta en marcha. La evidencia de autenticación del 25/10/2026 fue de 23 pruebas; la ampliación del correo del 28/10 registró 55 (35 unitarias/MVC y 20 PostgreSQL efímero). Son resultados históricos; las pruebas de conectividad de infraestructura siguen siendo distintas.

**Actualización de autenticación (28/10/2026):** el backend incluye cinco POST de auth, confirmación/reenvío por código HMAC-SHA256 y servicio SMTP configurable (Mailpit solo en desarrollo), además de limpieza de cuentas sin verificar tras 24 h. Documentación funcional en [auth-design.md](auth-design.md); contratos fuente en [api/openapi/base-openapi.yaml](api/openapi/base-openapi.yaml). Los diagramas de identidad y C4 requieren actualizar sus fuentes/editables y exportar los SVG correspondientes para reflejar la nueva entidad `EmailVerification` y Mailpit.
