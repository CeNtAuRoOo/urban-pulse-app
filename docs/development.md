# Desarrollo local

## 1. Configuración privada

Se necesita Docker Engine/Desktop en ejecución y Docker Compose con `up --wait`. No hace falta instalar Java, Maven o Node en el host para arrancar la aplicación. El único archivo Compose es `compose.yaml`, exclusivamente de desarrollo, con el proyecto `urban-pulse-app`.

Copiar las plantillas **solo si no existen los archivos privados**. Desde la raíz, en PowerShell:

```powershell
if (!(Test-Path backend/.env.development)) { Copy-Item backend/.env.example backend/.env.development }
if (!(Test-Path frontend/.env)) { Copy-Item frontend/.env.example frontend/.env }
```

Completar los valores antes de arrancar. No se utiliza un `.env` en la raíz. `backend/.env.production` pertenece al futuro despliegue y Compose no lo carga; sus marcadores `#####` se completarán al preparar producción.

| Archivo                                   | Configuración                                                                                                |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `backend/.env.example`                    | Plantilla versionada de nombres y comentarios para ambos entornos; no contiene secretos.                     |
| `backend/.env.development`                | JDBC local, Neon Object Storage de desarrollo, JWT/HMAC, CORS y Mailpit. Privado, cargado por Compose.       |
| `backend/.env.production`                 | Neon PostgreSQL, Object Storage y SMTP productivos, con sus secretos propios. Privado, fuera de Compose.     |
| `frontend/.env.example` / `frontend/.env` | Plantilla versionada / configuración privada local del cliente. `EXPO_PUBLIC_API_URL=http://localhost:8080`. |

Compose fija el JDBC, el usuario y la contraseña del servicio PostgreSQL local. El backend y el cliente se configuran con los archivos privados de la tabla.

Configurar `MAIL_HOST=mailpit`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, usuario/contraseña SMTP vacíos y un remitente de desarrollo. `CORS_ALLOWED_ORIGINS=http://localhost:8888` autoriza el origen del navegador. La URL pública del frontend apunta al backend publicado, nunca a `http://backend:8080`; `localhost` y `127.0.0.1` son orígenes distintos.

Generar **tres claves Base64 independientes**, de al menos 32 bytes aleatorios, para `JWT_SECRET`, `EMAIL_VERIFICATION_SECRET` y `STAFF_INVITATION_SECRET`. Ejecutar tres veces y guardar cada resultado únicamente en el archivo privado:

```bash
docker run --rm node:24-bookworm-slim node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"
```

Completar también `JWT_ISSUER`. No reutilizar claves de tests ni productivas. Neon Object Storage requiere `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `STORAGE_BUCKET` del recurso de desarrollo autorizado. La base SQL local no aísla el bucket: no hacer pruebas mutativas compartidas sin un escenario preparado y autorización. El CI usa dobles S3 y no necesita esas credenciales.

## Arranque desde cero

Desde la raíz, sobre el proyecto de desarrollo cuyos datos se quieran descartar:

```powershell
docker compose down
docker compose up --build
```

PostgreSQL 18 guarda PGDATA bajo `/var/lib/postgresql` en un tmpfs de hasta 512 MiB. Detener el contenedor descarta los datos. Para recuperar toda la aplicación después de detener PostgreSQL, repetir el ciclo completo. Un `up` sobre servicios que siguen activos conserva los datos. Las cachés Maven y Node sobreviven a `down`. Un volumen SQL de la configuración anterior queda desconectado. Su checksum de V1 es anterior al reordenamiento del bloque provincial y reutilizarlo requiere una transición independiente.

El backend espera a PostgreSQL y ejecuta Flyway. El auxiliar comprueba V1 y el esquema durante un plazo acotado. Aplica el [SQL de desarrollo](../backend/dev/database/seed.sql) con psql y rollback ante error. Una marca confirmada en esa misma transacción permite repetir el arranque del auxiliar. El frontend espera su salida correcta. El auxiliar conecta al DNS interno fijo y el SQL exige la marca del servidor de desarrollo.

## Comprobar el arranque

```powershell
docker compose ps -a
docker compose logs db-seed
docker compose exec postgres psql -U urbanpulse -d urban-pulse -c "SELECT version, success FROM flyway_schema_history"
docker compose exec postgres psql -U urbanpulse -d urban-pulse -c "SELECT status, count(*) FROM incidents GROUP BY status"
```

Tras una carga nueva hay 10.000 incidencias. Abre el cliente en `http://localhost:8888` e inicia sesión con `demo.citizen@urbanpulse.test` y `demo-password`. También puedes verificar el login en PowerShell sin imprimir el JWT:

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/v1/auth/login' -ContentType 'application/json' -Body '{"email":"demo.citizen@urbanpulse.test","password":"demo-password"}'
(Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/users/me' -Headers @{Authorization="Bearer $($login.accessToken)"}).email
Remove-Variable login
```

Mailpit recibe SMTP en el puerto 1025 y muestra los correos en `http://localhost:9999`. Las cuentas de pruebas de frontend mantienen sus UUID y sus pertenencias. Los expedientes de referencia de rendimiento se describen en [fixtures k6](../k6/fixtures/README.md).

## Verificación del backend

Desde la raíz, con Java 25, Maven y Docker para las IT:

```sh
mvn -f backend/pom.xml clean test
mvn -f backend/pom.xml clean verify -Ppostgres-it
mvn -f backend/pom.xml javadoc:javadoc -Ddoclint=all
docker compose config --quiet
```

Surefire ejecuta unitarias y MVC. Failsafe incluye las suites explícitas del [POM](../backend/pom.xml) contra PostGIS efímero, con dobles S3. Mapas comprueba visibilidad, JSON por HTTP, límites, filtros e invocaciones por celda con 1.000, 10.000 y 50.000 incidencias sintéticas. El perfil de almacenamiento real requiere autorización separada y credenciales del entorno de pruebas.

Los comandos de generación están en [API](api/README.md), y la ejecución local completa con informes en [k6](../k6/README.md). La [CI](../.github/workflows/ci.yml) ejecuta también tests, comprobación de tipos, lint y exportación web del frontend.
