# UrbanPulse

UrbanPulse es una **plataforma cloud para la gestión inteligente de incidencias urbanas**. Su finalidad es centralizar los reportes realizados por la ciudadanía y enriquecerlos con información contextual para mejorar su clasificación, priorización, seguimiento y resolución.

La aplicación permitirá registrar incidencias (por ejemplo, semáforos averiados o desperfectos en la vía pública), consultar su evolución y facilitar al personal municipal su validación, asignación y gestión. El proyecto se encuentra **en desarrollo**: estas funcionalidades describen el alcance previsto, no funciones ya disponibles.

## Estado del proyecto

- **Frontend:** base universal Expo/React Native creada; pantalla inicial ejecutable en web. Se han validado TypeScript, ESLint, Expo Doctor, exportación web y desarrollo web con Docker Compose. La ejecución nativa en dispositivos y la imagen web de producción están pendientes de validación.
- **Backend:** Java/Spring Boot con autenticación y gestión de departamentos y categorías implementadas localmente. La autenticación incluye registro, login, cambio de contraseña y verificación de correo, con migración Flyway V1. La gestión de departamentos incluye listado, consulta, creación y actualización parcial, con autorización por correo verificado y rol ADMIN para las operaciones de escritura, y migración Flyway V2. Categorías añade las mismas cuatro operaciones con migración V3, nombre único y sin código corto. Las pruebas de estas funcionalidades se han superado en PostgreSQL efímero mediante Testcontainers. Se ha comprobado anteriormente la conectividad JDBC con Neon y el acceso a Object Storage, pero no se ha validado el despliegue de estas migraciones en Neon `development` ni en `production`. Siguen pendientes los endpoints de incidencias y los demás módulos de negocio.
- **Infraestructura:** Docker Compose exclusivo para desarrollo, con frontend y backend históricamente comprobados y servicio SMTP local Mailpit ahora añadido (sin prueba conjunta backend+Mailpit). Neon PostgreSQL 18 y Neon Object Storage están aprovisionados en Frankfurt, con ramas `development` y `production`. El despliegue real de producción sigue pendiente.

## Tecnologías

| Parte                     | Tecnologías                                                                      |
| ------------------------- | -------------------------------------------------------------------------------- |
| Frontend                  | Expo SDK 57, React Native, React Native Web, React, TypeScript, Expo Router      |
| Herramientas frontend     | Node.js 24 (en Docker), npm, Metro, ESLint y Prettier                            |
| Backend                   | Java 25, Spring Boot 4.1.2, Maven 3.9.16, Spring Security, JPA/Hibernate, Flyway |
| Persistencia              | Neon PostgreSQL 18 (conexión de desarrollo comprobada)                           |
| Evidencias                | Neon Object Storage (bucket privado; acceso probado)                             |
| Desarrollo                | Docker, Docker Compose y VSCode                                                  |
| Distribución web prevista | Exportación SPA de Expo y Nginx en contenedor independiente                      |

## Instalación

Requisitos: Git, Docker y Docker Compose v2. Para trabajar sin contenedores: Node.js compatible con Expo SDK 57 (en las imágenes se utiliza Node.js 24), npm y, para el backend, JDK 25 y Maven 3.9.16 o compatibles.

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
```

El repositorio es **público**, por lo que su lectura y clonación no requieren iniciar sesión. Para proponer cambios se seguirá el flujo descrito en [CONTRIBUTING.md](CONTRIBUTING.md).

Para el frontend sin Docker:

```bash
cd frontend
npm ci
```

`frontend/package-lock.json` debe estar versionado. Si el lockfile no existe todavía, generarlo una vez mediante `npm install` antes de utilizar `npm ci`.

Para preparar las variables locales desde la raíz del repositorio:

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env.development
```

En Bash se puede utilizar `cp` en lugar de `Copy-Item`. Rellena las credenciales PostgreSQL y S3, `JWT_SECRET`, `JWT_ISSUER` y `CORS_ALLOWED_ORIGINS` **solo en `backend/.env.development`**, sin versionarlas. El backend debe utilizar la rama `development` de Neon y no compartir contraseñas con `production`. La base de datos no es necesaria para arrancar únicamente el frontend.

## Ejecución (desarrollo)

**Frontend web sin Docker**, desde `frontend/`:

```bash
npx expo start --web --port 7777
```

Abrir <http://localhost:7777>. La pantalla actual es una prueba de arranque, no la aplicación funcional de incidencias.

**Frontend web con Docker Compose**, desde la raíz:

```bash
docker compose up --build frontend
```

Abrir <http://localhost:7777>. Para detenerlo, `Ctrl+C` o `docker compose down` desde otra terminal.

