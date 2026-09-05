# Actualizar Kredi+ sobre el Railway existente

## Objetivo
Actualizar el backend que ya está en producción sin crear un servicio nuevo, sin cambiar de PostgreSQL y sin invalidar sesiones.

## Antes del deploy
1. En Railway abre el servicio backend actual y confirma que `DATABASE_URL` existe y apunta al PostgreSQL actual.
2. Confirma que `JWT_SECRET` existe. **No lo copies a otro valor ni lo regeneres.**
3. Si usas archivos subidos, confirma que el volumen sigue montado en `/data` y `UPLOAD_DIR=/data/uploads`.
4. No borres ni recrees el servicio PostgreSQL.

## Deploy
- Si Railway está conectado a GitHub: reemplaza el código de la rama conectada por este paquete, commit y push. Railway construirá el `Dockerfile` automáticamente.
- Si despliegas manualmente desde Railway CLI/Git: usa este directorio como raíz del mismo servicio existente.

El contenedor ejecuta tests + `buildFatJar` durante build. Al arrancar, el backend conecta a la base existente, toma un advisory lock y ejecuta `schema.sql` idempotentemente. La migración 84 se registra en `versiones_esquema`.

## Validación después del deploy
Comprueba en este orden:
1. `GET /health/live` -> 200.
2. `GET /health/ready` -> 200.
3. `GET /api/v1/health` -> base conectada/esquema listo.
4. Inicia sesión desde el APK actual.
5. Prueba suspender/reactivar una cuenta de prueba.
6. Prueba eliminar una cuenta de prueba sin historial financiero.
7. Verifica que una cuenta con historial financiero siga protegida y devuelva un mensaje claro en lugar de un 503 genérico.

## Si el deployment falla
Railway conserva deployments anteriores. No borres la base de datos: revisa primero el log de migración y la referencia/SQLSTATE. El script `scripts/repair-account-management-v84.sql` queda como reparación manual de emergencia, no como paso normal.
