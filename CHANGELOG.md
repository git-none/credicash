# Historial de versiones

## 1.0.0 — 2026-08-20

Primera versión estable del backend de Credicash preparada para Railway.

- Conserva la lógica de negocio Kotlin/Ktor y el esquema PostgreSQL existente.
- Admite `DATABASE_URL`, las variables `PG*` y el puerto dinámico `PORT` de Railway.
- Incluye healthchecks de proceso y disponibilidad de PostgreSQL.
- Documenta y configura `/data/uploads` para un volumen persistente.
- Protege documentos de identidad y comprobantes mediante enlaces firmados temporales.
- Hace privadas por defecto las categorías de uploads que no estén autorizadas expresamente como públicas.
- Añade vencimiento a los JWT y limita la vigencia móvil de las sesiones persistentes.
- Bloquea el desafío de PIN después de cinco intentos fallidos.
- Rechaza secretos JWT menores de 32 bytes.
- Incorpora pruebas automatizadas y verificación continua en GitHub Actions.
