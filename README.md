# Kredi+ Backend Railway 1.1.29

Backend de Kredi+ preparado para el servicio Railway existente.

## Persistencia de archivos

El contenedor usa `UPLOAD_DIR=/data/uploads` para fotografías, comprobantes y demás archivos subidos.
En Railway se debe mantener un volumen persistente montado en `/data` para que `/data/uploads` sobreviva a los redeploys.

Variables relevantes:

```env
UPLOAD_DIR=/data/uploads
```

El `Dockerfile` también define `UPLOAD_DIR=/data/uploads` y crea el directorio `/data/uploads` durante la construcción de la imagen final.

## Base de datos

Mantener el mismo `DATABASE_URL` del servicio en producción. Las migraciones incluidas en `src/main/resources/db/schema.sql` son idempotentes y conservan los datos existentes.

## Destinos de pago del catálogo

La versión 1.1.29 conserva la configuración separada de negocio receptor para ventas individuales y combos mediante `/catalog-sales-destinations`.

## Railway

El proyecto utiliza `Dockerfile`, escucha en `PORT=8080` y expone los checks `/health/live` y `/health/ready`.
