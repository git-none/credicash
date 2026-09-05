# Validación del hotfix Railway existente

Validación estática realizada antes de empaquetar:

- Backend/runtime actualizado a `1.1.2`.
- `railway.toml` mantiene `/health/ready` como healthcheck.
- Docker sigue compilando/ejecutando con Java 21.
- La migración 84 está incluida en `schema.sql`.
- `Database.verifyRequiredSchema()` ahora exige que la migración 84 esté registrada.
- El flujo de migración conserva `pg_advisory_xact_lock`, evitando que dos despliegues modifiquen el esquema simultáneamente.
- No se encontraron referencias a Northflank dentro del backend empaquetado.
- La documentación indica explícitamente actualizar el servicio Railway existente y conservar `DATABASE_URL`, `JWT_SECRET`, dominio y volumen.

## Compilación en este entorno

Se intentó ejecutar `./gradlew test`. El wrapper inició correctamente, pero este entorno no tiene salida DNS/Internet hacia `services.gradle.org`, por lo que Gradle 9.4.1 no pudo descargarse (`UnknownHostException`). Esto ocurre antes de compilar el proyecto y no corresponde a un error del código.

Railway sí podrá ejecutar el `Dockerfile` durante el deployment si tiene acceso normal a Internet para descargar las dependencias Gradle/Maven.
