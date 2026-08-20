package com.impulsosocial.server.config

import com.impulsosocial.server.security.PasswordPolicy
import com.impulsosocial.server.security.PinPolicy

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

private data class DatabaseEnvironment(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val source: String
)

private val resolvedDatabaseEnvironment: DatabaseEnvironment by lazy { resolveDatabaseEnvironment() }
private val configuredJwtSecret: String? by lazy { optionalEnv("JWT_SECRET") }
private val generatedJwtSecret: String by lazy {
    val bytes = ByteArray(48)
    SecureRandom().nextBytes(bytes)
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

data class AppConfig(
    val dbUrl: String = resolvedDatabaseEnvironment.jdbcUrl,
    val dbUser: String = resolvedDatabaseEnvironment.user,
    val dbPassword: String = resolvedDatabaseEnvironment.password,
    val dbConfigurationSource: String = resolvedDatabaseEnvironment.source,
    val dbMaximumPoolSize: Int = envInt("DB_POOL_MAX_SIZE", 8, 2, 32),
    val dbMinimumIdle: Int = envInt("DB_POOL_MIN_IDLE", 0, 0, 32).coerceAtMost(dbMaximumPoolSize),
    val dbConnectionTimeoutMs: Long = envLong("DB_CONNECTION_TIMEOUT_MS", 5_000L, 1_000L, 60_000L),
    val dbValidationTimeoutMs: Long = envLong("DB_VALIDATION_TIMEOUT_MS", 3_000L, 500L, 30_000L),
    val dbIdleTimeoutMs: Long = envLong("DB_IDLE_TIMEOUT_MS", 300_000L, 10_000L, 1_800_000L),
    val dbMaxLifetimeMs: Long = envLong("DB_MAX_LIFETIME_MS", 1_500_000L, 60_000L, 3_600_000L),
    val dbKeepaliveTimeMs: Long = envLong("DB_KEEPALIVE_TIME_MS", 120_000L, 30_000L, 600_000L),
    val dbLeakDetectionThresholdMs: Long = envLong("DB_LEAK_DETECTION_THRESHOLD_MS", 0L, 0L, 120_000L)
        .let { if (it in 1L..1_999L) 2_000L else it },
    val jwtSecret: String = configuredJwtSecret ?: generatedJwtSecret,
    val usesGeneratedJwtSecret: Boolean = configuredJwtSecret == null,
    val jwtIssuer: String = env("JWT_ISSUER", "credicash"),
    val jwtAudience: String = env("JWT_AUDIENCE", "credicash-android"),
    val jwtAccessTokenTtlMinutes: Long = envLong("JWT_ACCESS_TOKEN_TTL_MINUTES", 60L, 5L, 1_440L),
    val persistentSessionTtlDays: Long = envLong("PERSISTENT_SESSION_TTL_DAYS", 30L, 1L, 365L),
    val publicBaseUrl: String = resolvePublicBaseUrl(),
    val uploadDir: String = env("UPLOAD_DIR", "data/uploads"),
    val productionMode: Boolean = isProductionEnvironment(),
    val requireStableJwtSecret: Boolean = envBoolean("REQUIRE_STABLE_JWT_SECRET", productionMode),
    val corsAllowedOrigins: List<String> = optionalEnv("CORS_ALLOWED_ORIGINS")
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        .orEmpty(),
    val authRateLimitMaxRequests: Int = envInt("AUTH_RATE_LIMIT_MAX_REQUESTS", 12, 3, 120),
    val authRateLimitWindowSeconds: Long = envLong("AUTH_RATE_LIMIT_WINDOW_SECONDS", 60L, 10L, 3_600L),
    val registrationRateLimitMaxRequests: Int = envInt("REGISTRATION_RATE_LIMIT_MAX_REQUESTS", 5, 1, 30),
    val registrationRateLimitWindowSeconds: Long = envLong("REGISTRATION_RATE_LIMIT_WINDOW_SECONDS", 600L, 60L, 86_400L),
    val bankIntegrationEnabled: Boolean = envBoolean("BANK_INTEGRATION_ENABLED", false),
    // Credicash 7.0.0: únicamente el Contador se provisiona desde variables protegidas.
    val bootstrapAccountantUsername: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_USERNAME", ""),
    val bootstrapAccountantEmail: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_EMAIL", ""),
    val bootstrapAccountantPassword: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_PASSWORD", ""),
    val bootstrapAccountantPin: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_PIN", ""),
    val bootstrapAccountantName: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_NAME", ""),
    val bootstrapAccountantPhone: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_PHONE", ""),
    val bootstrapAccountantBirthDate: String = bootstrapEnv("BOOTSTRAP_ACCOUNTANT_BIRTH_DATE", "1990-01-01"),
    val accountantInitialBudgetUsd: Double = bootstrapEnv("ACCOUNTANT_INITIAL_BUDGET_USD", "1000000").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1_000_000.0,
    val firebaseServiceAccountJson: String? = optionalEnv("FIREBASE_SERVICE_ACCOUNT_JSON"),
    val firebaseServiceAccountBase64: String? = optionalEnv("FIREBASE_SERVICE_ACCOUNT_BASE64"),
    val recaptchaProjectId: String = env("RECAPTCHA_PROJECT_ID", ""),
    val recaptchaApiKey: String = env("RECAPTCHA_API_KEY", ""),
    val recaptchaSiteKey: String = env("RECAPTCHA_SITE_KEY", ""),
    val recaptchaMinScore: Double = env("RECAPTCHA_MIN_SCORE", "0.5").toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
    val recaptchaRequired: Boolean = env("RECAPTCHA_REQUIRED", "false").equals("true", ignoreCase = true)
) {

    val hasAnyBootstrapAccountantValue: Boolean
        get() = listOf(
            bootstrapAccountantEmail,
            bootstrapAccountantPassword,
            bootstrapAccountantPin,
            bootstrapAccountantPhone
        ).any(String::isNotBlank)

    val bootstrapAccountantConfigured: Boolean
        get() = listOf(
            bootstrapAccountantEmail,
            bootstrapAccountantPassword,
            bootstrapAccountantPin,
            bootstrapAccountantPhone
        ).all(String::isNotBlank)

    /**
     * Devuelve un mensaje entendible cuando las variables del Contador inicial están
     * incompletas o pertenecen a una política antigua. Esto no debe tumbar el backend:
     * El proveedor cloud puede conservar variables bootstrap históricas después de que la cuenta
     * ya fue creada.
     */
    fun bootstrapAccountantValidationError(): String? {
        if (!hasAnyBootstrapAccountantValue) return null
        if (!bootstrapAccountantConfigured) {
            return "Para crear el contador inicial debes definir BOOTSTRAP_ACCOUNTANT_EMAIL, BOOTSTRAP_ACCOUNTANT_PASSWORD, BOOTSTRAP_ACCOUNTANT_PIN y BOOTSTRAP_ACCOUNTANT_PHONE."
        }
        PasswordPolicy.validationError(bootstrapAccountantPassword, bootstrapAccountantUsername, bootstrapAccountantEmail)?.let { error ->
            return "BOOTSTRAP_ACCOUNTANT_PASSWORD no cumple la política de seguridad: $error"
        }
        PinPolicy.validationError(bootstrapAccountantPin)?.let { return "BOOTSTRAP_ACCOUNTANT_PIN no cumple la política de seguridad: $it" }
        if (accountantInitialBudgetUsd < 0.0) {
            return "ACCOUNTANT_INITIAL_BUDGET_USD no puede ser negativo."
        }
        return null
    }

    fun validateBootstrapAccountant() {
        bootstrapAccountantValidationError()?.let { throw IllegalArgumentException(it) }
    }

    fun runtimeSecurityValidationError(): String? {
        if (jwtSecret.toByteArray(StandardCharsets.UTF_8).size < 32) {
            return "JWT_SECRET debe contener al menos 32 bytes para proteger las sesiones."
        }
        if (jwtSecret.uppercase().contains("CAMBIA_ESTA_CLAVE") || jwtSecret.lowercase() in setOf("secret", "changeme", "change-me")) {
            return "JWT_SECRET conserva un valor de ejemplo conocido. Genera una clave aleatoria real."
        }
        if (requireStableJwtSecret && usesGeneratedJwtSecret) {
            return "JWT_SECRET es obligatorio en producción. Define una clave aleatoria estable antes de iniciar Credicash."
        }
        corsAllowedOrigins.forEach { origin ->
            val uri = runCatching { URI(origin) }.getOrNull()
                ?: return "CORS_ALLOWED_ORIGINS contiene un origen inválido: $origin"
            val local = uri.host in setOf("localhost", "127.0.0.1")
            val hasUnexpectedParts = !uri.userInfo.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank() ||
                uri.path.orEmpty() !in setOf("", "/")
            if (uri.host.isNullOrBlank() || uri.scheme !in setOf("https", "http") || (uri.scheme == "http" && !local) || hasUnexpectedParts) {
                return "CORS_ALLOWED_ORIGINS solo admite orígenes HTTPS completos; HTTP se reserva para localhost."
            }
        }
        return null
    }

    fun validateRuntimeSecurity() {
        runtimeSecurityValidationError()?.let { throw IllegalArgumentException(it) }
    }

}

private fun resolveDatabaseEnvironment(): DatabaseEnvironment {
    // Railway publica DATABASE_URL para PostgreSQL. Los demás nombres conservan
    // compatibilidad con instalaciones existentes sin depender de un proveedor concreto.
    val urlCandidates = listOf(
        "DATABASE_URL",
        "CREDICASH_DATABASE_URL",
        "DATABASE_PRIVATE_URL",
        "DATABASE_PUBLIC_URL",
        "DB_URL",
        "POSTGRES_URI",
        "JDBC_POSTGRES_URI"
    )

    urlCandidates.forEach { variableName ->
        val value = optionalEnv(variableName)?.stripWrappingQuotes() ?: return@forEach
        val resolved = runCatching { databaseEnvironmentFromAnyUrl(value, variableName) }
            .onFailure { error ->
                System.err.println(
                    "Credicash: se ignoró $variableName porque no contiene una URL PostgreSQL válida: ${error.message}"
                )
            }
            .getOrNull()
        if (resolved != null) {
            warnAboutCompetingDatabaseVariables(resolved.source)
            return resolved
        }
    }

    // Railway también expone PGHOST, PGPORT, PGUSER, PGPASSWORD y PGDATABASE.
    databaseEnvironmentFromPgVariables()?.let { selected ->
        warnAboutCompetingDatabaseVariables(selected.source)
        return selected
    }

    System.err.println(
        "Credicash: no se encontró una configuración PostgreSQL completa. " +
            "En Railway añade una referencia DATABASE_URL desde el servicio PostgreSQL, " +
            "o define las variables PG*. El servidor iniciará para exponer /health/live y seguirá intentando conectar PostgreSQL."
    )
    return DatabaseEnvironment(
        jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/credicash",
        user = env("DB_USER", "credicash_app"),
        password = optionalEnv("DB_PASSWORD")?.stripWrappingQuotes().orEmpty(),
        source = "fallback-local"
    )
}

private fun warnAboutCompetingDatabaseVariables(selectedSource: String) {
    val present = buildList {
        listOf(
            "DATABASE_URL",
            "CREDICASH_DATABASE_URL",
            "DATABASE_PRIVATE_URL",
            "DATABASE_PUBLIC_URL",
            "DB_URL",
            "POSTGRES_URI",
            "JDBC_POSTGRES_URI"
        )
            .filter { !optionalEnv(it).isNullOrBlank() }
            .forEach(::add)
        if (listOf("PGHOST", "PGUSER", "PGPASSWORD").all { !optionalEnv(it).isNullOrBlank() }) add("PG*")
    }.distinct()

    if (present.size > 1) {
        System.err.println(
            "Credicash: hay varias fuentes PostgreSQL configuradas (${present.joinToString(", ")}). " +
                "Se usará $selectedSource. Conserva una sola fuente para evitar ambigüedades."
        )
    }
}

private fun databaseEnvironmentFromPgVariables(): DatabaseEnvironment? {
    val host = optionalEnv("PGHOST")?.stripWrappingQuotes()?.takeIf(String::isNotBlank) ?: return null
    val user = firstEnvironmentValue("PGUSER", "DB_USER") ?: return null
    val password = firstEnvironmentValue("PGPASSWORD", "DB_PASSWORD") ?: return null
    val port = firstEnvironmentValue("PGPORT") ?: "5432"
    val database = firstEnvironmentValue("PGDATABASE") ?: "postgres"

    if (port.toIntOrNull() == null) {
        System.err.println("Credicash: PGPORT no es numérico; se ignorarán temporalmente las variables PG*.")
        return null
    }

    return DatabaseEnvironment(
        jdbcUrl = "jdbc:postgresql://$host:$port/$database",
        user = user,
        password = password,
        source = "PG*"
    )
}

private fun databaseEnvironmentFromAnyUrl(value: String, source: String): DatabaseEnvironment {
    val normalized = value.trim().trim('"', '\'')
    if (normalized.startsWith("jdbc:postgresql://", ignoreCase = true)) {
        // Convierte JDBC a URI PostgreSQL para extraer usuario y contraseña cuando vienen embebidos.
        val portable = normalized.removePrefix("jdbc:")
        return runCatching { databaseEnvironmentFromUrl(portable, source) }
            .getOrElse {
                DatabaseEnvironment(
                    jdbcUrl = normalized,
                    user = firstEnvironmentValue("DB_USER", "PGUSER", "POSTGRES_USER") ?: "credicash_app",
                    password = firstEnvironmentValue("DB_PASSWORD", "PGPASSWORD", "POSTGRES_PASSWORD").orEmpty(),
                    source = source
                )
            }
    }
    return databaseEnvironmentFromUrl(normalized, source)
}

private fun databaseEnvironmentFromUrl(value: String, source: String): DatabaseEnvironment {
    val uri = URI(value.trim())
    require(uri.scheme.equals("postgres", ignoreCase = true) || uri.scheme.equals("postgresql", ignoreCase = true)) {
        "$source debe ser una URL PostgreSQL válida."
    }
    val rawUserInfo = uri.rawUserInfo.orEmpty()
    val userInfoParts = rawUserInfo.split(":", limit = 2)
    val user = userInfoParts.getOrNull(0)?.takeIf { it.isNotBlank() }?.let(::decodeUriComponent)
        ?: firstEnvironmentValue("PGUSER", "DB_USER", "POSTGRES_USER")
        ?: error("$source no incluye usuario y no existe una variable de usuario PostgreSQL.")
    val password = userInfoParts.getOrNull(1)?.let(::decodeUriComponent)
        ?: firstEnvironmentValue("PGPASSWORD", "DB_PASSWORD", "POSTGRES_PASSWORD").orEmpty()
    val host = uri.host ?: error("$source no incluye host.")
    val port = if (uri.port > 0) uri.port else 5432
    val database = uri.rawPath.orEmpty().removePrefix("/").takeIf { it.isNotBlank() }?.let(::decodeUriComponent)
        ?: firstEnvironmentValue("PGDATABASE", "POSTGRES_DB")
        ?: "postgres"
    val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
    return DatabaseEnvironment(
        jdbcUrl = "jdbc:postgresql://$host:$port/$database$query",
        user = user,
        password = password,
        source = source
    )
}

/**
 * URLDecoder interpreta '+' como espacio porque fue diseñado para formularios HTML.
 * En una credencial PostgreSQL, '+' es un carácter válido y debe conservarse.
 * Los proveedores administrados pueden generar claves que lo contengan, por lo que primero lo protegemos como %2B.
 */
private fun decodeUriComponent(value: String): String =
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)

