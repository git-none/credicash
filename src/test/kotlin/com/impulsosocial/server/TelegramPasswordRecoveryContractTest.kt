package com.impulsosocial.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class TelegramPasswordRecoveryContractTest {
    private fun projectFile(relative: String): String = Files.readString(Path.of(relative))

    @Test
    fun `schema contiene solicitudes temporales de recuperacion por telegram`() {
        val schema = projectFile("src/main/resources/db/schema.sql")
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS solicitudes_recuperacion_telegram"))
        assertTrue(schema.contains("start_token_hash"))
        assertTrue(schema.contains("code_expires_at"))
        assertTrue(schema.contains("VALUES (87,"))
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS vinculaciones_recuperacion_telegram"))
        assertTrue(schema.contains("VALUES (90,"))
    }

    @Test
    fun `api habilita recuperacion y webhook exclusivamente para telegram`() {
        val application = projectFile("src/main/kotlin/com/impulsosocial/server/Application.kt")
        assertTrue(application.contains("post(\"/forgot-password\")"))
        assertTrue(application.contains("post(\"/password-recovery/request\")"))
        assertTrue(application.contains("post(\"/password-recovery/reset\")"))
        assertTrue(application.contains("resetPasswordWithTelegram"))
        assertTrue(application.contains("/api/v1/integrations/telegram/webhook"))
        assertTrue(application.contains("X-Telegram-Bot-Api-Secret-Token"))
    }

    @Test
    fun `servicio restablece password y pin y permite recuperacion repetible`() {
        val service = projectFile("src/main/kotlin/com/impulsosocial/server/service/AppService.kt")
        assertTrue(service.contains("ACCESS_RESET_TELEGRAM"))
        assertTrue(service.contains("sesiones_usuario"))
        assertTrue(service.contains("passwordSecurity.hash(newPassword)"))
        assertTrue(service.contains("passwordSecurity.hash(newPin)"))
        assertTrue(service.contains("vinculaciones_recuperacion_telegram"))
        assertTrue(service.contains("/recuperar"))
        assertTrue(service.contains("credenciales_biometricas_dispositivo"))
        assertTrue(service.contains("PASSWORD_RESET_TELEGRAM_REQUEST_DEDUPLICATED"))
        assertTrue(service.contains("CODE_ALREADY_SENT"))
        assertTrue(service.contains("RESET_ACCESS"))
    }
}
