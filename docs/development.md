# Guía de desarrollo

> **Estado:** guía de preparación del repositorio. **Actualmente `backend/src` y `frontend/src` están vacíos y el frontend carece de Dockerfiles ejecutables**. Los comandos de ejecución y prueba que dependen del código se etiquetan como _futuros_.

## 1. Requisitos y herramientas

- Git y acceso autorizado al repositorio privado GitHub.
- VSCode u otro editor con soporte para Java, XML, YAML y TypeScript.
- Docker Engine/ Docker Desktop y Docker Compose v2.
- Si se trabaja fuera de contenedores: JDK 25 y Maven 3.9.16 (o versiones compatibles), además de Node.js/Expo **cuando se inicialice frontend**.
- Acceso de desarrollo a Neon PostgreSQL. Las credenciales se compartirán por canal seguro, nunca en Git.

La carpeta `.vscode/` contiene recomendaciones de extensiones y configuración de equipo; `.editorconfig` y `.prettierrc.json` establecen convenciones generales.

## 2. Obtener el código

Repositorio: `https://github.com/CeNtAuRoOo/urban-pulse-app.git` (confirmar si se mueve a una organización).

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
code .
```

Para un repositorio privado se necesita autenticación y permisos sobre GitHub. `git clone` configura automáticamente el remoto `origin`.

## 3. Organización principal

```text
urban-pulse-app/
├── .github/                 # Issues, PR y futuros workflows
├── .vscode/                 # Recomendaciones/configuración compartida
├── backend/                 # Spring Boot + Maven; src vacío por ahora
│   ├── src/
│   ├── .env.example
│   ├── Dockerfile
│   ├── Dockerfile.dev
│   └── pom.xml
├── frontend/                # Expo/React Native; pendiente de inicializar
│   ├── src/
│   ├── .env.example
│   ├── Dockerfile
│   └── Dockerfile.dev
├── docs/                    # Arquitectura, ADR, API y diagramas
├── compose.yaml             # Solo desarrollo
├── .editorconfig
├── .prettierrc.json
└── README.md
```

En el futuro, los directorios internos de cada aplicación se crearán en función de los requisitos, no como carpetas vacías sin contenido.

## 4. Variables de entorno

Los valores secretos se almacenan en `.env` privados de cada servicio, que están excluidos mediante `.gitignore`:

```bash
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

En PowerShell:

```powershell
Copy-Item backend/.env.example backend/.env
Copy-Item frontend/.env.example frontend/.env
```

- `backend/.env.example`: actualmente contiene `SERVER_PORT=8888` y placeholders comentados de PostgreSQL.
- `frontend/.env.example`: URL pública de la API para desarrollo **web** `http://localhost:8888`; deberá adaptarse a los dispositivos móviles.
- Nunca publicar claves JWT, contraseñas de Neon ni credenciales S3 en variables `EXPO_PUBLIC_*`; estas son accesibles desde el cliente.
- Compose usa `env_file` para cada contenedor. La existencia de una variable de entorno en el servicio no implica configuración completa de Spring Boot ni conexión automática con Neon hasta crear el código.

## 5. Desarrollo con Docker Compose (futuro)

`compose.yaml` define dos servicios y conserva dependencias en volúmenes:

| Servicio                  | Puerto local | Imagen                              |
| ------------------------- | ------------ | ----------------------------------- |
| frontend (desarrollo web) | 7777         | `frontend/Dockerfile.dev` pendiente |
| backend (API)             | 8888         | `backend/Dockerfile.dev`            |

Cuando ambos servicios estén inicializados y los `.env` configurados:

