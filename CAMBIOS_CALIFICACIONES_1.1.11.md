# Kredi+ Backend 1.1.11 — Interés y satisfacción

- Migración 86: `calificaciones_banner` y `calificaciones_compra`.
- Un voto por usuario/banner y una valoración por usuario/compra mediante restricciones UNIQUE y UPSERT.
- `POST /banners/{id}/rating`: interés de campaña de 1 a 5 estrellas.
- `POST /purchases/{id}/rating`: satisfacción posterior a la compra, etiquetas y comentario opcional.
- `GET /ratings/insights`: estadísticas agregadas para ADMIN y ACCOUNTANT.
- Distribución 1–5 estrellas, promedio, volumen de votos y porcentaje 4–5 estrellas para banners.
- La valoración de compra valida que el pedido pertenezca al usuario autenticado.
