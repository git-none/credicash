# Kredi+ Backend 1.1.12 — Recuperación de contraseña por Telegram

Esta integración se utiliza **únicamente para recuperar contraseñas existentes**. No modifica el registro, el inicio de sesión normal ni vincula permanentemente Telegram al perfil Kredi+.

## Variables que deben existir en Railway

Obligatorias para Telegram:

- `TELEGRAM_BOT_TOKEN` — token del bot entregado por BotFather.
- `TELEGRAM_BOT_USERNAME` — username del bot, sin `@` (también se tolera con `@`).
- `TELEGRAM_WEBHOOK_SECRET` — secreto aleatorio para validar las llamadas del webhook de Telegram.
- `PUBLIC_BASE_URL` — URL HTTPS pública del backend. Si tu despliegue ya la resuelve automáticamente desde Railway, conserva la variable/valor actual que usa Kredi+.

Configuración del código temporal (tienen valores seguros por defecto, pero puedes definirlas):

- `PASSWORD_RESET_CODE_TTL_MINUTES=5`
- `PASSWORD_RESET_REQUEST_TTL_MINUTES=15`
- `PASSWORD_RESET_MAX_ATTEMPTS=5`
- `PASSWORD_RESET_CODE_LENGTH=6`

Variables de seguridad existentes que deben mantenerse, no reemplazarse:

- `JWT_SECRET`
- variables PostgreSQL/DATABASE_URL actuales
- variables reCAPTCHA actuales si `RECAPTCHA_REQUIRED=true`

## Flujo

1. En Kredi+ el usuario abre **Recuperar contraseña**.
2. Escribe primero su `@usuario` de Telegram y luego su usuario o correo Kredi+.
3. El backend crea una solicitud temporal y la app abre `t.me/<bot>?start=reset_<token>`.
4. El usuario pulsa **Iniciar** en Telegram.
5. El webhook valida el token temporal y que el Telegram abierto corresponda al `@` escrito en esa solicitud. Ese dato **no se guarda en el perfil**.
6. El bot genera y envía un código de 6 dígitos con vencimiento.
7. El usuario vuelve a Kredi+, escribe el código y su nueva contraseña.
8. Al confirmar, el código queda consumido y todas las sesiones anteriores quedan revocadas.

El backend registra automáticamente el webhook al iniciar cuando las tres variables de Telegram están configuradas.
