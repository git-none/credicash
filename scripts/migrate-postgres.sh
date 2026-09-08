#!/usr/bin/env bash
set -euo pipefail
: "${SOURCE_DATABASE_URL:?Define SOURCE_DATABASE_URL con la URL PostgreSQL actual}"
: "${TARGET_DATABASE_URL:?Define TARGET_DATABASE_URL con la URL PostgreSQL de destino}"
if [[ "${CONFIRM_MIGRATION:-}" != "YES" ]]; then
  echo "Por seguridad define CONFIRM_MIGRATION=YES para ejecutar la migración." >&2
  exit 2
fi
command -v pg_dump >/dev/null || { echo "Falta pg_dump" >&2; exit 3; }
command -v pg_restore >/dev/null || { echo "Falta pg_restore" >&2; exit 3; }
DUMP_FILE="${DUMP_FILE:-credicash_migration.dump}"
echo "Exportando base origen..."
pg_dump "$SOURCE_DATABASE_URL" --format=custom --no-owner --no-acl --file "$DUMP_FILE"
echo "Restaurando en PostgreSQL de destino..."
pg_restore --dbname="$TARGET_DATABASE_URL" --clean --if-exists --no-owner --no-acl "$DUMP_FILE"
echo "Migración PostgreSQL completada: $DUMP_FILE"
