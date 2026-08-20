# Credicash Backend 1.1.0

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
- `REQUIRE_STABLE_JWT_SECRET=true`: impide iniciar producción con una clave temporal.
- `UPLOAD_DIR=/data/uploads`.

Si existe una interfaz web, define `CORS_ALLOWED_ORIGINS` con sus orígenes HTTPS separados por coma. Cuando esta variable queda vacía en producción, ningún navegador externo recibe permisos CORS; las aplicaciones móviles nativas no se ven afectadas.

Las variables `BOOTSTRAP_ACCOUNTANT_PASSWORD` y `BOOTSTRAP_ACCOUNTANT_PIN` solo son necesarias durante la primera instalación. Una vez creado y verificado el Contador, retíralas de Railway y conserva las credenciales en un gestor seguro. Eliminar esas variables no borra la cuenta guardada en PostgreSQL.

Los tokens de acceso vencen después de `JWT_ACCESS_TOKEN_TTL_MINUTES` (60 por defecto). Las sesiones persistentes usan una vigencia móvil de `PERSISTENT_SESSION_TTL_DAYS` (30 por defecto) y se renuevan mediante `/api/v1/auth/refresh`.

`PUBLIC_BASE_URL` es opcional en Railway porque se deriva de `RAILWAY_PUBLIC_DOMAIN`. Las variables `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` y `PGDATABASE` siguen admitidas como alternativa. También se conservan nombres históricos de URL PostgreSQL para facilitar migraciones.

## Persistencia de uploads

Sin un volumen, los archivos escritos dentro del contenedor se pierden al redesplegar. Monta el volumen Railway en `/data`; la aplicación creará y usará `/data/uploads`. Utiliza una sola réplica mientras los uploads dependan de este volumen local.

Las imágenes de catálogo conservan URLs públicas. Los documentos de identidad, documentos del personal y comprobantes de pago se entregan mediante URLs firmadas que vencen; no expongas el contenido del volumen con otro servidor estático.

## Control presupuestario 1.1

El presupuesto se organiza con cinco dimensiones independientes: periodo, grupo/subcategoría de costo, centro de costo, partida y responsable o proyecto. Los grupos principales son:

- Costos de mercancía e inventario.
- Gastos operativos.
- Gastos administrativos.
- Gastos comerciales y marketing.
- Gastos financieros.
- Gastos extraordinarios.

Cada partida muestra monto aprobado, modificaciones, compromisos, ejecución real y saldo disponible. Los compromisos de categorías sensibles o desde US$ 1.000 crean una solicitud de doble aprobación; la misma persona que solicita no puede aprobar.

Rutas del Contador añadidas:

- `GET /api/v1/accountant/budget/catalog`
- `GET|POST /api/v1/accountant/cost-centers`
- `GET|POST /api/v1/accountant/budget/periods`
- `PATCH /api/v1/accountant/budget/periods/{id}/status`
- `POST /api/v1/accountant/budget/lines`
- `GET /api/v1/accountant/budget/dashboard?periodId={id}`
- `POST /api/v1/accountant/budget/commitments`
- `PATCH /api/v1/accountant/budget/commitments/{id}/status`
- `POST /api/v1/accountant/budget/adjustments`

`POST /api/v1/accountant/budget-movements` sigue siendo compatible con la aplicación anterior. Los clientes nuevos pueden enviar `costCategoryCode`, `costCenterId`, `budgetPeriodId`, `budgetLineId`, `commitmentId`, proveedor, factura, fecha, método de pago, comportamiento fijo/variable, recurrencia, proyecto y comprobante.

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
