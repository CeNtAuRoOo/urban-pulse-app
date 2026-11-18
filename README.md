# UrbanPulse

UrbanPulse permite comunicar incidencias urbanas y seguir su tramitación municipal. El backend admite el registro y verificación de cuentas, la incorporación de personal por invitación, la gestión de usuarios y catálogos, las incidencias con evidencias privadas, las revisiones, las solicitudes de información y la prioridad manual. También ofrece un mapa provincial con posiciones públicas aproximadas, detección de posibles duplicados y consulta de activos urbanos.

## Desarrollo local

Desde la raíz, prepara las variables privadas del backend y el cliente según la [guía de desarrollo](docs/development.md). Arranca con `docker compose up --build`. El frontend queda en `http://localhost:8888`, la API en `http://127.0.0.1:8080/api/v1` y Mailpit en `http://localhost:9999`. La base de desarrollo contiene 10.000 incidencias sintéticas y acepta `demo.citizen@urbanpulse.test` con `demo-password`. Al detener el contenedor PostgreSQL se pierden sus datos.

## Proyecto

- [Documentación](docs/README.md) y [contrato API](docs/api/README.md).
- [Requisitos y primera entrega funcional](docs/incidents-implementation-roadmap.md) y [pendientes](docs/pending.md).
- [Contribución](CONTRIBUTING.md), [seguridad](SECURITY.md) e [historial de cambios](CHANGELOG.md).
