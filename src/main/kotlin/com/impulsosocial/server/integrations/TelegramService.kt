package com.impulsosocial.server.integrations

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.impulsosocial.server.config.AppConfig
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import org.slf4j.LoggerFactory

class TelegramDeliveryException(
    val userMessage: String,
    technicalMessage: String,
    cause: Throwable? = null
) : IllegalStateException(technicalMessage, cause)

/**
 * Canal oficial de códigos de seguridad de Credicash mediante Telegram Bot API.
 *
 * El token del bot y el secreto del webhook solo se leen desde variables de entorno.
 * Ningún dato sensible se registra ni se devuelve a la aplicación Android.
 */
class TelegramService(private val config: AppConfig) {
    private val logger = LoggerFactory.getLogger(TelegramService::class.java)
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val configurationProblem: String?
        get() = when {
            config.telegramBotToken.isBlank() -> "Falta TELEGRAM_BOT_TOKEN."
            !config.telegramBotToken.matches(Regex("^\\d+:[A-Za-z0-9_-]{20,}$")) ->
                "TELEGRAM_BOT_TOKEN no tiene el formato esperado. Evita pegarlo entre comillas."
            config.telegramBotUsername.isBlank() -> "Falta TELEGRAM_BOT_USERNAME."
            !config.telegramBotUsername.matches(Regex("^[A-Za-z0-9_]{5,32}$")) ->
                "TELEGRAM_BOT_USERNAME no es válido. Colócalo sin @."
            config.telegramWebhookSecret.isBlank() -> "Falta TELEGRAM_WEBHOOK_SECRET."
            !config.telegramWebhookSecret.matches(Regex("^[A-Za-z0-9_-]{16,256}$")) ->
                "TELEGRAM_WEBHOOK_SECRET debe tener entre 16 y 256 caracteres seguros."
            else -> null
        }

    val enabled: Boolean
        get() = configurationProblem == null

    fun unavailableMessage(): String =
        "El bot de Telegram de Credicash no está disponible en este momento. Inténtalo nuevamente más tarde."

    fun deepLink(startParameter: String): String {
        require(startParameter.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) {
            "El parámetro de vinculación de Telegram no es válido."
        }
        return "https://t.me/${config.telegramBotUsername}?start=$startParameter"
    }

    fun webhookUrl(): String =
        config.telegramWebhookUrl.trim().takeIf { it.isNotBlank() }
            ?: "${config.publicBaseUrl.trimEnd('/')}/api/v1/integrations/telegram/webhook"

