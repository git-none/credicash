# Corrección Railway Build — Backend 1.1.14

Fecha: 2026-09-03

## Problema detectado
El test `RailwayRuntimeConfigurationTest` fallaba en la comparación entre la versión de Gradle y la declarada en Docker.

Valores antes de la corrección:
- `build.gradle.kts`: 1.1.14
- `Version.kt`: 1.1.14
- `Dockerfile`: 1.1.13
- `openapi/credicash.yaml`: 1.1.14

## Corrección aplicada
Se actualizó `ARG CREDICASH_BACKEND_VERSION` del `Dockerfile` de `1.1.13` a `1.1.14`.

## Verificación
Se reprodujeron las mismas expresiones regulares y comparaciones del test de coherencia. Los cuatro orígenes publican ahora `1.1.14`.

No se modificó la versión Android 7.2.31 ni la lógica de recuperación de contraseña + PIN por Telegram.
