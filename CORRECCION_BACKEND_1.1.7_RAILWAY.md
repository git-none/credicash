# Corrección Backend Kredi+ 1.1.7 — Railway

## Problema corregido

El proyecto publicaba la versión `1.1.7` en Gradle, runtime y OpenAPI, pero el `Dockerfile` todavía declaraba `1.1.5`.
Esto hacía fallar `RailwayRuntimeConfigurationTest` y detenía `clean test buildFatJar` durante el build de Railway.

## Cambio aplicado

- `Dockerfile`: `CREDICASH_BACKEND_VERSION="1.1.7"`.
- `README.md`: encabezado actualizado a 1.1.7.
- `CHANGELOG.md`: registrada la corrección de coherencia del despliegue.

## Combos comprobados

- `PUT /api/v1/admin/combos/{id}` existe y conserva el `combo_id`.
- `DELETE /api/v1/admin/combos/{id}` usa eliminación lógica (`active=FALSE`).
- El listado público devuelve únicamente combos activos.
- Las referencias históricas no son borradas físicamente.

## Validación

Se verificó estáticamente que Gradle, runtime, Docker y OpenAPI reportan exactamente `1.1.7` y que las rutas/servicios de editar y eliminar combo están presentes.

El entorno de reparación no dispone de acceso saliente a `services.gradle.org`, por lo que no pudo volver a descargar Gradle 9.4.1. El log original ya había completado `compileKotlin`, `jar`, `shadowJar` y `buildFatJar`; la única prueba fallida era la comparación de versión Docker ahora corregida.
