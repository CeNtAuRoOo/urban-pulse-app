# UrbanPulse

UrbanPulse es una **plataforma cloud para la gestión inteligente de incidencias urbanas**. Su finalidad es centralizar los reportes realizados por la ciudadanía y enriquecerlos con información contextual para mejorar su clasificación, priorización, seguimiento y resolución.

La aplicación permitirá registrar incidencias (por ejemplo, semáforos averiados o desperfectos en la vía pública), consultar su evolución y facilitar al personal municipal su validación, asignación y gestión. El proyecto se encuentra **en desarrollo**: estas funcionalidades describen el alcance previsto, no funciones ya disponibles.

## Estado del proyecto

- **Frontend:** base universal Expo/React Native creada; pantalla inicial ejecutable en web. Se han validado TypeScript, ESLint, Expo Doctor, exportación web y desarrollo web con Docker Compose. La ejecución nativa en dispositivos y la imagen web de producción están pendientes de validación.
- **Backend:** Java/Spring Boot arranca correctamente con Docker Compose y se ha verificado la conectividad JDBC con Neon PostgreSQL y el acceso al bucket privado mediante el SDK S3. **Módulo de autenticación implementado localmente**, con registro, login y cambio de contraseña, tres entidades de identidad y migración Flyway V1; **sin endpoints de incidencias ni demás operaciones de negocio**. La migración se ha probado en PostgreSQL efímero, pero no consta aplicada a Neon `development`.
- **Infraestructura:** Docker Compose exclusivo para desarrollo, con frontend y backend arrancados conjuntamente. Neon PostgreSQL 18 y Neon Object Storage están aprovisionados en Frankfurt, con ramas `development` y `production`. El despliegue real de producción sigue pendiente.

## Tecnologías

| Parte                     | Tecnologías                                                                      |
| ------------------------- | -------------------------------------------------------------------------------- |
| Frontend                  | Expo SDK 57, React Native, React Native Web, React, TypeScript, Expo Router      |
| Herramientas frontend     | Node.js 24 (en Docker), npm, Metro, ESLint y Prettier                            |
| Backend                   | Java 25, Spring Boot 4.1.1, Maven 3.9.16, Spring Security, JPA/Hibernate, Flyway |
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

El backend escucha en el puerto `8888` y conecta con Neon `development` mediante variables de entorno. Existen los tres endpoints de autenticación; **no existen todavía los endpoints de incidencias**. Al arrancar la nueva versión, Flyway puede aplicar `V1__create_authentication.sql` sobre la base configurada; **verificar previamente que es Neon `development`, nunca `production`**. Consulta [la guía de desarrollo](docs/development.md).

La aplicación móvil se probará posteriormente con Expo Go o una compilación de desarrollo, utilizando un dispositivo/emulador y una URL de API accesible desde él.

## Pruebas y validación

Desde `frontend/`:

```bash
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

Estas comprobaciones **se ejecutaron satisfactoriamente el 13/10/2026**; Expo Doctor informó de 21/21 comprobaciones superadas. Se validaron también el arranque de Expo Web y la imagen de **desarrollo** de Compose. La exportación web genera `frontend/dist/` (ignorado por Git).

La prueba puntual `NeonConnectivityIT` se ha superado contra Neon `development` (PostgreSQL y `HeadBucket` de Object Storage):

Para el módulo auth se dispone de tests unitarios/MVC y siete pruebas PostgreSQL 18 de integración, aisladas mediante Testcontainers. Desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

Codex documentó una ejecución anterior con **23 pruebas superadas** (16 unitarias/MVC y 7 de integración) el 25/10/2026. **La última revisión del código adjunto no equivale a una nueva ejecución:** repetir el comando y registrar su resultado antes del commit.

```bash
docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test
```

No se ejecuta con el arranque ordinario de Compose ni forma parte de los tests descubiertos por defecto por Surefire (`*IT`); se lanza **expresamente** para no consumir cuota innecesaria. Las pruebas de autenticación se realizan en memoria y en PostgreSQL efímero mediante Testcontainers, sin Neon. Se incorporarán tests de dominio a medida que exista funcionalidad. Los avisos de `npm audit` se revisarán aparte: una compilación correcta no implica ausencia de vulnerabilidades.

## Configuración

**API de autenticación disponible en el código:** `POST /api/v1/auth/register` (`201`), `POST /api/v1/auth/login` (`200`) y `POST /api/v1/users/me/password-changes` (`204`, exige Bearer). JWT HS256 de 7200 segundos; todos los JWT anteriores se revocan tras cambiar contraseña. Cuando PostgreSQL esté indisponible se responde `503`, no `401`. El correo no se verifica obligatoriamente aún. Ver [diseño e implementación](docs/auth-design.md).

- `frontend/.env`: `EXPO_PUBLIC_API_URL=http://localhost:8888` para desarrollo web local. Las variables `EXPO_PUBLIC_*` **son públicas** dentro del cliente; nunca incluir secretos.
- `backend/.env.development` (privado): JDBC (`SPRING_DATASOURCE_*`), acceso S3 (`AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`), `JWT_SECRET` (Base64 de al menos 32 bytes aleatorios), `JWT_ISSUER` y `CORS_ALLOWED_ORIGINS`. Los límites comunes se declaran en `application.properties`. Para producción, inyectar secretos y URLs de `production` mediante el proveedor de despliegue.
- `backend/src/main/resources/application.properties`: puerto 8888, JDBC, HikariCP, JPA, Flyway, `storage.s3.*`, JWT y políticas de rate limiting. `StorageProperties` configura el cliente S3. Flyway crea tablas mediante migraciones y `ddl-auto=validate` solo verifica el mapeo.
- `.editorconfig`, `.prettierrc.json` y `.vscode/`: convenciones del proyecto.

**Nota de redes:** `localhost` en un teléfono no apunta al ordenador; hay que utilizar la IP accesible del host o una API desplegada. Para web, el navegador no resuelve automáticamente el nombre Docker `backend`.

## Despliegue

**No hay un despliegue de producción validado.** La existencia del código de autenticación no demuestra el arranque con la nueva migración sobre Neon `development`, ni su despliegue en producción. La estrategia acordada es desplegar la web SPA y la API REST en contenedores **independientes**, mientras Android/iOS se distribuyen como aplicaciones nativas. `compose.yaml` es exclusivamente de desarrollo.

El `frontend/Dockerfile` prepara una compilación web con Expo y Nginx; todavía debe probarse como imagen de producción y recibir `EXPO_PUBLIC_API_URL` al construir. El backend dispone de un Dockerfile multietapa; el arranque previo **en desarrollo** y la conectividad con Neon están comprobados, pero **la puesta en marcha de la nueva V1 en Neon `development` y la imagen/despliegue de producción están pendientes de comprobación**. El proveedor de hosting de ambos contenedores sigue sin decidirse. Más detalles: [docs/deployment.md](docs/deployment.md).

## Contribución

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para ramas, commits, PR y revisiones. La documentación de arquitectura, desarrollo y ADR se encuentra en [docs/](docs/README.md).

## Licencia

**Pendiente de decisión:** el repositorio es público, pero actualmente no dispone de archivo `LICENSE`. La publicación del código no concede por sí misma una licencia de reutilización. No se ha seleccionado todavía una licencia para el proyecto.
