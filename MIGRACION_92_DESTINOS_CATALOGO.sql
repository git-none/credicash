-- Kredi+ Backend 1.1.28
-- Migración 92: negocio receptor independiente para ventas individuales y combos.
BEGIN;

CREATE TABLE IF NOT EXISTS configuracion_ventas_catalogo (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    product_business_id BIGINT REFERENCES negocios_asociados(id) ON DELETE SET NULL,
    combo_business_id BIGINT REFERENCES negocios_asociados(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE configuracion_ventas_catalogo ADD COLUMN IF NOT EXISTS product_business_id BIGINT;
ALTER TABLE configuracion_ventas_catalogo ADD COLUMN IF NOT EXISTS combo_business_id BIGINT;
ALTER TABLE configuracion_ventas_catalogo ADD COLUMN IF NOT EXISTS updated_by BIGINT;
ALTER TABLE configuracion_ventas_catalogo ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
INSERT INTO configuracion_ventas_catalogo(id) VALUES (1) ON CONFLICT(id) DO NOTHING;
INSERT INTO versiones_esquema(version, description)
VALUES (92, 'Kredi+ 7.2.39: asociación explícita de negocio para ventas individuales y combos; pago deshabilitado sin destino')
ON CONFLICT(version) DO UPDATE SET description=EXCLUDED.description, applied_at=NOW();

COMMIT;
