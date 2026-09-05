# Kredi+ Backend 1.1.19 — CORS real para visor Cloudflare

## Problema
El backend 1.1.18 añadía `Access-Control-*` dentro de las rutas del explorador, pero el plugin CORS global de Ktor podía rechazar la petición antes de ejecutar esas rutas.

## Corrección
- CORS se gestiona en una sola capa: el plugin oficial de Ktor.
- Se usa `anyHost()` sin credenciales de cookies; la seguridad de rutas privadas sigue dependiendo de JWT/Bearer.
- Se habilitan `GET`, `OPTIONS`, `POST`, `PUT`, `PATCH` y `DELETE` para mantener compatibilidad de clientes web.
- Se permiten `Authorization`, `Content-Type`, `Cache-Control`, `Last-Event-ID` y `X-Registration-Token`.
- Se expone `X-Kredi-Explorer`.
- Se eliminan headers CORS manuales de `/api/v1/explorer/*` para evitar duplicados.
- El visor puede alojarse en cualquier dominio `*.pages.dev` o dominio personalizado de Cloudflare.

## Producción
Este paquete debe sustituir el código del MISMO servicio Railway que publica `https://credicash-production.up.railway.app/`.
Al finalizar el despliegue, `/` debe mostrar la versión `1.1.19`.
