$ErrorActionPreference = "Stop"
if (-not $env:SOURCE_DATABASE_URL) { throw "Define SOURCE_DATABASE_URL con la URL PostgreSQL actual." }
if (-not $env:TARGET_DATABASE_URL) { throw "Define TARGET_DATABASE_URL con la URL PostgreSQL de destino." }
if ($env:CONFIRM_MIGRATION -ne "YES") { throw "Por seguridad define CONFIRM_MIGRATION=YES para ejecutar la migración." }
$dump = if ($env:DUMP_FILE) { $env:DUMP_FILE } else { "credicash_migration.dump" }
Write-Host "Exportando base origen..."
& pg_dump $env:SOURCE_DATABASE_URL --format=custom --no-owner --no-acl --file $dump
if ($LASTEXITCODE -ne 0) { throw "pg_dump falló." }
Write-Host "Restaurando en PostgreSQL de destino..."
& pg_restore --dbname=$env:TARGET_DATABASE_URL --clean --if-exists --no-owner --no-acl $dump
if ($LASTEXITCODE -ne 0) { throw "pg_restore falló." }
Write-Host "Migración PostgreSQL completada: $dump"
