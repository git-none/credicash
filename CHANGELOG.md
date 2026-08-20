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