private fun resolvePublicBaseUrl(): String {
    fun normalize(value: String): String {
        var clean = value.trim().trimEnd('/')
        if (clean.isBlank()) return "http://localhost:8080"
        if (!clean.startsWith("http://", true) && !clean.startsWith("https://", true)) clean = "https://$clean"
        if (clean.startsWith("http://", true)) {
            val host = runCatching { URI(clean).host.orEmpty().lowercase() }.getOrDefault("")
            val local = host == "localhost" || host == "127.0.0.1" || host.startsWith("10.") ||
                host.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(host)
            if (!local) clean = "https://" + clean.substringAfter("://")
        }
        return clean
    }
    firstEnvironmentValue("PUBLIC_BASE_URL", "CREDICASH_PUBLIC_BASE_URL")
        ?.let { return normalize(it) }
    firstEnvironmentValue("RAILWAY_PUBLIC_DOMAIN")
        ?.let { return normalize(it) }
    System.err.println(
        "Credicash: PUBLIC_BASE_URL no está definido y no existe RAILWAY_PUBLIC_DOMAIN. " +
            "Configura el dominio público para generar URLs de archivos correctamente."
    )
    return "http://localhost:8080"
}


private fun bootstrapEnv(name: String, fallback: String): String {
    val value = env(name, fallback).trim()
    return when {
        value.length >= 2 && value.startsWith('"') && value.endsWith('"') -> value.substring(1, value.length - 1).trim()
        value.length >= 2 && value.startsWith('\'') && value.endsWith('\'') -> value.substring(1, value.length - 1).trim()
        else -> value
    }
}

