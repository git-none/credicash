package com.impulsosocial.server.integrations

import com.google.gson.Gson
import com.impulsosocial.server.config.AppConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory

class TelegramBotService(private val config: AppConfig) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)
    private val gson = Gson()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val configured: Boolean
        get() = config.telegramBotToken.isNotBlank() &&
            config.telegramBotUsername.isNotBlank() &&
            config.telegramWebhookSecret.isNotBlank()

    fun botDeepLink(startParameter: String): String {
        check(config.telegramBotUsername.isNotBlank()) { "TELEGRAM_BOT_USERNAME no está configurado." }
        val username = config.telegramBotUsername.removePrefix("@")
        return "https://t.me/$username?start=$startParameter"
    }

    fun configureWebhook(): Boolean {
        if (!configured) {
            logger.info("Telegram recovery: integración no configurada; no se registrará webhook.")
            return false
        }
        val webhookUrl = "${config.publicBaseUrl.trimEnd('/')}/api/v1/integrations/telegram/webhook"
        val body = mapOf(
            "url" to webhookUrl,
            "secret_token" to config.telegramWebhookSecret,
            "allowed_updates" to listOf("message")
        )
        val response = postTelegram("setWebhook", body)
        logger.info("Telegram recovery: webhook configurado en {}.", webhookUrl)
        return response
    }

    fun sendMessage(chatId: Long, text: String): Boolean = postTelegram(
        method = "sendMessage",
        payload = mapOf(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to "HTML",
            "disable_web_page_preview" to true
        )
    )

    private fun postTelegram(method: String, payload: Any): Boolean {
        if (config.telegramBotToken.isBlank()) return false
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot${config.telegramBotToken}/$method"))
            .timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            logger.warn("Telegram API {} respondió HTTP {}: {}", method, response.statusCode(), response.body().take(500))
            return false
        }
        return true
    }
}
