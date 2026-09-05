# Corrección Railway 1.1.2 — JWT persistente

## Error observado
Railway iniciaba el contenedor y Ktor abortaba inmediatamente con:

`JWT_SECRET es obligatorio en producción`

El servicio entraba en un ciclo de reinicios antes de abrir la API.

## Causa
La versión histórica desplegada no tenía una variable `JWT_SECRET` estable. La validación de seguridad añadida en 1.1.1 hizo correctamente fail-closed, pero eso impidió actualizar instalaciones antiguas que ya tenían un volumen persistente.

## Corrección
1. Se siguen priorizando `JWT_SECRET`, `KREDI_JWT_SECRET` y `JWT_SIGNING_SECRET`.
2. Si ninguna existe en Railway, Kredi+ crea una clave aleatoria criptográfica de 48 bytes una sola vez.
3. La clave se guarda por defecto en `/data/.kredi-secrets/jwt-secret`, fuera de `/data/uploads`.
4. En reinicios y nuevos despliegues se reutiliza exactamente la misma clave.
5. Si no existe variable y tampoco hay almacenamiento persistente escribible, el backend continúa fallando de forma segura; no usa una clave efímera en producción.
6. `JWT_SECRET_FILE` permite elegir otra ruta persistente.

## Railway existente
No crees otro servicio ni otra base de datos. Conserva el volumen montado en `/data` y `UPLOAD_DIR=/data/uploads`.

Si ya tienes `JWT_SECRET` configurado, no cambia nada: esa variable sigue teniendo prioridad y el archivo de respaldo no se usa.