    fun isValidWebhookSecret(received: String?): Boolean {
        if (!enabled || received.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            config.telegramWebhookSecret.toByteArray(StandardCharsets.UTF_8),
            received.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun configureWebhook() {
        ensureEnabled()
        if (!webhookUrl().startsWith("https://", ignoreCase = true)) {
            logger.warn(
                "Webhook de Telegram no configurado porque la URL no usa HTTPS: {}",
                webhookUrl()
            )
            return
        }
        val payload = mapOf(
            "url" to webhookUrl(),
            "secret_token" to config.telegramWebhookSecret,
            "allowed_updates" to listOf("message"),
            "drop_pending_updates" to false
        )
        callApi("setWebhook", payload)
        logger.info("Webhook del bot de Credicash configurado en {}.", webhookUrl())
    }

    fun sendAccountVerificationCode(chatId: Long, code: String, firstName: String? = null) {
        val greeting = firstName?.trim()?.takeIf { it.isNotBlank() }?.let { "Hola, ${escapeHtml(it)}.\n\n" }.orEmpty()
        sendHtmlMessage(
            chatId,
            "🔐 <b>Verificación de Credicash</b>\n\n" +
                greeting +
                "Tu código para activar la cuenta es:\n\n" +
                "<code>${escapeHtml(code)}</code>\n\n" +
                "⏳ Vence en 10 minutos y funciona una sola vez.\n" +
                "⚠️ No compartas este código con nadie."
        )
    }

    fun sendPasswordResetCode(chatId: Long, code: String, firstName: String? = null) {
        val greeting = firstName?.trim()?.takeIf { it.isNotBlank() }?.let { "Hola, ${escapeHtml(it)}.\n\n" }.orEmpty()
        sendHtmlMessage(
            chatId,
            "🛡️ <b>Recuperación de acceso · Credicash</b>\n\n" +
                greeting +
                "Tu código para crear una nueva contraseña es:\n\n" +
                "<code>${escapeHtml(code)}</code>\n\n" +
                "⏳ Vence en 10 minutos y funciona una sola vez.\n" +
                "Si no solicitaste este cambio, ignora el mensaje."
        )
    }

    fun sendHelp(chatId: Long) {
        sendHtmlMessage(
            chatId,
            "🤖 <b>Bot oficial de Credicash</b>\n\n" +
                "• Abre el enlace de vinculación desde la aplicación.\n" +
                "• Usa /codigo para solicitar un código nuevo cuando tu cuenta ya esté vinculada.\n" +
                "• Nunca compartas los códigos recibidos en este chat."
        )
    }

    fun sendPlainMessage(chatId: Long, text: String) {
        sendHtmlMessage(chatId, escapeHtml(text))
    }

    private fun sendHtmlMessage(chatId: Long, text: String) {
        ensureEnabled()
        callApi(
            method = "sendMessage",
            payload = mapOf(
                "chat_id" to chatId,
                "text" to text,
                "parse_mode" to "HTML",
                "protect_content" to true,
                "link_preview_options" to mapOf("is_disabled" to true)
            )
        )
    }

    private fun callApi(method: String, payload: Any) {
        ensureEnabled()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot${config.telegramBotToken}/$method"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
            .build()

        var lastFailure: Throwable? = null
        for (attempt in 1..3) {
            try {
                val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                val parsed = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrNull()
                val ok = parsed?.get("ok")?.asBoolean == true
                if (response.statusCode() in 200..299 && ok) return

                val description = parsed?.get("description")?.asString.orEmpty().take(400)
                logger.error(
                    "Telegram Bot API rechazó {} con HTTP {} (intento {}): {}",
                    method,
                    response.statusCode(),
                    attempt,
                    description
                )
                if (isRetryableStatus(response.statusCode()) && attempt < 3) {
                    pauseBeforeRetry(attempt)
                    continue
                }
                throw TelegramDeliveryException(
                    userMessage = telegramUserMessage(response.statusCode(), description),
                    technicalMessage = "Telegram rechazó $method con HTTP ${response.statusCode()}: $description"
                )
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw TelegramDeliveryException(
                    userMessage = "No pudimos contactar el bot de Telegram. Inténtalo nuevamente.",
                    technicalMessage = "La operación de Telegram fue interrumpida.",
                    cause = error
                )
            } catch (error: IOException) {
                lastFailure = error
                logger.warn(
                    "Fallo de red al contactar Telegram para {} (intento {}/3): {}",
                    method,
                    attempt,
                    error.message
                )
                if (attempt < 3) {
                    pauseBeforeRetry(attempt)
                    continue
                }
            } catch (error: TelegramDeliveryException) {
                throw error
            }
        }

        throw TelegramDeliveryException(
            userMessage = "No pudimos contactar el bot de Telegram por un problema temporal. Inténtalo nuevamente en unos minutos.",
            technicalMessage = "Telegram no respondió después de tres intentos.",
            cause = lastFailure
        )
    }

    private fun ensureEnabled() {
        if (!enabled) {
            throw TelegramDeliveryException(
                userMessage = unavailableMessage(),
                technicalMessage = configurationProblem ?: "Bot de Telegram no configurado."
            )
        }
    }

    private fun telegramUserMessage(statusCode: Int, description: String): String {
        val lower = description.lowercase()
        return when {
            statusCode == 401 -> "El bot de Telegram no está autorizado."
            statusCode == 403 && "bot was blocked" in lower ->
                "El bot de Credicash está bloqueado en Telegram. Desbloquéalo y vuelve a intentarlo."
            statusCode == 400 && "chat not found" in lower ->
                "Abre primero el bot de Credicash en Telegram y pulsa Iniciar."
            statusCode == 429 -> "Telegram limitó temporalmente los envíos. Espera un momento e inténtalo otra vez."
            statusCode in 500..599 -> "Telegram presenta una falla temporal. Inténtalo nuevamente en unos minutos."
            else -> "No pudimos enviar el código por Telegram. Abre el bot de Credicash y vuelve a intentarlo."
        }
    }

    private fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode in setOf(408, 425, 429) || statusCode in 500..599

    private fun pauseBeforeRetry(attempt: Int) {
        try {
            Thread.sleep(350L * attempt)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TelegramDeliveryException(
                userMessage = "No pudimos contactar el bot de Telegram. Inténtalo nuevamente.",
                technicalMessage = "La espera entre reintentos fue interrumpida.",
                cause = error
            )
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
