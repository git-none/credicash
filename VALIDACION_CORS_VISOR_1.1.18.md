# Validación del parche CORS del visor

Validaciones estáticas realizadas:

- Versión 1.1.18 alineada en Gradle, runtime, Docker y OpenAPI.
- Rutas públicas del visor presentes: health, transactions, transaction detail y events.
- Cabecera `Access-Control-Allow-Origin: *` aplicada exclusivamente mediante `applyPublicExplorerHeaders()`.
- Métodos públicos documentados para CORS: GET y OPTIONS.
- Cabeceras permitidas: Accept, Content-Type, Cache-Control y Last-Event-ID.
- El plugin CORS global ya no autoriza automáticamente todos los dominios `*.pages.dev`; el resto de la API continúa usando `CORS_ALLOWED_ORIGINS`.
- Se conservan las rutas privadas y la autenticación sin cambios.

La ejecución de Gradle no pudo completarse en el entorno de preparación porque `services.gradle.org` no resuelve desde este contenedor. Railway ejecutará el build con el wrapper incluido.
