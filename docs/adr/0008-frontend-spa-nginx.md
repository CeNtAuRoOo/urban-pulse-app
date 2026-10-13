# ADR-0008: Exportación web SPA y contenedor Nginx independiente

- **Estado:** Aceptado (estrategia), **validación de imagen de producción pendiente**
- **Fecha:** 2026-10-13
- **Ámbito:** Distribución web del frontend UrbanPulse

## Contexto

UrbanPulse comparte una base de código Expo/React Native para navegadores y dispositivos móviles (ADR-0003). La aplicación será principalmente interactiva y consumirá la API REST independiente de Spring Boot. El repositorio utiliza Docker Compose solo en desarrollo; en producción se desea desplegar la web y el backend por separado, sin asumir la necesidad de renderizado del lado del servidor.

## Alternativas consideradas

1. **Exportación SPA estática y servidor web ligero** (Nginx): no requiere un proceso Node.js permanente para servir la web.
2. Expo Web con renderizado de servidor (_server output_): capacidad adicional que no se necesita en la etapa inicial y complica hosting.
3. Exportación estática prerenderizada (_static output_): útil para páginas públicas con contenido prerenderizable, menos relevante para la interacción autenticada prevista.
4. Servir el frontend desde Spring Boot: simplifica el número de servicios desplegados, pero impide la separación operativa elegida.

## Decisión

Configurar `frontend/app.json` con `web.output="single"`. Utilizar Metro/Expo para exportar `dist/` mediante `npx expo export --platform web`. Servirlo mediante un **contenedor Nginx independiente** utilizando `frontend/Dockerfile` multietapa y `frontend/nginx.conf` con fallback a `index.html` para las rutas de Expo Router. Mantener `frontend/Dockerfile.dev` con Expo Dev Server para el desarrollo y `compose.yaml` solo en local.

El frontend incluirá la URL pública de la API a través de `EXPO_PUBLIC_API_URL` **en tiempo de compilación**, sin secretos. Un cambio de URL exigirá reconstruir la imagen salvo que se adopte otra estrategia explícita.

## Consecuencias

**Ventajas:** despliegue web independiente, imagen final pequeña, sin Node.js en ejecución en producción, navegación SPA y proceso de compilación reproducible mediante npm/lockfile.

**Costes y riesgos:** configuración pública ligada a la compilación, gestión de HTTPS/CORS, rutas SPA en Nginx, posible necesidad futura de SSR/prerenderizado y diferenciación entre la distribución web Docker y las aplicaciones móviles nativas.

## Evidencia y trabajo pendiente

El 13/10/2026 se validó **la exportación web** a `dist/` y el desarrollo web tanto fuera como dentro de Docker Compose. **No se ha ejecutado todavía la imagen de producción con Nginx** ni elegido proveedor cloud, dominio o sistema CI/CD. Antes de desplegar: probar imagen Nginx, recarga de rutas profundas, URL HTTPS de la API y CORS, y revisar los límites del proveedor.

## Referencias

- [ADR-0003: frontend universal](0003-expo-react-native-web.md)
- [Despliegue](../deployment.md)
- [Desarrollo](../development.md)
