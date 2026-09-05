# Kredi+ Backend 1.1.7

Cambios sobre 1.1.6:

- `PUT /api/v1/admin/combos/{id}` para editar un combo existente.
- Conserva el mismo `combo_id`; no crea un combo duplicado.
- Permite modificar nombre, descripción, productos, cantidades y marca `extra`.
- La carátula sigue actualizándose con `POST /api/v1/admin/combos/{id}/cover`.
- `DELETE /api/v1/admin/combos/{id}` se mantiene como eliminación lógica.
- El listado público de combos devuelve únicamente combos activos.
- Se validan todos los productos antes de reemplazar la composición.
- Se registra auditoría `ADMIN_UPDATED_COMBO`.

Esto evita conflictos con compras/facturas históricas porque la edición no cambia el identificador del combo.