**Frontend y backend en Docker Compose** (comprobado el 15/10/2026), desde la raíz:

```bash
docker compose up --build
```

El backend escucha en el puerto `8888` y utiliza las variables de entorno para configurar su conexión a PostgreSQL. En el código están implementados los cinco endpoints de autenticación y cuatro endpoints por catálogo, departamentos y categorías (`GET` listado y detalle, `POST` y `PATCH`). Todavía no existen los endpoints de incidencias.

Al arrancar, Flyway puede aplicar las migraciones V1 (autenticación), V2 (departamentos) y V3 (categorías) sobre la base configurada. Antes de ejecutar el backend contra Neon, verificar que se utiliza la rama `development`, nunca `production`, y comprobar el historial y los checksums de las migraciones existentes. Las tres migraciones se han validado conjuntamente en PostgreSQL efímero, pero no se ha comprobado su aplicación en Neon `development`. Consulta [la guía de desarrollo](docs/development.md).

La aplicación móvil se probará posteriormente con Expo Go o una compilación de desarrollo, utilizando un dispositivo/emulador y una URL de API accesible desde él.

## Pruebas y validación

**Última verificación backend (02/11/2026):** `mvn -B -ntp verify -Ppostgres-it` en Docker, sin cargar credenciales de Neon: **108 pruebas superadas** (77 unitarias/MVC y 31 PostgreSQL), incluidas categorías, departamentos, autenticación y correo. OpenAPI válido con dos advertencias previas; fuentes y exportaciones de Category actualizadas. [Comando reproducible, resultados y límites](docs/categories-finalization.md).

Desde `frontend/`:

```bash
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

Estas comprobaciones **se ejecutaron satisfactoriamente el 13/10/2026**; Expo Doctor informó de 21/21 comprobaciones superadas. Se validaron también el arranque de Expo Web y la imagen de **desarrollo** de Compose. La exportación web genera `frontend/dist/` (ignorado por Git).

Para los módulos de autenticación, verificación de correo y departamentos se dispone de pruebas unitarias/MVC y de integración sobre PostgreSQL 18 efímero mediante Testcontainers. La verificación histórica de Departamentos (31/10/2026) se ejecutó desde la raíz del proyecto mediante Docker Compose:

```bash
docker compose run --rm --no-deps -v /tmp/urbanpulse-departments-build:/app/target -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal backend mvn -B -ntp verify -Ppostgres-it
```

Resultado: **BUILD SUCCESS, 80 pruebas superadas** (55 unitarias/MVC y 25 de integración PostgreSQL), sin fallos, errores ni pruebas omitidas. Se utilizó PostgreSQL aislado; no se ejecutaron estas pruebas contra Neon. Los escenarios y resultados completos se documentan en el [informe de cierre de departamentos](docs/departments-finalization.md).

La prueba puntual `NeonConnectivityIT` se ha superado contra Neon `development` (PostgreSQL y `HeadBucket` de Object Storage):

```bash
docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test
```

No se ejecuta con el arranque ordinario de Compose ni forma parte de los tests descubiertos por defecto por Surefire (`*IT`); se lanza **expresamente** para no consumir cuota innecesaria. Las pruebas de autenticación se realizan en memoria y en PostgreSQL efímero mediante Testcontainers, sin Neon. Se incorporarán tests de dominio a medida que exista funcionalidad. Los avisos de `npm audit` se revisarán aparte: una compilación correcta no implica ausencia de vulnerabilidades.

## Configuración

**API disponible en el código:**

- **Autenticación:** `POST /api/v1/auth/register` (`201`), `POST /api/v1/auth/login` (`200`), `POST /api/v1/users/me/password-changes` (`204`), `POST /api/v1/auth/email-verifications/confirm` (`204`) y `POST /api/v1/auth/email-verifications/resend` (`202`/`204`).
- **Departamentos:** `GET /api/v1/departments` (`200`), `GET /api/v1/departments/{departmentId}` (`200`), `POST /api/v1/departments` (`201`) y `PATCH /api/v1/departments/{departmentId}` (`200`).
- **Categorías:** `GET /api/v1/categories` (`200`), `GET /api/v1/categories/{categoryId}` (`200`), `POST /api/v1/categories` (`201`, Location) y `PATCH /api/v1/categories/{categoryId}` (`200`). Nombre único de 2–100 caracteres tras trim, descripción opcional hasta 500, active y UUIDv7; sin código corto. [Informe de implementación y pruebas actuales](docs/categories-finalization.md).

La autenticación utiliza JWT HS256 con una duración de 7200 segundos. Las operaciones de departamentos y categorías requieren autenticación y correo verificado; la creación y actualización exigen además el rol global `ADMIN`. La desactivación se realiza mediante PATCH, sin eliminar físicamente el registro.

La confirmación del correo habilita el acceso con el JWT existente. El cambio de contraseña revoca los JWT anteriores. Los fallos tipados de disponibilidad de PostgreSQL responden `503`.

Consulta el [diseño de autenticación](docs/auth-design.md) y el [informe de cierre de departamentos](docs/departments-finalization.md) para conocer las reglas, pruebas y limitaciones de ambos módulos.

- `frontend/.env`: `EXPO_PUBLIC_API_URL=http://localhost:8888` para desarrollo web local. Las variables `EXPO_PUBLIC_*` **son públicas** dentro del cliente; nunca incluir secretos.
- `backend/.env.development` (privado): JDBC (`SPRING_DATASOURCE_*`), acceso S3 (`AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`), `JWT_SECRET` (Base64 de al menos 32 bytes aleatorios), `JWT_ISSUER`, `CORS_ALLOWED_ORIGINS`, `EMAIL_VERIFICATION_SECRET` y variables SMTP `MAIL_*` (sin secretos reales en Git). Los límites comunes se declaran en `application.properties`. Para producción, inyectar secretos y URLs de `production` mediante el proveedor de despliegue.
- `backend/src/main/resources/application.properties`: puerto 8888, JDBC, HikariCP, JPA, Flyway, `storage.s3.*`, JWT, SMTP, limpieza programada y políticas de rate limiting. `StorageProperties` configura el cliente S3. Flyway crea tablas mediante migraciones y `ddl-auto=validate` solo verifica el mapeo.
- `.editorconfig`, `.prettierrc.json` y `.vscode/`: convenciones del proyecto.

