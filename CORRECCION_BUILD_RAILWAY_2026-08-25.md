# Corrección de build Railway

El deployment fallaba después de compilar correctamente porque `RailwayRuntimeConfigurationTest` seguía exigiendo la versión 1.1.0.

## Corrección
- El test ya no contiene una versión rígida.
- Extrae y compara automáticamente las versiones de `build.gradle.kts`, `Version.kt`, `Dockerfile` y OpenAPI.
- OpenAPI fue actualizado a 1.1.2.
- No se modifican `DATABASE_URL`, `JWT_SECRET`, volumen, dominio ni migraciones.

El comando de Railway puede mantenerse sin cambios:
`./gradlew --no-daemon --console=plain --max-workers=2 clean test buildFatJar --stacktrace`
