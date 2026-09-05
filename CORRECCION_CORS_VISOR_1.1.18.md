# Kredi+ Backend 1.1.18 — corrección CORS del visor público

## Objetivo
Permitir que el visor público alojado en Cloudflare consulte directamente el mismo backend de Railway sin abrir CORS sobre las rutas privadas.

## Cambios
- `/api/v1/explorer/health`, `/transactions`, `/transactions/{id}` y `/events` responden con `Access-Control-Allow-Origin: *`.
- Se mantienen únicamente métodos `GET` y `OPTIONS` para el contrato público del visor.
- Se permiten `Accept`, `Content-Type`, `Cache-Control` y `Last-Event-ID`.
- Se expone `X-Kredi-Explorer` para diagnóstico.
- Se eliminó la excepción global automática `*.pages.dev` del plugin CORS para evitar cabeceras duplicadas.
- El CORS global del resto de la aplicación sigue gobernado por `CORS_ALLOWED_ORIGINS`.
- No se modifica el dominio de Railway ni las credenciales de producción.

## Despliegue
Subir este proyecto sobre el servicio Railway existente `credicash-production.up.railway.app`.
No crear otro servicio ni cambiar la base de datos.