**Nota de redes:** `localhost` en un teléfono no apunta al ordenador; hay que utilizar la IP accesible del host o una API desplegada. Para web, el navegador no resuelve automáticamente el nombre Docker `backend`.

## Despliegue

**No hay un despliegue de producción validado.** Los módulos de autenticación, departamentos y categorías están implementados y sus migraciones Flyway V1, V2 y V3 se han verificado conjuntamente en PostgreSQL efímero mediante Testcontainers. Sin embargo, no consta validada su aplicación en Neon `development` ni su despliegue en `production`. Antes de aplicar las migraciones en Neon, es necesario comprobar el entorno de destino y el historial y los checksums de Flyway, especialmente los correspondientes a V1.

La estrategia acordada es desplegar la web SPA y la API REST en contenedores **independientes**, mientras Android/iOS se distribuyen como aplicaciones nativas. `compose.yaml` es exclusivamente de desarrollo.

El `frontend/Dockerfile` prepara una compilación web con Expo y Nginx; todavía debe probarse como imagen de producción y recibir `EXPO_PUBLIC_API_URL` al construir.

El backend dispone de un Dockerfile multietapa. Su arranque previo en desarrollo y la conectividad con Neon están comprobados. Los módulos de autenticación, departamentos y categorías, incluidas las migraciones Flyway V1, V2 y V3, se han verificado en PostgreSQL efímero mediante Testcontainers. Sin embargo, sigue pendiente comprobar su aplicación en Neon `development` y validar la imagen y el despliegue de producción.

El proveedor de hosting de ambos contenedores sigue sin decidirse. Más detalles: [docs/deployment.md](docs/deployment.md).

## Contribución

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para ramas, commits, PR y revisiones. La documentación de arquitectura, desarrollo y ADR se encuentra en [docs/](docs/README.md).

## Licencia

**Pendiente de decisión:** el repositorio es público, pero actualmente no dispone de archivo `LICENSE`. La publicación del código no concede por sí misma una licencia de reutilización. No se ha seleccionado todavía una licencia para el proyecto.

### Correo en desarrollo (Mailpit)

`compose.yaml` incorpora `mailpit`: visor local en <http://localhost:8025> y SMTP en `mailpit:1025` desde el contenedor backend (o `localhost:1025` desde el equipo anfitrión). Es un sumidero de pruebas: **no envía emails reales**, no requiere dominio ni cuenta de proveedor. Completar `MAIL_HOST`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, `MAIL_FROM=no-reply@urbanpulse.test` y `EMAIL_VERIFICATION_SECRET` privado de al menos 32 bytes aleatorios Base64 en `backend/.env.development`. Si se ejecuta el backend fuera de Compose, ajustar `MAIL_HOST=localhost`. Nunca reutilizar `JWT_SECRET` como clave HMAC de verificación.

La V1 se amplió **antes de su primera aplicación**; no debe ejecutarse sobre una BD que ya contenga otra V1 con ese número y checksum. No se ha probado la ejecución completa de backend+Mailpit en Compose; Codex comprobó los módulos por separado.
