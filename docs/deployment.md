# Guía de despliegue de UrbanPulse

> **Estado a 15/11/2026:** se ha comprobado el arranque conjunto de frontend y backend en Docker Compose de **desarrollo**, la conectividad histórica con Neon PostgreSQL y Object Storage, y un flujo HTTP aislado de Invitaciones con PostgreSQL/Mailpit. **No se ha acreditado ningún despliegue de producción ni la aplicación de Flyway V1 consolidada en Neon `development`**. Las fuentes C4/ER/clases describen los módulos implementados; el **nuevo diagrama C4 Deployment**, dependiente de la infraestructura finalmente elegida, continúa pendiente.

## 1. Objetivo y separación de entornos

UrbanPulse contempla un frontend web exportable como SPA, clientes Android/iOS Expo/React Native, un **único backend Spring Boot modular** y servicios externos PostgreSQL y almacenamiento de objetos. Los entornos de desarrollo y producción deben disponer de conexiones y secretos independientes. Las pruebas de mutación de Invitaciones deben emplear una base efímera y no Neon compartido. El informe histórico utilizó `compose.test.yaml`, pero **ese archivo no aparece en el repositorio**; no debe presentarse como infraestructura actualmente disponible. Ni aquella prueba ni un `HeadBucket` equivalen a un despliegue real.

- **Desarrollo**: el único `compose.yaml` arranca frontend Expo Web (8888), backend (8080), PostgreSQL 18 (5432) y Mailpit (SMTP 1025, web 9999 → 8025), todos publicados en loopback. JDBC interno `postgres:5432`, SMTP `mailpit:1025` y Neon Object Storage de desarrollo se configuran en los archivos privados. Ver [Desarrollo](development.md).
- **Pruebas aisladas**: el perfil Maven `postgres-it` usa Testcontainers PostgreSQL 18. El informe histórico de HTTP usó JAR + PostgreSQL `tmpfs` + Mailpit; su configuración Compose no está versionada en el repositorio.
- **Producción futura**: imagen frontend exportada/servida por Nginx, imagen backend independiente, Neon PostgreSQL y Neon Object Storage de producción, SMTP confiable, HTTPS, gestión segura de secretos y automatización CD. No se describe como una infraestructura ya operativa.

## 2. Componentes y responsabilidades

| Componente        | Implementado/configurado                                                                                                                                      | Pendiente de validar o entregar                                             |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Frontend Expo Web | Panel web conectado a auth, usuarios, invitaciones, catálogos e incidencias; exportación SPA                                                                  | Imagen multietapa Nginx desplegada y validación productiva                  |
| Android/iOS       | Base de código compartida Expo/React Native                                                                                                                   | Ejecución funcional completa y distribución en dispositivos                 |
| API Spring Boot   | Java 25/Spring Boot 4.1.2, módulos `auth`, `identity`, `invitations`, `organization`, `categories`, `incidents`, `processing`, `attachment`, `maps`, `duplicates`, `audit`, `shared`; puerto 8080 | Despliegue externo, TLS/proxy, observabilidad y endurecimiento de imagen    |
| PostgreSQL        | PostgreSQL 18 local persistente y esquema inicial consolidado V1 con 16 tablas; JDBC Neon comprobado históricamente                                                                    | Aplicación controlada de las migraciones en producción y gestión de backups |
| Object Storage    | Cliente S3 privado, cinco rutas de evidencias JPEG/PNG y baja lógica                                                                                          | Retención, purgado físico y reconciliación de huérfanos                     |
| SMTP              | `EmailSender` y eventos `AFTER_COMMIT`; Mailpit local                                                                                                         | Proveedor SMTP real, entregabilidad y recuperación/reintentos               |

## 3. Diagramas arquitectónicos

