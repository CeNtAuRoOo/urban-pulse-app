# Guía de despliegue

> **Estado (15/10/2026):** Docker Compose inicia frontend y backend en desarrollo; el backend ha superado una prueba manual de conectividad con Neon PostgreSQL y Object Storage. **No existe todavía un despliegue de producción validado**, ni imagen final de frontend Nginx verificada. Hosting y dominios públicos pendientes.

## 1. Objetivo y separación de entornos

- **Desarrollo:** `compose.yaml` orquesta los servicios `frontend` (7777) y `backend` (8888) con volúmenes de código. PostgreSQL queda fuera de Compose, en Neon.
- **Producción web:** un contenedor Docker independiente sirve los archivos SPA exportados por Expo mediante Nginx.
- **Producción backend:** otro contenedor ejecutará el JAR de Spring Boot, una vez configurado y probado.
- **Android/iOS:** se generarán compilaciones nativas a partir del mismo frontend; **no se desplegarán en contenedores Docker** en los dispositivos.

Todo despliegue real deberá utilizar HTTPS hacia la API, secretos gestionados en el entorno y permisos mínimos.

## 2. Componentes y estado

| Componente                    | Artefacto / proveedor                               | Estado                                                            |
| ----------------------------- | --------------------------------------------------- | ----------------------------------------------------------------- |
| Frontend desarrollo web       | `frontend/Dockerfile.dev`, Expo/Metro, puerto 7777  | **Validado localmente y con Compose**                             |
| Exportación web               | `npx expo export --platform web` → `frontend/dist/` | **Validada**                                                      |
| Frontend producción web       | `frontend/Dockerfile` (Node 24 → Nginx)             | Creado; **imagen final no probada**                               |
| Backend desarrollo            | `backend/Dockerfile.dev`, Spring Boot, puerto 8888  | Creado; arranque en Compose comprobado                            |
| Backend producción            | `backend/Dockerfile` (Maven → JRE)                  | Creado; imagen de producción no verificada                        |
| PostgreSQL                    | Neon (administrado)                                 | PostgreSQL 18; JDBC de desarrollo verificado                      |
| Almacenamiento de fotografías | Neon Object Storage, bucket privado                 | Bucket privado y HeadBucket de desarrollo verificados             |
| Aplicaciones Android/iOS      | Expo / React Native                                 | Compilación/distribución por definir; sin prueba funcional nativa |
| Hosting web y backend         | Proveedores cloud distintos o separables            | Pendientes                                                        |

## 3. Diagrama de despliegue

**[PENDIENTE: incorporar C4 Deployment]** al seleccionar hosting, dominios, red, TLS y mecanismos de entrega. Archivo propuesto `docs/diagrams/deployment.drawio`; exportación SVG junto a él.

Los diagramas C4 actuales de contexto y contenedores se encuentran en [architecture.md](architecture.md).

## 4. Frontend web: desarrollo frente a producción

### 4.1. Desarrollo comprobado

Desde la raíz:

```bash
docker compose up --build frontend
```

El contenedor ejecuta `npx expo start --web --port 7777`; se accede a <http://localhost:7777>. La fuente se monta como volumen y las dependencias permanecen en un volumen separado. La aplicación web también se ha probado sin Docker con el mismo puerto.

### 4.2. Exportación comprobada

Desde `frontend/`:

```bash
npx expo export --platform web
```

La configuración `app.json` contiene `web.output="single"`. La exportación web genera `dist/`, con HTML, JavaScript y recursos estáticos, pero **no equivale a desplegarlo**.

### 4.3. Contenedor de producción (pendiente de validar)

`frontend/Dockerfile` es multietapa: instala con `npm ci`, exporta la aplicación y copia `dist/` a Nginx. `frontend/nginx.conf` escucha en el puerto 7777 y utiliza `try_files ... /index.html` para soportar rutas SPA de Expo Router.

Comandos propuestos de validación local desde la raíz, en PowerShell:

```powershell
docker build -f frontend/Dockerfile --build-arg EXPO_PUBLIC_API_URL=http://localhost:8888 -t urbanpulse-frontend:local frontend
docker run --rm -p 7777:7777 urbanpulse-frontend:local
```

Abrir <http://localhost:7777>. **Todavía no se ha adjuntado evidencia de esta prueba.**

### 4.4. Configuración pública en tiempo de build

`EXPO_PUBLIC_API_URL` debe proporcionarse al construir la imagen. Se incorpora al bundle JavaScript y **no es un secreto**. En producción reemplazar por el endpoint HTTPS verdadero de la API; por ejemplo `https://api.ejemplo.invalid`, una URL **ficticia, no operativa**. Si cambia la URL, la imagen actual necesitará reconstruirse o evolucionar hacia otro sistema de configuración pública en ejecución.

