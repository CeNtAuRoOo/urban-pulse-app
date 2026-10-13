# UrbanPulse

UrbanPulse es una **plataforma cloud para la gestión inteligente de incidencias urbanas**. Su finalidad es centralizar los reportes realizados por la ciudadanía y enriquecerlos con información contextual para mejorar su clasificación, priorización, seguimiento y resolución.

La aplicación permitirá registrar incidencias (por ejemplo, semáforos averiados o desperfectos en la vía pública), consultar su evolución y facilitar al personal municipal su validación, asignación y gestión. El proyecto se encuentra **en desarrollo**: estas funcionalidades describen el alcance previsto, no funciones ya disponibles.

## Estado del proyecto

- **Frontend:** base universal Expo/React Native creada; pantalla inicial ejecutable en web. Se han validado TypeScript, ESLint, Expo Doctor, exportación web y desarrollo web con Docker Compose. La ejecución nativa en dispositivos y la imagen web de producción están pendientes de validación.
- **Backend:** estructura Java/Spring Boot y configuración PostgreSQL/Flyway preparadas, **sin endpoints ni migraciones funcionales**. El arranque y la integración con Neon todavía no se han validado.
- **Infraestructura:** Docker Compose exclusivo para desarrollo; despliegues independientes de frontend web y backend previstos para producción. Neon PostgreSQL es la elección de base de datos; Neon Object Storage permanece como propuesta.

## Tecnologías

| Parte                     | Tecnologías                                                                      |
| ------------------------- | -------------------------------------------------------------------------------- |
| Frontend                  | Expo SDK 57, React Native, React Native Web, React, TypeScript, Expo Router      |
| Herramientas frontend     | Node.js 24 (en Docker), npm, Metro, ESLint y Prettier                            |
| Backend                   | Java 25, Spring Boot 4.1.1, Maven 3.9.16, Spring Security, JPA/Hibernate, Flyway |
| Persistencia              | PostgreSQL administrado por Neon (decidido; integración pendiente)               |
| Evidencias                | Neon Object Storage (propuesto; integración pendiente)                           |
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
Copy-Item backend/.env.example backend/.env
```

En Bash se puede utilizar `cp` en lugar de `Copy-Item`. Rellena las credenciales de PostgreSQL **solo en `backend/.env`**, sin versionarlas. La base de datos no es necesaria para arrancar únicamente el frontend.

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

**Backend:** está preparado para utilizar el puerto `8888`, pero su ejecución requiere completar la conexión PostgreSQL, el esquema y la configuración funcional. No se considera validado todavía. Consulta [la guía de desarrollo](docs/development.md).

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

La ejecución de tests automáticos de funcionalidad frontend y las pruebas Maven/backend se incorporarán cuando existan implementaciones. Los avisos de `npm audit` se revisarán aparte: una compilación correcta no implica ausencia de vulnerabilidades.

## Configuración

- `frontend/.env`: `EXPO_PUBLIC_API_URL=http://localhost:8888` para desarrollo web local. Las variables `EXPO_PUBLIC_*` **son públicas** dentro del cliente; nunca incluir secretos.
- `backend/.env`: `SERVER_PORT=8888` y conexión a Neon mediante `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`.
- `backend/src/main/resources/application.properties`: puerto, PostgreSQL, HikariCP, JPA y Flyway; `ddl-auto=validate` no crea tablas.
- `.editorconfig`, `.prettierrc.json` y `.vscode/`: convenciones del proyecto.

**Nota de redes:** `localhost` en un teléfono no apunta al ordenador; hay que utilizar la IP accesible del host o una API desplegada. Para web, el navegador no resuelve automáticamente el nombre Docker `backend`.

## Despliegue

**No hay un despliegue de producción validado.** La estrategia acordada es desplegar la web SPA y la API REST en contenedores **independientes**, mientras Android/iOS se distribuyen como aplicaciones nativas. `compose.yaml` es exclusivamente de desarrollo.

El `frontend/Dockerfile` prepara una compilación web con Expo y Nginx; todavía debe probarse como imagen de producción y recibir `EXPO_PUBLIC_API_URL` al construir. El backend dispone de un Dockerfile multietapa, pendiente de una aplicación funcional y conexión a PostgreSQL. El proveedor de hosting de ambos contenedores sigue sin decidirse. Más detalles: [docs/deployment.md](docs/deployment.md).

## Contribución

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para ramas, commits, PR y revisiones. La documentación de arquitectura, desarrollo y ADR se encuentra en [docs/](docs/README.md).

## Licencia

**Pendiente de decisión:** el repositorio es público, pero actualmente no dispone de archivo `LICENSE`. La publicación del código no concede por sí misma una licencia de reutilización. No se ha seleccionado todavía una licencia para el proyecto.
