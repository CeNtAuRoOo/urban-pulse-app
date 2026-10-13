# Guía de desarrollo de UrbanPulse

> **Estado (13/10/2026):** la base de **frontend web** está operativa en local y Docker Compose. El backend contiene el proyecto Spring Boot y su configuración inicial, pero sigue sin endpoints ni integración real con Neon. Esta guía diferencia comandos **comprobados** de procedimientos **pendientes**.

## 1. Requisitos

- Git, VSCode o editor equivalente, Docker Engine/Desktop y Docker Compose v2.
- Frontend sin Docker: Node.js compatible con Expo SDK 57, npm; los Dockerfiles utilizan **Node.js 24**.
- Backend sin Docker: JDK 25 y Maven 3.9.16 o compatibles.
- Acceso a credenciales de Neon PostgreSQL únicamente cuando se vaya a ejecutar realmente el backend.

El repositorio es **público**. Clonarlo no requiere autenticación; enviar cambios sí exige los permisos o el flujo de contribución correspondiente.

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
code .
```

`.editorconfig`, `.prettierrc.json` y `.vscode/` establecen convenciones compartidas. Para Java se usa el formateador de la extensión de Java; para TypeScript, Prettier.

## 2. Organización del repositorio

```text
urban-pulse-app/
├── .github/                      # Plantillas; workflows pendientes
├── .vscode/                      # Recomendaciones/configuración compartida
├── backend/
│   ├── src/main/java/es/grupo18/urbanpulse/
│   │   ├── BackendApplication.java
│   │   └── config/, controller/, dto/, entity/, repository/, service/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/         # Sin migración SQL funcional
│   ├── src/test/
│   ├── pom.xml
│   ├── Dockerfile
│   └── Dockerfile.dev
├── frontend/
│   ├── assets/
│   ├── src/app/_layout.tsx        # Expo Router
│   ├── src/app/index.tsx          # Pantalla inicial
│   ├── src/components/, config/, constants/, features/, services/, types/
│   ├── app.json, package.json, package-lock.json, tsconfig.json
│   ├── eslint.config.js, nginx.conf
│   ├── Dockerfile
│   └── Dockerfile.dev
├── docs/
├── compose.yaml                  # Solo entorno de desarrollo
└── README.md
```

`package-lock.json` debe conservarse bajo control de versiones para que `npm ci` y Docker sean reproducibles. Los directorios vacíos pueden contener `.gitkeep`.

## 3. Variables de entorno

Cada servicio dispone de un archivo `.env.example` versionado. El `.env` real se ignora en Git y nunca debe contener datos públicos por accidente.

Desde la raíz en PowerShell:

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env
```

En Bash, usar `cp` de manera equivalente.

### Frontend (`frontend/.env`)

```dotenv
EXPO_PUBLIC_API_URL=http://localhost:8888
```

Es la dirección prevista para **el navegador en el mismo ordenador**. `EXPO_PUBLIC_*` queda disponible en el JavaScript cliente y **no puede almacenar contraseñas, tokens privados o claves de Neon**. Todavía no existe una integración HTTP en el frontend; esta URL está preparada para cuando se implemente.

En un móvil físico `localhost` designa el propio teléfono; debe utilizarse una dirección del equipo accesible por LAN o la API HTTPS publicada. En el emulador Android habitual puede emplearse `10.0.2.2` para acceder al equipo anfitrión, sujeto a configuración de red y seguridad de la plataforma.

### Backend (`backend/.env`)

```dotenv
SERVER_PORT=8888
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/DATABASE?sslmode=require
SPRING_DATASOURCE_USERNAME=USER
SPRING_DATASOURCE_PASSWORD=PASSWORD
```

**Sustituir** `HOST`, `DATABASE`, `USER`, `PASSWORD` por datos del proyecto Neon; son marcadores de posición. No están configurados de forma operativa. `application.properties` utiliza estas variables. Docker Compose las entrega al contenedor mediante `env_file`; ejecutar Spring Boot directamente con Maven **no carga por sí solo** el archivo `.env` convencional, de modo que hay que proporcionar las variables al proceso de otro modo.

## 4. Frontend: instalación y ejecución local (comprobado)

Desde `frontend/`:

```bash
npm ci
npx expo start --web --port 7777
```

Acceder a <http://localhost:7777>. Expo utilizará **Metro**, Expo Router y React Native Web. El código de arranque está en `src/app/index.tsx`; el layout de navegación, en `src/app/_layout.tsx`. `Ctrl+C` detiene el servidor.

La aplicación dispone de scripts `npm start`, `npm run web`, `npm run android`, `npm run ios`, `npm run lint`. `npm run web` no fija el puerto 7777 por sí mismo; para respetarlo se ha validado el comando explícito anterior.

Para pruebas de Android/iOS, se podrá usar `npm start` con Expo Go o un cliente de desarrollo. **No se ha verificado aún una sesión completa en un dispositivo**. El código Android sí llegó a empaquetarse durante una prueba de Expo; no equivale a validar la aplicación en Android/iOS.

## 5. Frontend: desarrollo web con Docker Compose (comprobado)

Desde la raíz del repositorio:

```bash
docker compose up --build frontend
```

El servicio `frontend` usa `frontend/Dockerfile.dev` y expone **7777:7777**. Monta el código con `./frontend:/app` y las dependencias Linux con el volumen `node-modules:/app/node_modules`. Abre <http://localhost:7777>.

Otros comandos útiles:

```bash
docker compose ps
docker compose logs -f frontend
docker compose down
```