```bash
docker compose up --build
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

**No ejecutar ahora esperando una app funcional:** el frontend todavía no tiene configuración de Expo ni Dockerfile válido; el backend carece de clase principal y ajustes de conexión.

- Los cambios de código entrarán en los contenedores mediante volúmenes.
- La compilación de Java no se actualiza por guardar un archivo; se puede reiniciar con `docker compose restart backend` cuando el proyecto compile.
- Expo ofrece Fast Refresh para el frontend. El acceso desde teléfonos o emuladores puede requerir una dirección LAN, puertos adicionales o conexión de desarrollo diferente de la web en Docker; se documentará tras comprobarlo.

## 6. Desarrollo y pruebas sin Compose (futuro)

Con el backend implementado y configurado:

```bash
cd backend
mvn test
mvn verify
mvn spring-boot:run
```

- `mvn test` ejecutará las pruebas configuradas.
- `mvn verify` realizará las validaciones definidas en el proyecto, además de las fases previas.
- Una compilación Maven correcta no demuestra que la API funcione ni que exista una conexión válida a Neon.

Con el frontend inicializado, se documentarán los comandos reales del `package.json` para instalar dependencias, arrancar Expo, ejecutar pruebas y analizar el código. **No asumir nombres de scripts todavía.**

## 7. Base de datos y Flyway (futuro)

Neon alojará PostgreSQL. Al implementar el backend se creará:

```text
backend/src/main/resources/db/migration/
└── V1__create_initial_schema.sql
```

Las migraciones se añadirán por versiones (`V2__...sql`, etc.). Se ejecutarán contra el entorno correspondiente de manera controlada. Cuando exista un esquema, Hibernate se configurará con `spring.jpa.hibernate.ddl-auto=validate`; no se utilizará `update` para gestionar cambios.

No modificar scripts Flyway que ya se hayan aplicado a una base compartida. No ejecutar una migración experimental contra datos de otros compañeros.

## 8. Flujo de trabajo Git y Pull Requests

No implementar cambios directamente en `main`. Para cada tarea:

```bash
git switch main
git pull
git switch -c feature/nombre-funcionalidad
# trabajar y probar
git add .
git commit -m "feat(backend): describir cambio"
git push -u origin feature/nombre-funcionalidad
```

Después, abrir un Pull Request hacia `main`, describir pruebas, asociar el issue cuando corresponda y obtener la revisión definida por el equipo.

Convención acordada: `feat`, `fix`, `refactor`, `test`, `docs`; se admite `ci` para cambios de pipelines (ya aparece en `CONTRIBUTING.md`). El scope `(backend)` o `(frontend)` es opcional para cambios generales. Consultar [CONTRIBUTING.md](../CONTRIBUTING.md).

## 9. Formato y calidad

- Prettier para frontend y formatos compatibles, siguiendo `.prettierrc.json`.
- VSCode/Red Hat para Java; reglas básicas de whitespace e indentación en `.editorconfig`.
- Mantener DTO, controladores, repositorios y reglas de autorización coherentes con OpenAPI.
- No subir ficheros `target/`, `node_modules/`, `.env`, credenciales ni artefactos temporales.
- No utilizar datos personales reales en archivos de pruebas o capturas compartidas.

## 10. Comprobaciones antes de un PR

1. Revisar archivos modificados y confirmar que no hay secretos (`git diff`, `git status`).
2. Ejecutar las pruebas disponibles o documentar por qué todavía no aplican.
3. Actualizar docs/OpenAPI/ADR cuando el cambio afecte a contratos o decisiones.
4. Comprobar el formato de archivos editados y revisar warnings relevantes.
5. Describir explícitamente las limitaciones o tareas pendientes.

## 11. Problemas frecuentes

- **Acceso denegado al clonar:** revisar autenticación GitHub y permisos del repositorio privado.
- **Falta `backend/.env` o `frontend/.env`:** copiar `.env.example`; nunca subir el `.env` real.
- **El backend falla al conectar:** revisar variables JDBC, TLS y permisos de Neon; un servicio suspendido puede tardar en reactivarse.
- **`docker compose up` falla ahora:** normal; los Dockerfiles y `src` del frontend no están implementados.
- **El teléfono no llega a `localhost:8888`:** `localhost` en el dispositivo no es el equipo de desarrollo; usar un host accesible y una configuración por plataforma.

## 12. Documentación relacionada

- [Arquitectura](architecture.md)
- [Despliegue](deployment.md)
- [API](api/README.md)
- [ADR](adr/README.md)
