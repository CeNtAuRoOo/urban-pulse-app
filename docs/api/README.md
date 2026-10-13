# Documentación de la API

## Contrato OpenAPI

- Archivo reservado: [`openapi.yaml`](openapi.yaml).
- **Estado actual:** contenido provisional; todavía no es una especificación válida ni describe endpoints implementados.
- Al definir los casos de uso se incorporarán rutas, métodos HTTP, parámetros, esquemas/DTO, ejemplos de respuestas, errores, autenticación y permisos.

## Documentación adicional recomendada (más adelante)

- `conventions.md`: nomenclatura, versionado, paginación, filtros y formatos de error.
- `auth.md`: registro, autenticación, JWT, roles e invitaciones.
- `examples/`: ejemplos de peticiones y respuestas, si aportan valor y no duplican OpenAPI.

Se seguirá un enfoque **design-first** siempre que sea viable: acuerdo de contrato, pruebas y después implementación. Si se adopta code-first para alguna parte, se garantizará la coherencia entre especificación y código.

### Solicitudes `fields`

La selección dinámica de campos **no está aprobada ni forma parte del contrato actual**. Antes de introducirla deberán definirse listas blancas, autorización por campo, significado de `null` y compatibilidad con DTO de lectura/escritura. La optimización de consultas al repositorio se medirá antes de incorporarla.
