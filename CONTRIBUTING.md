# Guía de contribución

Este documento establece las normas básicas para contribuir
al desarrollo de UrbanPulse.

## Flujo de trabajo

1. Actualizar la rama `main` antes de comenzar.
2. Crear una rama específica para cada cambio.
3. Implementar los cambios necesarios.
4. Realizar commits pequeños y descriptivos.
5. Ejecutar las pruebas correspondientes.
6. Abrir un Pull Request hacia `main`.
7. Revisar los cambios antes de fusionarlos.

## Nomenclatura de ramas

Se utilizarán los siguientes prefijos:

- `feature/`: nuevas funcionalidades.
- `fix/`: corrección de errores.
- `docs/`: cambios en documentación.
- `refactor/`: cambios internos sin alterar comportamiento.
- `test/`: añadir o modificar tests.
- `ci/`: cambios en integración continua

## Convención de commits

Se seguirá la convención Conventional Commits,
utilizando los prefijos estándar en inglés y las
descripciones en español.

Se utilizará el siguiente formato:

`tipo(scope): descripción del cambio`

El scope indica la parte del proyecto afectada
(backend o frontend). Será opcional para cambios
generales que afecten a todo el proyecto.

Ejemplos:

- `feat(backend): añadir endpoint para crear incidencias`
- `feat(frontend): implementar formulario de creación de incidencias`

- `fix(backend): corregir la validación de incidencias`
- `fix(frontend): corregir la visualización de incidencias`

- `refactor(backend): simplificar el servicio de incidencias`
- `refactor(frontend): simplificar el componente de listado de incidencias`

- `test(backend): añadir pruebas del servicio de incidencias`

- `docs: actualizar la documentación de arquitectura`
- `test: añadir pruebas de integración entre frontend y backend`
- `ci: configurar GitHub Actions para ejecutar las pruebas`

## Pull Requests

Cada Pull Request deberá describir los cambios realizados,
su motivación y las pruebas ejecutadas.

No se deben fusionar cambios que rompan el funcionamiento
del proyecto.

## Documentación

Los cambios relevantes deberán acompañarse de las
actualizaciones necesarias en la documentación.

Las decisiones arquitectónicas importantes deberán
registrarse mediante ADR.