Las fuentes [C4 de contexto y contenedores](diagrams/README.md) actualizan el alcance V1 consolidada en [`c4.drawio`](diagrams/c4.drawio); incluyen el transporte de correo y el alcance inicial de fotografías. Las fuentes ER y de clases incorporan V1 consolidada y distinguen el modelo futuro; las ocho exportaciones SVG son históricas, permanecen idénticas y quedan pendientes de regeneración manual e inspección visual. La comprobación estructural está descrita en el [inventario](diagrams/README.md#criterios-de-sincronización).

**[Pendiente: nuevo C4 Deployment]**. Cuando se decidan hosting, dominios, TLS, redes, topología y estrategia de operación, documentarlos en un diagrama de despliegue adicional (por ejemplo, `docs/diagrams/deployment.drawio`). Esto **no** es una tarea pendiente de corrección de los gráficos C4/ER/clases existentes.

## 4. Frontend web: desarrollo y producción

El frontend utiliza Expo SDK 57, Expo Router y TypeScript. El desarrollo se ejecuta mediante Docker Compose; desde la raíz:

```bash
docker compose up --build frontend
```

La exportación `npx expo export --platform web` genera `frontend/dist/`; por sí sola no prueba un despliegue. `frontend/Dockerfile` construye la SPA y copia los estáticos a Nginx, que escucha en 8888 con fallback para las rutas de Expo Router. La imagen productiva recibe `EXPO_PUBLIC_API_URL` como argumento de build con la URL pública del despliegue. **El despliegue real de esa imagen sigue fuera de alcance**; no se utiliza para el desarrollo habitual.

`EXPO_PUBLIC_API_URL` se integra en el código cliente en tiempo de compilación, es **pública** y debe apuntar a una API accesible y preferiblemente HTTPS en producción. No incluir en ninguna variable `EXPO_PUBLIC_*` contraseñas, tokens de servidor ni credenciales Neon/S3. La distribución móvil y la adecuación de direcciones (`localhost` en un dispositivo no significa el host Docker) se verificarán por separado.

## 5. Backend, imagen y persistencia

`backend/Dockerfile` es la imagen multietapa prevista para ejecutar el JAR, distinta de `backend/Dockerfile.dev` para desarrollo. El informe histórico menciona un `backend/Dockerfile.test` de pruebas, **no incluido en el repositorio**. El servicio arranca por el puerto 8080, configurable con `SERVER_PORT`; usa Spring Security, JPA/Hibernate, Flyway, HikariCP y un cliente S3. Las variables se suministran externamente y no se versionan secretos.

El código Java está ordenado por funcionalidades, no en paquetes globales `controller/entity/service`. [Arquitectura](architecture.md) y [desarrollo](development.md) describen su estructura exacta. `V1__initial_schema.sql` crea identidad/verificación, departamentos, pertenencias, invitaciones, auditoría, categorías, incidencias, estructuras de tramitación y evidencias. V1 incluye también respuestas, AWAITING_INFORMATION, restricciones de revisión, Duplicados y funciones/índices geográficos. `spring.jpa.hibernate.ddl-auto=validate` comprueba el esquema; **Flyway puede modificarlo al iniciar** si las migraciones aún no figuran aplicadas.

Antes de desplegar una versión productiva conectada a Neon PostgreSQL:

1. Confirmar los endpoints productivos JDBC y S3 y sus permisos; los nombres de base, usuario o bucket no bastan para identificar un entorno. El desarrollo usa SQL local y un recurso S3 separado.
2. Comprobar migraciones y checksums en `flyway_schema_history`; si una BD tiene V1 aplicada con otro contenido, **no sobrescribir V1 ni editar el historial**: diseñar una migración posterior compatible.
3. Preparar copia de seguridad, ventana de cambio, credenciales de migración adecuadas, plan de reversión y verificación postdespliegue. No ensayar cambios sobre producción.
4. Revisar el scheduler `EmailVerificationCleanup`: borra cuentas no verificadas después de 24 h y los códigos caducados; la configuración de su cron se hereda del entorno en que corre el backend.

No consta un arranque del backend actual con V1 consolidada sobre Neon compartido. La evidencia previa `NeonConnectivityIT` solo comprobó JDBC y `HeadBucket` en `development` con Flyway/Hibernate desactivados en ese test.

## 6. Seguridad y variables externas

Consultar `backend/.env.example` para los nombres y comentarios de configuración. `backend/.env.production` no se carga en Compose; sus marcadores `#####` se completarán al preparar el despliegue y no son un fallo del desarrollo local. Son especialmente relevantes:

| Variable o familia                                    | Finalidad                                                                                   |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `SPRING_DATASOURCE_*`                                 | JDBC, usuario y contraseña específicos del entorno                                          |
| `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `STORAGE_BUCKET` | Endpoint, región y bucket de Object Storage del entorno                                     |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`          | Credenciales de Object Storage leídas por el SDK; no exponer al frontend                    |
| `JWT_SECRET`, `JWT_ISSUER`                            | Firma HS256 y emisor del JWT; el secreto se configura como Base64 suficientemente aleatorio |
| `EMAIL_VERIFICATION_SECRET`                           | HMAC independiente para códigos decimales de verificación                                   |
| `STAFF_INVITATION_SECRET`                             | HMAC independiente para códigos de invitación                                               |
| `MAIL_*`                                              | SMTP, cuenta remitente, autenticación y seguridad de transporte                             |
| `CORS_ALLOWED_ORIGINS`                                | Orígenes permitidos del cliente web                                                         |

Las claves HMAC y JWT deben ser independientes; **rotar `STAFF_INVITATION_SECRET` invalida los códigos de invitaciones pendientes** porque no existe soporte multiclave. Guardar secretos en un gestor seguro y restringir acceso; evitar logs, imágenes Docker, repositorio y valores de pruebas. Configurar HTTPS, proxy de confianza, cabeceras, CORS, transporte de JWT y condiciones de CSRF antes de publicar. El limitador en memoria usa la IP de conexión y no confía en cabeceras `Forwarded`: detrás de un proxy requerirá diseño seguro para reconocer clientes y cuotas compartidas si hay réplicas.

## 7. Mailpit, envío y limpieza

En desarrollo Mailpit expone la interfaz en `http://localhost:9999` (interno 8025), con SMTP interno `mailpit:1025`. Registro/verificación y emisión de invitaciones publican eventos atendidos `AFTER_COMMIT` por los listeners específicos de `auth/mail` e `invitations/mail`, ambos con el transporte de `shared/mail`. Si SMTP falla, el alta/verificación/reenvío o la invitación confirmada en SQL **no se revierte**. No se ha implementado outbox ni reintentos automáticos para invitaciones: el procedimiento actual es revocar y emitir otra.

La limpieza periódica `EmailVerificationCleanup` se ejecuta según el cron configurado (ordinariamente una vez por hora) con lotes y bloqueos `SKIP LOCKED`. `staff_invitations.email` **no** es FK a `users`, por lo que una invitación no canjeada sobrevive a la eliminación de una cuenta sin verificar. Otras referencias históricas usan `RESTRICT`; las futuras políticas de eliminación/retención deben definirse antes de producción.

## 8. Pruebas previas a un despliegue

Desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

El perfil incorpora catorce suites aisladas de autenticación, correo, invitaciones, departamentos, categorías, usuarios, pertenencias, incidencias, evidencias, Tramitación/instalación, Duplicados y Mapas funcional/rendimiento con PostgreSQL 18 de Testcontainers y **no** ejecuta `NeonConnectivityIT`. En el informe histórico fechado el **30/10/2026** constan **90 pruebas superadas** y un flujo HTTP adicional del JAR con PostgreSQL `tmpfs` (sin puerto publicado), Mailpit, y fixtures sintéticos: rutas, autorización, permisos, auditoría, cuotas, limpieza y entrega local. El servicio de pruebas utilizaba `127.0.0.1:18888` y Mailpit `127.0.0.1:18025`; el cron y las cuotas de fixtures estaban ajustados exclusivamente para tests.

**Pruebas superadas y reproducibilidad:** la entrega de Invitaciones registró **90 pruebas exitosas** mediante Maven/Testcontainers y un recorrido HTTP aislado con resultado satisfactorio. El perfil `-Ppostgres-it` sigue configurado en `pom.xml`. El repositorio no contiene `compose.test.yaml`, `backend/Dockerfile.test` ni `backend/scripts/invitations_http_test.py`; por ello, el recorrido HTTP ejecutado no es reproducible **con este árbol de archivos**, aunque su resultado ya quedó documentado. [Guía de desarrollo](development.md).

## 9. CI implementada y despliegue pendiente

El [workflow CI](../.github/workflows/ci.yml) valida PR hacia main, push a main y ejecución manual mediante jobs independientes de backend, frontend y OpenAPI/Compose. Usa PostgreSQL Testcontainers y no accede a Neon ni a secretos productivos. No publica imágenes ni despliega. La [guía de desarrollo](development.md#5-ci-y-comprobaciones-locales) detalla los comandos y exclusiones.

Quedan para una **fase posterior de despliegue y producto** la automatización CD, el análisis periódico de dependencias y secretos, el control de migraciones, backups/restauración, observabilidad, imágenes productivas endurecidas, dominios/TLS, entrega SMTP externa, procedimiento del **primer ADMIN**, retirada de permisos y política de conservación de invitaciones/auditoría (RGPD). Derivación, ejecución e integraciones avanzadas de incidencias pertenecen a entregas futuras; revisión/respuestas/prioridad manual están implementadas; no se deben implementar tablas por aparecer en el diseño sin revisar el tag.

## 10. Referencias

- [Arquitectura y componentes](architecture.md), [desarrollo y pruebas](development.md) y [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md).
- [Diagramas existentes](diagrams/README.md) y [ADR](adr/README.md).
- [Autenticación](auth-design.md), [API OpenAPI](api/README.md) y [seguridad del proyecto](../SECURITY.md).

V1 incluye las restricciones finales y solo se aplica a un destino vacío sin historial Flyway; no constituye una actualización de bases existentes. La ETag de detalle es opaca y depende del JSON completo: cualquier proxy que transforme o comprima representaciones deberá mantener validadores correctos por variante. La configuración actual no habilita compresión de respuestas; no se ha desplegado sobre Neon.
