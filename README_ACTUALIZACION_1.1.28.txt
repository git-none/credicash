KREDI+ BACKEND 1.1.28 - HOTFIX DESTINOS DE PAGO DEL CATALOGO

Objetivo:
- Corregir el error al asociar un negocio desde Contador > Negocios asociados.
- Garantizar que /catalog-sales-destinations funcione incluso durante una actualización de una base existente.

Cambios:
1. El backend repara de forma idempotente configuracion_ventas_catalogo antes de leer/guardar.
2. La migración 92 queda validada explícitamente al iniciar.
3. Se incluye MIGRACION_92_DESTINOS_CATALOGO.sql como reparación manual opcional.
4. No se borra ni reemplaza ningún negocio ni asociación existente.

RAILWAY:
- Actualiza el servicio existente con esta carpeta.
- CONSERVA exactamente el mismo DATABASE_URL y JWT_SECRET del servicio actual.
- No crees otra base PostgreSQL.
- El despliegue aplica schema.sql automáticamente; el endpoint también tiene reparación automática.

Después del deploy:
- /health/ready debe responder listo.
- Inicia sesión como Contador.
- Presupuesto > Negocios asociados > Ventas individuales / Combos.
- Selecciona el negocio activo.
