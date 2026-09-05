# Validación Kredi+ Backend 1.1.19

- OK: CORS gestionado por el plugin Ktor.
- OK: `anyHost()` habilita Cloudflare Pages sin conocer previamente el subdominio.
- OK: `Last-Event-ID` permitido para reconexión SSE.
- OK: `X-Kredi-Explorer` expuesto.
- OK: no quedan encabezados `Access-Control-Allow-*` manuales en `applyPublicExplorerHeaders()`.
- OK: existen feed, detalle, SSE y OPTIONS del explorador.
- OK: versión 1.1.19 alineada en Gradle, runtime, Docker y OpenAPI.

## Limitación de validación local
No se pudo ejecutar Gradle en este entorno porque `services.gradle.org` no resuelve DNS. El wrapper y los tests quedan incluidos para que Railway ejecute `clean test buildFatJar` durante el despliegue.
