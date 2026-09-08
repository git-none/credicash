KREDI+ BACKEND 1.1.29 — CORRECCIÓN DE BUILD EN RAILWAY

Corrección aplicada:
- Se añadió README.md, requerido por RailwayRuntimeConfigurationTest.
- El README documenta /data y /data/uploads, tal como valida la prueba.
- Se conserva UPLOAD_DIR=/data/uploads en Dockerfile y .env.example.
- Se mantiene la lógica 1.1.28 de destinos de pago de catálogo.
- Versión sincronizada en Gradle, runtime, Docker y OpenAPI.

Motivo del fallo anterior:
RailwayRuntimeConfigurationTest intentaba leer README.md, pero ese archivo no existía en el contexto Docker y lanzaba java.nio.file.NoSuchFileException.
