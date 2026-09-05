# Kredi+ Backend 1.1.11 — permisos de productos

- Se agrega `MANAGE_PRODUCTS` al rol Almacenista.
- Este permiso permite editar precio/modalidad y eliminar productos.
- No concede `MANAGE_CATALOG`, por lo que no habilita administración de Combos u otras áreas del catálogo.
- Las rutas `/admin/products/{id}` de eliminación y `/pricing`, junto con sus alias en español, aceptan `MANAGE_PRODUCTS`.
