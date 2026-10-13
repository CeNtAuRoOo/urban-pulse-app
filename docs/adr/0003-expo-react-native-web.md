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

Mantener una única base de código frontend con React Native, Expo y React Native Web; se recomienda TypeScript. La aplicación web se compilará para despliegue como recursos web; Android e iOS se compilarán como aplicaciones nativas. Ambas consumirán la API REST de Spring Boot.

## Consecuencias

**Positivas:** reutilización de lógica, componentes y navegación; menor duplicación de funcionalidades.

**Costes:** algunos componentes nativos, mapas, permisos y almacenamiento local necesitarán implementaciones por plataforma. El resultado móvil no se despliega como un contenedor Docker, aunque el desarrollo web sí pueda usar Compose.

## Consideraciones pendientes

Elegir navegación, librería de mapas, sistema de pruebas, mecanismo de actualización y estrategia de compilación nativa. La configuración de Expo para desarrollo con Docker está pendiente de implementar y probar.