La API del backend deberá admitir el origen web mediante la política CORS que se defina. No usar el hostname Docker `backend` en código ejecutado por el navegador.

## 5. Backend Spring Boot: imagen independiente

`backend/Dockerfile` usa Maven 3.9.16 y Java 25 para compilar el JAR y una imagen con JRE 25 para ejecutarlo. Puerto interno previsto: **8888**.

Comando de referencia para la imagen **de producción, aún no validado**:

```bash
docker build -f backend/Dockerfile -t urbanpulse-backend:local backend
```

El backend tiene cinco operaciones de autenticación/verificación, pero **todavía no tiene API de negocio de incidencias**; el arranque real de esta nueva versión con Mailpit y Neon no consta verificado. La imagen final de producción y las comprobaciones de despliegue siguen pendientes; **ya existe una V1 ampliada para autenticación y verificación de correo**, probada en PostgreSQL efímero, pero no consta aplicada a Neon `development`. Durante la fase Maven `package` del Dockerfile se ejecutarán los tests Surefire habituales; el test manual `NeonConnectivityIT` (`*IT`) no se ejecuta por defecto.

En despliegue no incluir contraseñas ni claves mediante `ENV`, `ARG` o archivos versionados. Inyectar credenciales de Neon y claves de autenticación mediante mecanismos de secretos del proveedor de hosting.

## 6. Distribución móvil

Android e iOS reutilizan `frontend/src`. Expo permite iniciar un servidor para pruebas con Expo Go en bibliotecas compatibles o usar _development builds_ cuando se requieran módulos nativos adicionales. Las compilaciones finales serán artefactos móviles, no imágenes Docker.

**Pendientes:** pruebas reales en dispositivo, identificadores finales, permisos de ubicación/cámara, firma de apps y modalidad de distribución. El bundle de Android generado durante una sesión de Expo no demuestra que exista una aplicación Android ya validada.

Las aplicaciones móviles se conectarán a una URL HTTPS pública, no a `localhost` del ordenador de desarrollo.

## 7. Neon: PostgreSQL y fotografías

- **Proyecto** `urban-pulse-app`, **AWS Frankfurt**, PostgreSQL **18**, base `urban-pulse` en ramas Neon `production` (principal) y `development` (desarrollo). `production` no se puede proteger automáticamente con el plan gratuito disponible; extremar precauciones.
- **PostgreSQL:** JDBC/TLS desde Spring Boot mediante variables, con rol `backend`. La conexión directa a `development` está comprobada. Flyway habilitado y Hibernate con `ddl-auto=validate`; en `development` existía `flyway_schema_history` sin entidades. En el código ya existe `V1__create_authentication.sql` para `users`, `roles`, `user_roles` y `email_verifications`, probada en PostgreSQL efímero; **su ejecución sobre Neon `development` aún no está confirmada**. Verificar el destino y el plan de recuperación antes del primer arranque con Flyway activo.
- **Object Storage:** bucket `incident-evidence` **privado**, ramificado junto a los datos. El backend utiliza AWS SDK for Java 2.x y credenciales distintas por entorno. `HeadBucket` en `development` está comprobado; **no se han probado ni implementado todavía operaciones reales de fotografías ni URLs firmadas**.
- **Configuración de entorno:** Compose local carga `backend/.env.development` (ignorado por Git). En producción se inyectarán `SPRING_DATASOURCE_*`, `AWS_*`, `JWT_SECRET`, `JWT_ISSUER` y `CORS_ALLOWED_ORIGINS` como variables y secretos del proveedor, sin incrustar endpoints o credenciales en el Dockerfile. Nunca usar producción como destino de tests.
- **Backups y recuperación:** no hay un procedimiento de restauración validado ni una política de snapshots programados; la retención de historial de Neon es limitada (6 horas en la configuración comprobada). Definir copias verificables antes de emplear datos importantes.

## 8. Seguridad y configuración

El backend ya implementa JWT HS256 de 7200 s, BCrypt 12, comprobación de estado/versión/CITIZEN/roles actuales por petición, 503 para indisponibilidad de PostgreSQL, errores sin credenciales y límites locales contra abuso. **Pendiente antes del despliegue detrás de un proxy:** configurar confianza en el proxy e IP efectiva, ya que `server.forward-headers-strategy=none` usa la dirección de conexión y puede agrupar muchos clientes en el mismo bucket. Rotación de claves, TLS, dominios y CORS finales todavía no están validados.

