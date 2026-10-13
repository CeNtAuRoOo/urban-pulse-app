# ADR-0003: Frontend universal con Expo y React Native Web

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Contexto

UrbanPulse debe funcionar en navegadores y dispositivos Android/iOS. Todos los roles tendrán las mismas posibilidades funcionales, condicionadas por sus permisos, con un desarrollo y mantenimiento viables para un equipo universitario.

## Opciones consideradas

1. React Native + Expo + React Native Web.
2. React Native con configuración nativa sin Expo.
3. Aplicación web React y aplicaciones móviles separadas.
4. Ionic/Capacitor o Flutter.

## Decisión

Mantener una única base de código frontend con **TypeScript**, React Native, Expo y React Native Web. Se utiliza **Expo Router** para navegación basada en archivos y **Metro** para empaquetado; no se introduce Vite. La aplicación web se exportará como SPA y se servirá independientemente del backend; Android e iOS se compilarán como aplicaciones nativas. Ambas consumirán la API REST de Spring Boot. El formato de distribución web queda documentado en [ADR-0008](0008-frontend-spa-nginx.md).

## Consecuencias

**Positivas:** reutilización de lógica, componentes y navegación; menor duplicación de funcionalidades.

**Costes:** algunos componentes nativos, mapas, permisos y almacenamiento local necesitarán implementaciones por plataforma. El resultado móvil no se despliega como un contenedor Docker, aunque el desarrollo web sí pueda usar Compose.

## Consideraciones pendientes

Expo Router y TypeScript ya están configurados; la ejecución web local, exportación web y `Dockerfile.dev` mediante Compose han sido comprobados (13/10/2026). Siguen pendientes: librería de mapas, pruebas funcionales, pruebas de dispositivos Android/iOS, actualización y compilación nativa. La imagen web **de producción** Nginx no se ha probado todavía.
