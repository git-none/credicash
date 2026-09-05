# 1.1.9 — Estabilidad de carrusel y auditoría móvil

- El orden de nuevos banners se asigna en el backend de forma automática y transaccional.
- Al editar un banner se conserva su posición actual sin depender de campos ocultos del cliente.
- Evita órdenes duplicados por listas desactualizadas o creación concurrente desde dos administradores.
- Mantiene compatibilidad con clientes 7.2.12–7.2.15 que todavía envían `sortOrder`.

# 1.1.8 — Carrusel de banners administrable

- Agrega banners del inicio con imagen, orden, vigencia y estado.
- Permite asociar un banner a una jornada.
- Si la jornada no está disponible, el cliente muestra “Próximamente”.
- Incluye CRUD administrativo y carga de imagen del banner.

# 1.1.7 — Combos editables/eliminables y coherencia de despliegue

- Añade edición de combos conservando el mismo ID.
- Mantiene eliminación lógica para preservar referencias históricas.
- Corrige la versión del Dockerfile a 1.1.7 para que coincida con Gradle, runtime y OpenAPI.
- Desbloquea `RailwayRuntimeConfigurationTest` y el build Docker/Railway.

# 1.1.5 — Recuperación segura de credenciales del Contador

- Corrige instalaciones donde `BOOTSTRAP_ACCOUNTANT_PASSWORD`/`PIN` de Railway no coincidían con los hashes históricos ya guardados en PostgreSQL.
- Si el intento de login presenta exactamente la contraseña bootstrap configurada y existe un único Contador, se sincronizan contraseña y PIN de forma transaccional y auditada.
- Un redeploy por sí solo no cambia credenciales: la recuperación solo se ejecuta durante un intento de login que coincide con la contraseña protegida.
- No crea otra cuenta, no borra datos y no requiere migración de esquema.

# Changelog

## 1.1.3 - 2026-08-25

- Corrige aprobación de solicitudes de registro desde el perfil Contador.
- Añade `POST /accountant/users/{id}/verification`, que es el contrato utilizado por Android.
- Añade alias `/accountant/usuarios/{id}/verification` y `/accountant/verifications/{id}/review`.
- El rol Contador incluye `REVIEW_USERS`, coherente con su función de revisión documental.
- Añade prueba de contrato para impedir que estas rutas desaparezcan en futuras versiones.
- Mantiene PostgreSQL, Railway, JWT persistente y migraciones existentes sin reinicializar datos.

# Kredi+ Backend 1.1.2

- Corrige el crash loop de Railway cuando una instalación histórica no tenía `JWT_SECRET`.
- Busca primero `JWT_SECRET`, `KREDI_JWT_SECRET` o `JWT_SIGNING_SECRET`.
- Si no existe variable en producción, crea/reutiliza una clave segura persistente fuera de `/uploads`.
- Mantiene la validación fail-closed si no hay variable ni volumen escribible.
- Añade prueba de persistencia de la clave JWT.
- Conserva las correcciones de operaciones y migración 84 de 1.1.1.

# 1.1.1 - Railway existing-service hotfix

- Corrige `RailwayRuntimeConfigurationTest`: elimina el versionado rígido 1.1.0 y valida coherencia automática entre Gradle, runtime, Docker y OpenAPI.
- OpenAPI actualizado a 1.1.1 para coincidir con el runtime desplegado.

- Actualización in-place para el servicio Railway existente; no requiere nueva base ni nuevo dominio.
- Verificación obligatoria de la migración 84 antes de declarar el backend READY.
- Conserva compatibilidad de API, `DATABASE_URL`, `JWT_SECRET` y sesiones actuales.
- Documentación corregida para evitar crear accidentalmente un segundo PostgreSQL/servicio.

# Historial de versiones

## 1.1.0 — 2026-08-20

Mejora integral del control presupuestario y del endurecimiento operativo.

- Incorpora catálogo jerárquico para inventario, gastos operativos, administrativos, comerciales, financieros y extraordinarios.
- Añade centros de costo, periodos, partidas, compromisos, ajustes y transferencias presupuestarias.
- Conserva los movimientos históricos y reclasifica sus categorías sin eliminarlos.
- Distingue presupuesto aprobado, modificado, comprometido, ejecutado y disponible.
- Genera alertas al 70 %, 85 % y 100 %, y detecta gastos sin partida, sin comprobante o clasificados como Otros.
- Exige doble aprobación para compromisos sensibles o iguales/superiores a US$ 1.000.
- Añade idempotencia y control de concurrencia a las operaciones presupuestarias.
- Rechaza PIN nuevos triviales, repetidos o secuenciales sin invalidar las credenciales existentes.
- Limita por origen los intentos de registro, login, PIN y renovación de sesión.
- Exige un `JWT_SECRET` estable en producción y restringe CORS mediante una lista autorizada.
- Añade encabezados de seguridad e identificadores de solicitud para rastrear errores en Railway.

## 1.0.0 — 2026-08-20

Primera versión estable del backend de Credicash preparada para Railway.

- Conserva la lógica de negocio Kotlin/Ktor y el esquema PostgreSQL existente.
- Admite `DATABASE_URL`, las variables `PG*` y el puerto dinámico `PORT` de Railway.
- Incluye healthchecks de proceso y disponibilidad de PostgreSQL.
- Documenta y configura `/data/uploads` para un volumen persistente.
- Protege documentos de identidad y comprobantes mediante enlaces firmados temporales.
- Hace privadas por defecto las categorías de uploads que no estén autorizadas expresamente como públicas.
- Añade vencimiento a los JWT y limita la vigencia móvil de las sesiones persistentes.
- Bloquea el desafío de PIN después de cinco intentos fallidos.
- Rechaza secretos JWT menores de 32 bytes.
- Incorpora pruebas automatizadas y verificación continua en GitHub Actions.