private fun env(name: String, fallback: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback

private fun envInt(name: String, fallback: Int, minimum: Int, maximum: Int): Int =
    optionalEnv(name)?.toIntOrNull()?.coerceIn(minimum, maximum) ?: fallback.coerceIn(minimum, maximum)

private fun envLong(name: String, fallback: Long, minimum: Long, maximum: Long): Long =
    optionalEnv(name)?.toLongOrNull()?.coerceIn(minimum, maximum) ?: fallback.coerceIn(minimum, maximum)

private fun envBoolean(name: String, fallback: Boolean): Boolean =
    optionalEnv(name)?.let { value ->
        when (value.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> fallback
        }
    } ?: fallback

private fun optionalEnv(name: String): String? =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

private fun firstEnvironmentValue(vararg names: String): String? =
    names.asSequence()
        .mapNotNull(::optionalEnv)
        .map(String::stripWrappingQuotes)
        .firstOrNull(String::isNotBlank)

private fun String.stripWrappingQuotes(): String {
    val value = trim()
    return when {
        value.length >= 2 && value.startsWith('"') && value.endsWith('"') -> value.substring(1, value.length - 1).trim()
        value.length >= 2 && value.startsWith('\'') && value.endsWith('\'') -> value.substring(1, value.length - 1).trim()
        else -> value
    }
}

private fun isProductionEnvironment(): Boolean {
    if (!optionalEnv("RAILWAY_ENVIRONMENT").isNullOrBlank()) return true
    return firstEnvironmentValue("APP_ENV", "ENVIRONMENT")
        ?.lowercase() in setOf("production", "prod")
}

private fun requiredEnv(name: String): String =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Falta la variable de entorno obligatoria $name.")
