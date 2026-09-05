# Validación Kredi+ Backend 1.1.5

## Diagnóstico confirmado

La cuenta Contador ya existe en PostgreSQL, pero las variables `BOOTSTRAP_ACCOUNTANT_PASSWORD` y `BOOTSTRAP_ACCOUNTANT_PIN` de Railway no necesariamente coinciden con los hashes históricos almacenados en `usuarios`.

La versión 1.1.4 detectaba el Contador existente y, por diseño, no reescribía sus credenciales. Por eso un valor correcto visible en Railway podía terminar en `Usuario o contraseña incorrectos.`.

## Corrección 1.1.5

- `ensureBootstrapAccountant` admite una contraseña de recuperación únicamente durante el login.
- Solo se activa si existe exactamente un Contador y la contraseña enviada coincide byte a byte con `BOOTSTRAP_ACCOUNTANT_PASSWORD`.
- Si los hashes están desincronizados, actualiza `password_hash` y `pin_hash` de forma transaccional.
- Registra auditoría `BOOTSTRAP_ACCOUNTANT_CREDENTIALS_RECOVERED`.
- Un despliegue por sí solo no cambia credenciales.
- Una contraseña distinta a la protegida no activa recuperación.
- No crea usuarios adicionales y no requiere migración SQL.

## Versiones

- Gradle: 1.1.5
- Runtime: 1.1.5
- Docker: 1.1.5
- OpenAPI: 1.1.5

## Build en este entorno

No se pudo ejecutar `./gradlew test` porque el wrapper intenta descargar Gradle 9.4.1 desde `services.gradle.org` y este entorno no tiene resolución de red hacia ese host. Se añadió `AccountantCredentialRecoveryTest.kt` para que Railway/CI ejecute la validación durante el build normal.
