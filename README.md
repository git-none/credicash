# Credicash Backend 1.0.0

Backend Kotlin/Ktor de Credicash, preparado para desplegarse en Railway con Docker y PostgreSQL.

## Despliegue en Railway

1. Conecta este repositorio como un servicio nuevo. Railway detectará `railway.toml` y construirá `Dockerfile`.
2. Añade PostgreSQL al mismo proyecto.
3. En las variables del backend, crea una referencia `DATABASE_URL` hacia `Postgres.DATABASE_URL`.
4. Define `JWT_SECRET` con al menos 32 bytes aleatorios y mantenlo estable entre despliegues.
5. Genera un dominio público para el servicio. Credicash usa `RAILWAY_PUBLIC_DOMAIN` automáticamente. Si empleas un dominio personalizado, define `PUBLIC_BASE_URL=https://tu-dominio` sin `/api/v1`.
6. Conecta un volumen persistente al backend y móntalo en `/data`. Mantén `UPLOAD_DIR=/data/uploads`.

Railway inyecta `PORT` dinámicamente y la API escucha en `0.0.0.0`. No fijes `PORT` en las variables del servicio.

## Salud

- `GET /health/live`: confirma que el proceso HTTP está vivo.
- `GET /health/ready`: responde 200 únicamente cuando PostgreSQL y las migraciones están listos. Railway usa esta ruta durante el despliegue.
- `GET /api/v1/health`: estado detallado de la API y la base de datos.

## Variables

Copia `.env.example` solo para conocer los nombres disponibles; nunca subas un `.env` real.

Variables mínimas de producción:

- `DATABASE_URL`: referencia privada al PostgreSQL del proyecto.
- `JWT_SECRET`: secreto de sesiones estable.
- `UPLOAD_DIR=/data/uploads`.

Los tokens de acceso vencen después de `JWT_ACCESS_TOKEN_TTL_MINUTES` (60 por defecto). Las sesiones persistentes usan una vigencia móvil de `PERSISTENT_SESSION_TTL_DAYS` (30 por defecto) y se renuevan mediante `/api/v1/auth/refresh`.

`PUBLIC_BASE_URL` es opcional en Railway porque se deriva de `RAILWAY_PUBLIC_DOMAIN`. Las variables `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` y `PGDATABASE` siguen admitidas como alternativa. También se conservan nombres históricos de URL PostgreSQL para facilitar migraciones.

## Persistencia de uploads

Sin un volumen, los archivos escritos dentro del contenedor se pierden al redesplegar. Monta el volumen Railway en `/data`; la aplicación creará y usará `/data/uploads`. Utiliza una sola réplica mientras los uploads dependan de este volumen local.

Las imágenes de catálogo conservan URLs públicas. Los documentos de identidad, documentos del personal y comprobantes de pago se entregan mediante URLs firmadas que vencen; no expongas el contenido del volumen con otro servidor estático.

## Desarrollo y verificación

```bash
./gradlew test
./gradlew buildFatJar
```

Para ejecutar el JAR se necesita PostgreSQL y las variables correspondientes:

```bash
java --enable-native-access=ALL-UNNAMED -jar build/libs/credicash-server-all.jar
```

## Migrar una base existente

Los scripts de `scripts/` usan `pg_dump` y `pg_restore`. Define `SOURCE_DATABASE_URL`, `TARGET_DATABASE_URL` y `CONFIRM_MIGRATION=YES` antes de ejecutarlos. Revisa siempre el destino: la restauración usa `--clean --if-exists`.
