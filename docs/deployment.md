# Guía de despliegue

> **Estado:** estrategia objetivo, **no procedimiento operativo validado**. A 11/10/2026 no se han elegido proveedores para alojar el frontend web y el backend, ni existen aún los artefactos finales de las aplicaciones. Las instrucciones siguientes no deben presentarse como un despliegue realizado.

## 1. Objetivo

Desplegar la aplicación web y la API REST **por separado**, conservar PostgreSQL y las evidencias en servicios administrados y distribuir Android/iOS como aplicaciones nativas. Reducir costes y facilitar la actualización independiente de cada parte.

## 2. Visión de los componentes

| Componente                          | Destino                                                              | Estado                                                      |
| ----------------------------------- | -------------------------------------------------------------------- | ----------------------------------------------------------- |
| Frontend web (Expo Web)             | Contenedor independiente que sirve el build web; proveedor pendiente | No implementado                                             |
| Backend API (Spring Boot)           | Contenedor Docker independiente; proveedor pendiente                 | Dockerfile base preparado, app no implementada              |
| Android/iOS (Expo)                  | Compilaciones nativas instalables; distribución por definir          | No implementado                                             |
| PostgreSQL                          | Neon PostgreSQL administrado                                         | Proveedor decidido; provisionamiento pendiente de confirmar |
| Evidencias (fotografías)            | Neon Object Storage, bucket privado                                  | Propuesto; validar disponibilidad y permisos                |
| Fuentes contextuales/notificaciones | Servicios externos por definir                                       | Pendiente                                                   |

**Importante:** `compose.yaml` se utilizará **solo para desarrollo**. No es el manifiesto de despliegue conjunto de producción.

## 3. Diagrama de despliegue

**[PENDIENTE: incorporar diagrama C4 Deployment]**

Se elaborará cuando conozcamos el proveedor, las redes, los dominios públicos, TLS y el lugar de ejecución real de web/backend. Archivo propuesto: `docs/diagrams/deployment.drawio`.

<!-- Cuando exista: ![C4 - Deployment](diagrams/deployment.svg) -->

## 4. Requisitos previos al primer despliegue

- Implementación y pruebas del backend y frontend.
- Proveedores/planes seleccionados y credenciales de despliegue.
- Gestión segura de secretos; sin valores reales en Git ni dentro de imágenes Docker.
- Definir dominio y HTTPS para API y web, CORS, políticas de acceso y cabeceras.
- Migraciones Flyway verificadas y separación entre base de pruebas y base compartida.
- Decidir cómo revisar fotografías antes de mostrarlas a usuarios.
- CI que construya y pruebe la versión antes de desplegarla.

## 5. Backend Spring Boot: imagen independiente

El repositorio ya contiene `backend/Dockerfile` multietapa: Maven construye un JAR y una imagen con JRE ejecuta la aplicación. El puerto previsto es **8888**.

Tras implementar y configurar el backend, comandos de referencia:

```bash
# Desde la raíz del repositorio
docker build -f backend/Dockerfile -t urban-pulse-backend:0.1.0 backend
# Ejecutar con las variables necesarias, definidas por el entorno de destino.
```

El comando solo ilustra la futura construcción; hoy la aplicación no existe y no se garantiza que termine correctamente. No usar etiquetas inmutables ficticias como si hubiera una release publicada. No insertar variables secretas mediante instrucciones `ENV` o `ARG` del Dockerfile.

En producción se espera que el proveedor entregue variables de entorno para conexión JDBC/TLS, firma/verificación JWT y almacenamiento S3. La disponibilidad y el endpoint HTTPS de la API se verificarán tras el despliegue.

## 6. Frontend web: imagen y configuración independientes

El frontend tendrá una única base de código React Native + Expo. Para web se generarán archivos estáticos que podrá servir un contenedor independiente (por ejemplo, mediante un servidor web ligero). **El Dockerfile de producción está pendiente de definir**.

La URL consumida por el navegador deberá ser pública y usar HTTPS. Las variables `EXPO_PUBLIC_*` son visibles en el cliente tras construirlo: **nunca incluir secretos ni credenciales de Neon**. Se decidirá si la URL API se inyecta en tiempo de construcción o mediante configuración pública en tiempo de ejecución.

La compatibilidad entre cliente web y API requiere configurar CORS y utilizar rutas versionadas cuando el contrato lo necesite.

## 7. Aplicaciones Android e iOS

Las aplicaciones móviles **no se ejecutarán como contenedores Docker** en los teléfonos. Se compilarán mediante las herramientas de Expo y se instalarán/distribuirán de acuerdo con las posibilidades de la asignatura.

