-- Kredi+ Backend 1.1.8 · Migración 85
CREATE TABLE IF NOT EXISTS banners_inicio (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    image_path TEXT,
    fair_id BIGINT REFERENCES jornadas(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_by BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX IF NOT EXISTS idx_home_banners_active_order
    ON banners_inicio(active, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_home_banners_fair
    ON banners_inicio(fair_id) WHERE fair_id IS NOT NULL;
INSERT INTO versiones_esquema(version, description)
VALUES (85, 'Carrusel de banners del inicio con orden, vigencia y asociación opcional a jornadas')
ON CONFLICT(version) DO UPDATE SET description=EXCLUDED.description, applied_at=NOW();
