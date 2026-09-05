# Kredi+ Backend Railway 1.1.5 — recuperación de credenciales del Contador

## Problema confirmado

Railway puede mostrar `BOOTSTRAP_ACCOUNTANT_USERNAME`, `BOOTSTRAP_ACCOUNTANT_PASSWORD` y `BOOTSTRAP_ACCOUNTANT_PIN` correctamente, mientras PostgreSQL conserva hashes antiguos de una cuenta Contador creada en un despliegue anterior.

En esa situación el identificador es localizado, pero `passwordSecurity.verify(...)` falla y Android muestra `Usuario o contraseña incorrectos.`.

## Corrección

Cuando el intento de login usa el identificador bootstrap del Contador:

1. El backend comprueba que existe exactamente una cuenta con rol Contador.
2. La contraseña enviada debe coincidir exactamente con `BOOTSTRAP_ACCOUNTANT_PASSWORD` de Railway.
3. Solo en ese caso, si los hashes almacenados no coinciden, el backend vuelve a generar `password_hash` y `pin_hash` usando las variables protegidas actuales.
4. La operación queda auditada como `BOOTSTRAP_ACCOUNTANT_CREDENTIALS_RECOVERED`.
5. Después continúa el login normal y se solicita el PIN real.

Un redeploy sin un intento de login no cambia la contraseña. Una contraseña distinta de la variable protegida tampoco activa la recuperación.

No crea otra cuenta, no elimina datos y no requiere migración SQL.