Los cambios de los archivos locales se propagan mediante el volumen; Expo normalmente realiza **Fast Refresh**, aunque el comportamiento de edición continua no se ha medido formalmente en todos los entornos Windows/Docker. Si cambian las dependencias, además del lockfile puede ser necesario actualizar el volumen:

```bash
docker compose exec frontend npm ci
docker compose restart frontend
```

Se recomienda probar el frontend **aislado** con `docker compose up --build frontend`, no ejecutar automáticamente todo Compose mientras el backend continúe sin conexión de base de datos funcional.

La prueba Docker del 13/10/2026 terminó con compilación y ejecución del frontend. El aviso de descarga de React Native DevTools usó una versión de respaldo y no impidió el arranque.

## 6. Frontend: comprobaciones de calidad (comprobado)

Desde `frontend/`:

```bash
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

**Resultados del 13/10/2026:** dependencias compatibles; Expo Doctor **21/21**; TypeScript y ESLint sin errores; exportación web correcta a `dist/`. La exportación es una **compilación** de archivos estáticos, no prueba de la imagen Nginx ni de un despliegue externo. `dist/` está ignorado por Git.

Las herramientas de Expo generan archivos temporales como `expo-env.d.ts`, ignorado en `frontend/.gitignore` por Expo CLI. Las versiones concretas de las bibliotecas se consultan en `frontend/package.json` y `frontend/package-lock.json`.

**Seguridad de dependencias:** `npm install` había informado de avisos de `npm audit` en una instalación anterior. No se ha adjuntado un informe actualizado tras los cambios; por tanto no debe declararse resuelto. Revisar:

```bash
npm audit
npm audit --omit=dev
npm explain uuid
```

Evitar `npm audit fix --force` sin comprobar compatibilidad con Expo SDK 57.

**No hay aún pruebas unitarias o E2E de funcionalidades**, ni clientes API reales. Estas pruebas se incorporarán con los primeros casos de uso.

## 7. Backend: base y ejecución pendiente

`backend/pom.xml` contiene Spring Boot 4.1.1, Java 25, Web MVC, Validation, Spring Data JPA, PostgreSQL, Flyway, Spring Security con soporte para validar JWT, Lombok y dependencias de test.

`BackendApplication.java` está creada y correctamente situada para el escaneo de subpaquetes. `application.properties` configura `SERVER_PORT` (por defecto 8888), JDBC por entorno, HikariCP, Flyway y `spring.jpa.hibernate.ddl-auto=validate`; no hay perfil `dev` adicional. **No existen todavía endpoints de dominio ni pruebas automáticas backend.**

Al provisionar Neon y crear la primera migración real se podrán probar:

```bash
cd backend
mvn test
mvn verify
mvn spring-boot:run
```

Desde Compose, una vez listo:

```bash
docker compose up --build backend
```

**Estos comandos no se han validado aún.** El backend puede no arrancar sin PostgreSQL accesible, el esquema adecuado y la configuración de seguridad requerida.

## 8. Producción web (configuración creada, prueba pendiente)

El `frontend/Dockerfile` exporta una SPA de Expo y copia `dist/` a Nginx; `frontend/nginx.conf` escucha en el puerto 7777 y redirige rutas de Expo Router a `index.html`.

Para comprobar **localmente** esa imagen en una fase posterior, desde la raíz, en una sola línea compatible con PowerShell:

```powershell
docker build -f frontend/Dockerfile --build-arg EXPO_PUBLIC_API_URL=http://localhost:8888 -t urbanpulse-frontend:local frontend
docker run --rm -p 7777:7777 urbanpulse-frontend:local
```

Esto **no figura en las pruebas adjuntadas**. El argumento `EXPO_PUBLIC_API_URL` queda integrado en el cliente en tiempo de compilación. La URL debe ser pública/HTTPS en producción y el backend deberá autorizar el origen mediante su política CORS.

## 9. Colaboración y PR

Se sigue el flujo [CONTRIBUTING.md](../CONTRIBUTING.md): una rama por cambio, commits descriptivos, pruebas y Pull Request hacia `main`. Para un cambio mixto backend/frontend:

```bash
git status
git diff --check
git add .
git diff --cached --name-only
git commit -m "docs: actualizar guías de desarrollo"
```

Comprobar que no se han añadido secretos, datos personales reales ni directorios generados (`node_modules`, `dist`, `target`, `.env`). Confirmar además que `frontend/package-lock.json` esté versionado: `git ls-files frontend/package-lock.json`.

## 10. Problemas frecuentes

- `expo/tsconfig.base` no encontrado: comprobar `node_modules` y `npm ci`, abrir `frontend/` en VSCode y reiniciar el servidor TypeScript.
- Error JSX tras el anterior: normalmente desaparece cuando TypeScript resuelve la configuración Expo.
- `localhost` falla en móvil: utilizar la IP de la máquina alcanzable desde el dispositivo o una URL pública; comprobar firewall/HTTPS.
- Puerto 7777 ocupado: detener el servidor Expo/Docker anterior antes de lanzar otro.
- Docker actualiza el código pero no las dependencias: sincronizar el volumen `node-modules` con `npm ci`.
- Backend falla sin Neon: comprobar `.env`, conexión JDBC/TLS, esquema Flyway y configuración del backend; no confundir con un error del frontend.
- Clonación: la lectura del repositorio público no requiere permisos; para enviar cambios sí hay que autenticar y respetar GitHub Flow.

## 11. Próximas pruebas y referencias

Pendientes: imagen Nginx de producción, arranque backend, tests con PostgreSQL, app móvil en dispositivo, Fast Refresh sistemático, tests de integración y workflows GitHub Actions.

- [Arquitectura](architecture.md)
- [Despliegue](deployment.md)
- [API](api/README.md)
- [ADR](adr/README.md)
