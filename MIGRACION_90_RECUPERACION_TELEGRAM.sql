-- Kredi+ Backend 1.1.14
-- Migración 90: recuperación repetible por Telegram y reset conjunto contraseña + PIN.
BEGIN;

CREATE TABLE IF NOT EXISTS vinculaciones_recuperacion_telegram (
    user_id BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    telegram_chat_id BIGINT NOT NULL,
    telegram_username VARCHAR(64) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_telegram_recovery_binding_username
ON vinculaciones_recuperacion_telegram(LOWER(telegram_username));
CREATE INDEX IF NOT EXISTS idx_telegram_recovery_binding_chat
ON vinculaciones_recuperacion_telegram(telegram_chat_id);

INSERT INTO vinculaciones_recuperacion_telegram(user_id,telegram_chat_id,telegram_username,verified_at,updated_at)
SELECT DISTINCT ON (user_id)
       user_id,telegram_chat_id,telegram_username,COALESCE(code_sent_at,created_at),NOW()
FROM solicitudes_recuperacion_telegram
WHERE telegram_chat_id IS NOT NULL AND code_sent_at IS NOT NULL
ORDER BY user_id,code_sent_at DESC NULLS LAST,created_at DESC
ON CONFLICT(user_id) DO UPDATE SET
    telegram_chat_id=EXCLUDED.telegram_chat_id,
    telegram_username=EXCLUDED.telegram_username,
    verified_at=EXCLUDED.verified_at,
    updated_at=NOW();

INSERT INTO versiones_esquema(version, description)
VALUES (90, 'Recuperación Telegram repetible y restablecimiento conjunto de contraseña y PIN')
ON CONFLICT(version) DO UPDATE SET description=EXCLUDED.description, applied_at=NOW();

COMMIT;
