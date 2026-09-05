# Kredi+ Backend 1.1.9 — Auditoría de estabilidad

- Conserva las rutas del carrusel, combos, jornadas, productos, autenticación y pagos.
- La versión es coherente en Gradle, runtime, Docker y OpenAPI.
- El orden de nuevos banners se resuelve ahora en PostgreSQL de forma automática y transaccional.
- Editar un banner conserva su posición actual; el cliente ya no puede cambiarla accidentalmente mediante un campo oculto.
- Se añadió una prueba de contrato para impedir regresiones de esta lógica.

La compilación Gradle completa no se ejecutó en este entorno porque no existe una distribución Gradle cacheada ni acceso al SDK/dependencias necesario para reproducir el pipeline completo.
