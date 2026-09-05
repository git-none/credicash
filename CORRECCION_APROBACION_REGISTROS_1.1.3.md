# Corrección aprobación de registros — Kredi+ Backend 1.1.3

## Causa
Android, cuando el perfil activo es Contador, usa `POST /accountant/users/{id}/verification`.
El backend 1.1.2 exponía `GET /accountant/registration-requests` y `GET /accountant/verifications`, pero no exponía el POST de decisión bajo `/accountant`.

## Corrección
- Se agregó el endpoint exacto usado por Android.
- Se añadieron aliases compatibles por ID de usuario y por ID de verificación.
- Se agregó `REVIEW_USERS` al conjunto de permisos funcionales del Contador.
- No se requiere migración de base de datos.
- No se cambian `DATABASE_URL`, `JWT_SECRET`, volumen `/data` ni dominio Railway.

## Despliegue
Actualizar el mismo servicio existente de Railway con este código. No crear otra base de datos.
