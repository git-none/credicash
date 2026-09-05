# Kredi+ Backend 1.1.8

Backend Kotlin/Ktor de Kredi+, preparado para actualizar el servicio existente en Railway con Docker y PostgreSQL. Mantiene compatibilidad de API y nombres técnicos históricos para no romper el APK ni las sesiones actuales.

## Actualizar el servicio que YA existe en Railway

Este paquete **no requiere crear otro servicio ni otra base de datos**. Debe desplegarse sobre el backend Kredi+/Credicash que ya está conectado a Railway.

1. Sustituye el código del repositorio/rama que ya usa el servicio Railway por esta versión y haz push, o ejecuta un redeploy desde la fuente existente.
2. **No cambies `DATABASE_URL`**: debe seguir apuntando al mismo PostgreSQL que contiene tus usuarios y operaciones actuales.
3. **No cambies `JWT_SECRET`**: si lo reemplazas, las sesiones existentes dejarán de validar.
4. Conserva `UPLOAD_DIR=/data/uploads` y el mismo volumen persistente montado en `/data` si ya lo estás usando.
5. Conserva el dominio público actual. El APK Kredi+ está configurado para la API de Railway y no necesita un dominio nuevo.
6. Al arrancar, el backend toma un `pg_advisory_xact_lock`, ejecuta el esquema de forma idempotente y aplica/verifica la migración 84 antes de marcar `/health/ready` como disponible.
7. Espera a que Railway muestre el nuevo deployment como healthy antes de probar suspensión/eliminación desde Android.

### Variables que NO debes regenerar durante esta actualización

- `DATABASE_URL`
- `JWT_SECRET`
- `PUBLIC_BASE_URL` si usas dominio personalizado
- credenciales/configuración de Firebase
- referencias a volumen/`UPLOAD_DIR`

Las variables `BOOTSTRAP_ACCOUNTANT_*` no son necesarias si el Contador ya existe. En 1.1.5 pueden actuar como recuperación segura: si el login usa exactamente el usuario/correo y la contraseña configurados en Railway, y existe un único Contador, el backend sincroniza su hash de contraseña y PIN. Después de verificar el acceso, se recomienda retirar o rotar esas variables.

### Migración 84

La migración 84 corrige las relaciones que impedían eliminar usuarios realmente vacíos y normaliza `suspended_by` con `ON DELETE SET NULL`. El esquema se ejecuta automáticamente durante el arranque y ahora `verifyRequiredSchema()` exige que la versión 84 haya quedado registrada. **No ejecutes `repair-account-management-v84.sql` manualmente antes del despliegue** salvo que el arranque reporte explícitamente que la migración automática no pudo completarse.

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

Las variables `BOOTSTRAP_ACCOUNTANT_PASSWORD` y `BOOTSTRAP_ACCOUNTANT_PIN` sirven para la primera instalación y, desde 1.1.5, también como recuperación controlada de una cuenta Contador histórica desincronizada. Una vez confirmado el acceso, retíralas o rótalas en Railway y conserva las credenciales en un gestor seguro. Eliminar esas variables no borra la cuenta guardada en PostgreSQL.

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


## JWT en Railway existente (1.1.2)

Si el servicio histórico no tiene `JWT_SECRET`, el backend ya no entra en un ciclo de reinicios.
Con el volumen persistente montado en `/data`, crea una clave criptográfica estable en
`/data/.kredi-secrets/jwt-secret` y la reutiliza. La ruta queda fuera de `/data/uploads` y no se
expone por el endpoint de archivos. Si prefieres administrar la clave desde Railway, define
`JWT_SECRET` y conserva el mismo valor en todos los despliegues.