| Dato                                  | Dónde debe estar                                                 |
| ------------------------------------- | ---------------------------------------------------------------- |
| URL pública de API                    | `EXPO_PUBLIC_API_URL` en compilación web / configuración cliente |
| JDBC, usuario y contraseña Neon       | Entorno seguro del backend, nunca frontend                       |
| Claves de firma/validación JWT        | `JWT_SECRET` Base64 externo de ≥32 bytes aleatorios              |
| Credenciales S3 y permisos del bucket | Backend / servicio seguro; jamás bundle cliente                  |
| Dominio público y CORS                | Configuración de hosting y backend por entorno                   |

La API y los archivos privados no deben ser accesibles saltándose la autorización del backend. Validar formatos y tamaños de fotografías, acceso y eliminación, y tratamiento de información personal. Las evidencias no se publicarán por defecto.

## 9. CI/CD y controles previos

Los workflows en `.github/workflows/` están pendientes. Propuesta al implementarlos:

1. En PR: instalar dependencias con lockfile y ejecutar `npx expo install --check`, `npx tsc --noEmit`, `npm run lint`, `npx expo export --platform web`; añadir después tests.
2. Backend: ejecutar `mvn test` / `mvn verify` con **tests unitarios aislados**, sin credenciales de Neon. No activar `NeonConnectivityIT` ni otras pruebas externas en CI por defecto; reservarlas a verificaciones manuales o entornos efímeros autorizados.
3. Verificar Dockerfiles de producción sin enviar secretos al build.
4. Publicar imágenes inmutables y desplegar web/backend de forma independiente, con comprobaciones posteriores.

Al fusionar un PR, los despliegues automáticos necesitarán autorización, credenciales seguras y permisos mínimos; no se activarán a ciegas.

## 10. Pruebas previas a un despliegue real

- Imagen web de Nginx funcionando con rutas SPA recargadas directamente.
- Acceso a la API por HTTPS desde web y móvil; CORS correcto.
- Backend inicia con la configuración de producción, y las migraciones aprobadas crean/validan el esquema esperado (sin probar sobre datos reales sensibles).
- JWT válidos e inválidos correctamente gestionados, con permisos comprobados.
- Evidencias privadas consultables solo tras autorización; subida y descarga controladas.
- Logs sin secretos; tratamiento de errores externos, revisión de vulnerabilidades.
- Plan de versiones y rollback de imágenes. Revertir una imagen **no revierte** automáticamente el esquema SQL.

## 11. Costes, riesgos y pendientes

Neon y el hosting tendrán límites de sus planes gratuitos; confirmar cuotas y política de suspensión antes de exponer el proyecto. No hay actualmente proveedores ni URLs finales para las aplicaciones web/backend. La política de copias de seguridad y recuperación permanece pendiente.

Falta además resolver la licencia del repositorio público, probar la app en dispositivos y crear los workflows. Estas tareas no impiden continuar el desarrollo local, pero **sí impiden considerar completado el despliegue de producción**.

## 12. Documentación relacionada

- [Arquitectura](architecture.md)
- [Guía de desarrollo](development.md)
- [ADR](adr/README.md)
- [Diagramas](diagrams/README.md)

## Correo y limpieza de cuentas pendientes

Docker Compose **de desarrollo** incluye Mailpit, cuya UI web está ligada a `127.0.0.1:8025` y su SMTP de host a `127.0.0.1:1025`; el backend accede internamente a `mailpit:1025`. Mailpit **no es el servicio de entrega de producción**. El SMTP real, su dominio, DNS (SPF/DKIM/DMARC), credenciales, límites, datos personales y obligaciones del proveedor siguen pendientes. `SmtpEmailSender` usa `JavaMailSender` y configuración mediante `MAIL_*`; el secreto HMAC `EMAIL_VERIFICATION_SECRET` es diferente de `JWT_SECRET` y debe guardarse como secreto del entorno.

El servicio `EmailVerificationCleanup` se ejecuta automáticamente según cron sobre **la base configurada en ese backend**: elimina códigos caducados y cuentas sin verificar con al menos 24 horas de antigüedad; la frecuencia ordinaria es horaria y usa lotes/`SKIP LOCKED`. Su activación junto con Flyway debe planificarse explícitamente; no arrancar una instancia conectada por error a `production`. Revisar el borrado **antes de incorporar nuevas claves foráneas hacia `users`**. No existe un outbox ni un mecanismo de reintento automático del correo; fallos SMTP no revierten el alta/reenvío.

La V1 de Flyway se cambió deliberadamente **antes de aplicarse por primera vez en Neon**. Si cualquier BD ya tiene registrada otra V1 con checksum distinto, no modificar su historial: detener despliegue y preparar una migración correctiva nueva. No interpretar una prueba con PostgreSQL efímero como aplicación validada de la V1 sobre Neon `development`.