La configuración móvil debe apuntar a la API HTTPS accesible desde Internet, no a `localhost` ni al nombre de servicio Docker `backend`. El método de distribución (instalación de pruebas, tiendas u otra modalidad) está pendiente.

## 8. PostgreSQL y Object Storage en Neon

### PostgreSQL

- Crear/provisionar proyecto, región y base según el plan gratuito vigente.
- Facilitar al backend una conexión JDBC cifrada y credenciales de privilegio mínimo.
- Tener en cuenta la latencia por suspensión/reanudación automática del cómputo.
- Flyway ejecutará migraciones versionadas de modo controlado antes de utilizar la nueva versión de la aplicación.
- Hibernate validará el esquema con `ddl-auto=validate`; no alterará las tablas de producción por sí mismo.

### Evidencias

- Validar que Neon Object Storage esté disponible para el proyecto/región y configurar un bucket privado.
- Las credenciales de objetos solo estarán en backend o en el mecanismo de emisión segura de permisos temporales.
- Almacenar identificador y metadatos en PostgreSQL, contenido en Object Storage.
- Definir política de visibilidad, revisión, retención y eliminación. Evitar objetos sin referencia si falla una parte de la operación.

## 9. Configuración y secretos

| Categoría               | Ejemplo de dato                   | Ubicación                              |
| ----------------------- | --------------------------------- | -------------------------------------- |
| URL y credenciales JDBC | host, base, usuario, contraseña   | Entorno seguro del backend             |
| Configuración JWT       | claves, caducidades, emisores     | Entorno seguro del backend             |
| Acceso Object Storage   | endpoint, bucket, credenciales S3 | Entorno seguro del backend             |
| URL pública API         | `https://api.ejemplo.invalid`     | Configuración **pública** del frontend |
| Control de origen web   | orígenes HTTPS permitidos         | Configuración backend                  |

`api.ejemplo.invalid` es un **marcador de posición**: sustituirlo únicamente cuando exista un dominio real.

No publicar `.env`, tokens ni copias de bases de datos. El archivo `.env.example` solo documentará nombres y valores no sensibles.

## 10. Estrategia CI/CD prevista

Cuando exista código:

1. Pull Request -> formateo/validación, pruebas, análisis y build.
2. Revisión y fusión a `main` tras resultados correctos.
3. Generar artefactos e imágenes con identificador de versión y commit.
4. Publicar artefacto en un registro definido.
5. Desplegar frontend y backend de forma independiente.
6. Comprobar conectividad, estado del servicio y endpoints principales.
7. Documentar rollback para cada proveedor.

Los workflows estarán en `.github/workflows/` y **no se crean todavía**. No se debe activar el despliegue automático antes de configurar secrets y permisos mínimos.

## 11. Verificaciones posteriores al despliegue

- HTTPS y disponibilidad desde web y dispositivos.
- API responde con el contrato acordado y errores consistentes.
- JWT válidos aceptados e inválidos rechazados; accesos no autorizados bloqueados.
- Conexión PostgreSQL y esquema verificado; migraciones aplicadas como se espera.
- Escritura/lectura de imágenes en bucket privado con permisos correctos.
- CORS, logs sin secretos, límites de subida y manejo de fallos externos.
- Frontend y backend compatibles entre sí.

## 12. Rollback, costes y limitaciones

Cada imagen del backend y compilación web tendrá una versión identificable para volver a una anterior. **Las migraciones SQL no se revierten automáticamente** al revertir una imagen: los cambios de esquema deberán diseñarse para permitir despliegues y retrocesos seguros.

Neon tiene cuotas y comportamientos específicos de sus planes gratuitos; antes del despliegue deben revisarse sus condiciones actuales. **No se presupone disponibilidad permanente ni ausencia de costes por exceso de cuota**. La estrategia de recuperación/copia de datos todavía no está diseñada y constituye un riesgo pendiente, aunque se posponga en la asignatura.

## 13. Pendientes para convertir esta guía en operativa

- Proveedores, dominios y URLs reales de frontend web y backend.
- Dockerfile de producción del frontend.
- Scripts de compilación/pruebas reales de Expo.
- Base Neon y almacenamiento provisionados, más estrategia de backups.
- Workflows CI/CD, registro de imágenes, secretos y permisos de despliegue.
- Comprobaciones de salud implementadas, métricas y procedimiento de rollback probado.

## 14. Documentos relacionados

- [Arquitectura](architecture.md)
- [Desarrollo](development.md)
- [ADR](adr/README.md)
- [Diagramas](diagrams/README.md)
