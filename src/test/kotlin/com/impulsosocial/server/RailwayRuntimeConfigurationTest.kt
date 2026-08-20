package com.impulsosocial.server

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RailwayRuntimeConfigurationTest {
    private fun projectFile(relative: String): String = Files.readString(Path.of(relative))

    @Test
    fun `railway usa docker y readiness de aplicacion`() {
        val railway = projectFile("railway.toml")
        val dockerfile = projectFile("Dockerfile")
        val application = projectFile("src/main/kotlin/com/impulsosocial/server/Application.kt")
        assertTrue(railway.contains("builder = \"DOCKERFILE\""))
        assertTrue(railway.contains("healthcheckPath = \"/health/ready\""))
        assertTrue(dockerfile.contains("EXPOSE 8080"))
        assertTrue(dockerfile.contains("ENV PORT=8080"))
        assertTrue(application.contains("get(\"/health/live\")"))
        assertTrue(application.contains("get(\"/health/ready\")"))
    }

    @Test
    fun `railway prioriza database url y conserva alternativas postgres`() {
        val config = projectFile("src/main/kotlin/com/impulsosocial/server/config/AppConfig.kt")
        val databaseUrlIndex = config.indexOf("\"DATABASE_URL\",")
        val customIndex = config.indexOf("\"CREDICASH_DATABASE_URL\",")
        val pgIndex = config.indexOf("databaseEnvironmentFromPgVariables()")
        assertTrue(databaseUrlIndex >= 0, "Falta soporte para DATABASE_URL")
        assertTrue(customIndex > databaseUrlIndex, "DATABASE_URL debe ser la opción principal")
        assertTrue(pgIndex > customIndex, "PG* debe conservarse como alternativa")
        assertTrue(config.contains("RAILWAY_PUBLIC_DOMAIN"), "Debe reconocer el dominio público de Railway")
        assertTrue(config.contains("value.replace(\"+\", \"%2B\")"), "El decodificador no debe convertir + en espacio")
    }

    @Test
    fun `uploads apuntan al volumen persistente documentado`() {
        val dockerfile = projectFile("Dockerfile")
        val example = projectFile(".env.example")
        val readme = projectFile("README.md")
        assertTrue(dockerfile.contains("UPLOAD_DIR=/data/uploads"))
        assertTrue(example.contains("UPLOAD_DIR=/data/uploads"))
        assertTrue(readme.contains("/data/uploads"))
        assertTrue(readme.contains("/data"))
    }

    @Test
    fun `el apagado cierra listeners antes del pool`() {
        val application = projectFile("src/main/kotlin/com/impulsosocial/server/Application.kt")
        val notifier = projectFile("src/main/kotlin/com/impulsosocial/server/LedgerRealtimeNotifier.kt")
        val stopIndex = application.indexOf("ledgerRealtimeNotifier.stop()")
        val databaseCloseIndex = application.indexOf("database.close()")
        assertTrue(stopIndex >= 0)
        assertTrue(databaseCloseIndex > stopIndex)
        assertTrue(notifier.contains("activeConnection.getAndSet(null)"))
        assertTrue(notifier.contains("listenerJob.getAndSet(null)?.cancel()"))
    }

    @Test
    fun `listeners esperan a migraciones listas`() {
        val application = projectFile("src/main/kotlin/com/impulsosocial/server/Application.kt")
        val initIndex = application.indexOf("initializeDatabaseInBackground(")
        val listenerIndex = application.indexOf("ledgerRealtimeNotifier.start(startupScope)", initIndex)
        assertTrue(initIndex >= 0)
        assertTrue(listenerIndex > initIndex)
        assertTrue(application.contains("bootstrapAccountantValidationError()"))
    }
}
