# Kredi+ Backend Railway 1.1.4 — acceso del Contador

## Problema corregido
El endpoint `/auth/login` devolvía `Debes registrarte primero.` cuando el identificador visible usado por el Contador no coincidía exactamente con el `username/email` histórico almacenado en `usuarios`.

## Corrección
1. La búsqueda normal ahora compara `username` y `email` con `TRIM` y sin distinguir mayúsculas/minúsculas.
2. `CONTADOR` funciona como alias canónico de compatibilidad únicamente cuando:
   - la búsqueda directa no encontró una cuenta; y
   - existe exactamente un único usuario con rol de Contador no bloqueado.
3. El alias NO omite seguridad: la contraseña almacenada del usuario resuelto se verifica exactamente igual que en el login normal.
4. Si existen dos o más Contadores, el alias no se resuelve para evitar ambigüedad.
5. Si la cuenta realmente no existe, el backend devuelve `Usuario o contraseña incorrectos.` en lugar de sugerir crear otra cuenta.

## Railway
Es una actualización in-place del servicio existente.
- No crea PostgreSQL nuevo.
- No cambia DATABASE_URL.
- No cambia JWT_SECRET.
- No requiere migración de esquema.
- Mantiene la migración 84 y todos los fixes anteriores.

Versión: 1.1.4
