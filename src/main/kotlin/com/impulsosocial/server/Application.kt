package com.impulsosocial.server

import com.auth0.jwt.JWT
import com.impulsosocial.server.config.AppConfig
import com.impulsosocial.server.db.Database
import com.impulsosocial.server.export.ExcelExporter
import com.impulsosocial.server.integrations.BcvRateService
import com.impulsosocial.server.integrations.PushNotificationService
import com.impulsosocial.server.integrations.TelegramService
import com.impulsosocial.server.integrations.RecaptchaService
import com.impulsosocial.server.model.*
import com.impulsosocial.server.security.JwtService
import com.impulsosocial.server.security.PasswordSecurity
import com.impulsosocial.server.service.AppException
import com.impulsosocial.server.service.AppService
import com.impulsosocial.server.service.ForbiddenException
import com.impulsosocial.server.service.NotFoundException
import com.impulsosocial.server.service.UploadPolicy
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.io.File
import java.nio.file.StandardCopyOption
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val CREDICASH_APP_VERSION = "7.2.9"

private enum class StartupPhase {
    STARTING,
    MIGRATING,
    READY,
    DEGRADED
}

private val startupPhase = AtomicReference(StartupPhase.STARTING)
@Volatile private var startupLastError: String? = null

fun Application.module() {
    val config = AppConfig()
    val database = Database(config)
    val security = PasswordSecurity()
    val jwt = JwtService(config)
    val pushNotifications = PushNotificationService(config)
    val bcvRateService = BcvRateService()
    val telegramService = TelegramService(config)
    // 7.2.9: el módulo se conserva para una futura reactivación, pero no participa
    // en registro, inicio de sesión, recuperación ni revisión de identidad.
    val telegramValidationEnabled = false
    val recaptchaService = RecaptchaService(config)
    val service = AppService(
        database = database,
        config = config,
        passwordSecurity = security,
        pushNotifications = pushNotifications,
        bcvRateService = bcvRateService,
        telegramService = telegramService,
        recaptchaService = recaptchaService
    )
    val exporter = ExcelExporter(database)
    val appLogger = environment.log
    val primaryUploadRoot = File(config.uploadDir).absoluteFile.apply { mkdirs() }
    val uploadRoots = discoverUploadRoots(primaryUploadRoot)
    migrateLegacyUploads(primaryUploadRoot, uploadRoots.drop(1), appLogger)

    // Railway necesita que el proceso abra el puerto de inmediato. La preparación de
    // PostgreSQL se ejecuta en segundo plano, con reintentos, sin bloquear Ktor.
    val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Los canales LISTEN se inician solamente después de confirmar PostgreSQL y las
    // migraciones. Antes se lanzaban al mismo tiempo que Hikari y, ante una credencial
    // rotada durante un redeploy, producían varios errores de autenticación por segundo.
    val ledgerRealtimeNotifier = LedgerRealtimeNotifier(config, appLogger)
    val operationalRealtimeNotifier = OperationalRealtimeNotifier(config, appLogger)

    appLogger.info("Credicash: configuración PostgreSQL resuelta desde {}.", config.dbConfigurationSource)

    // Revisión automática de mora y niveles. No depende de que el usuario abra la app:
    // cada hora se actualizan cuotas vencidas, penalizaciones de nivel y cierres por mora grave.
    startupScope.launch {
        while (isActive) {
            if (startupPhase.get() == StartupPhase.READY) {
                runCatching { service.refreshAllCreditHistories() }
                    .onFailure { error -> appLogger.warn("No fue posible ejecutar el mantenimiento horario de Crédito Credicash.", error) }
            }
            delay(60L * 60L * 1000L)
        }
    }

    if (config.usesGeneratedJwtSecret) {
        appLogger.warn(
            "JWT_SECRET no está definido. Se generó una clave segura temporal; las sesiones se renovarán cuando el contenedor reinicie. Define JWT_SECRET en Railway para mantenerlas entre despliegues."
        )
    }

    if (pushNotifications.enabled) {
        appLogger.info("Firebase Cloud Messaging: CONFIGURADO")
    } else {
        appLogger.warn("Firebase Cloud Messaging: NO CONFIGURADO. En nube define FIREBASE_SERVICE_ACCOUNT_BASE64 o FIREBASE_SERVICE_ACCOUNT_JSON; en Windows puedes seguir usando la ruta al archivo JSON")
    }
    if (telegramValidationEnabled && telegramService.enabled) {
        appLogger.info("Bot de Telegram Credicash: CONFIGURADO · @{}", config.telegramBotUsername)
        startupScope.launch {
            runCatching { telegramService.configureWebhook() }
                .onFailure { error -> appLogger.error("No fue posible configurar el webhook de Telegram.", error) }
        }
    } else {
        appLogger.info("Bot de Telegram Credicash: DESACTIVADO para la versión {}.", CREDICASH_APP_VERSION)
    }
    if (recaptchaService.configured) {
        appLogger.info("reCAPTCHA Enterprise: CONFIGURADO · score mínimo {}", config.recaptchaMinScore)
    } else if (config.recaptchaRequired) {
        appLogger.warn("reCAPTCHA Enterprise: REQUERIDO PERO NO CONFIGURADO. Los flujos sensibles serán rechazados hasta definir sus variables.")
    } else {
        appLogger.warn("reCAPTCHA Enterprise: desactivado mediante RECAPTCHA_REQUIRED=false.")
    }

    environment.monitor.subscribe(ApplicationStopped) {
        appLogger.info("Credicash {}: apagado solicitado; cerrando recursos.", CREDICASH_APP_VERSION)
        ledgerRealtimeNotifier.stop()
        operationalRealtimeNotifier.stop()
        startupScope.cancel()
        service.close()
        database.close()
        appLogger.info("Credicash {}: apagado limpio completado.", CREDICASH_APP_VERSION)
    }

    install(CallLogging) {
        filter { call ->
            when (call.request.path()) {
                "/health", "/health/live", "/health/ready" -> false
                else -> true
            }
        }
    }
    install(ContentNegotiation) { gson() }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Registration-Token")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val message = cause.message ?: "Solicitud inválida."
            val alreadyProcessed = listOf(
                "ya fue procesada", "ya fue procesado", "ya fue revisada", "ya fue revisado",
                "ya se encuentra", "ya está", "ya esta"
            ).any { message.contains(it, ignoreCase = true) }
            if (alreadyProcessed) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(message, "ALREADY_PROCESSED", false))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(message, "VALIDATION_ERROR"))
            }
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Acceso denegado.", "FORBIDDEN"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "No encontrado.", "NOT_FOUND"))
        }
        exception<SQLException> { call, cause ->
            val requestId = UUID.randomUUID().toString().substring(0, 8).uppercase()
            appLogger.error("Error PostgreSQL controlado [{}] SQLSTATE={}", requestId, cause.sqlState, cause)
            val (message, code, retryable) = when (cause.sqlState) {
                "23503" -> Triple(
                    "No se puede completar esta eliminación porque existen registros históricos relacionados que deben conservarse. Referencia: $requestId",
                    "PROTECTED_HISTORY",
                    false
                )
                "23514" -> Triple(
                    "La operación fue detenida por una regla de integridad de datos. Actualiza la pantalla e inténtalo nuevamente. Referencia: $requestId",
                    "DATA_INTEGRITY_RULE",
                    false
                )
                "42703", "42P01" -> Triple(
                    "El servicio está aplicando una actualización. Espera unos segundos e intenta nuevamente. Referencia: $requestId",
                    "SCHEMA_UPDATING",
                    true
                )
                "23505" -> Triple(
                    "La operación ya fue registrada. Actualiza la pantalla antes de intentarlo nuevamente. Referencia: $requestId",
                    "DUPLICATE_OPERATION",
                    false
                )
                "40001", "40P01" -> Triple(
                    "La operación coincidió con otra actualización. Intenta nuevamente. Referencia: $requestId",
                    "CONCURRENT_UPDATE",
                    true
                )
                else -> Triple(
                    "No fue posible completar la operación. Referencia: $requestId",
                    "DATABASE_ERROR",
                    true
                )
            }
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(message, code, retryable, requestId)
            )
        }
        exception<Throwable> { call, cause ->
            val requestId = UUID.randomUUID().toString().substring(0, 8).uppercase()
            appLogger.error("Error no controlado [{}]", requestId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    "Ocurrió un error interno. Referencia: $requestId",
                    "INTERNAL_ERROR",
                    false,
                    requestId
                )
            )
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "credicash"
            verifier(
                JWT.require(jwt.algorithm)
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asLong()
                val sessionId = credential.payload.getClaim("sessionId").asString()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (userId != null && sessionId != null && service.sessionRole(userId, sessionId) != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                val bearer = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotBlank() }
                val suspended = bearer?.let { token ->
                    runCatching { JWT.decode(token).getClaim("userId").asLong() }.getOrNull()
                }?.let(service::accountSuspensionState)
                if (suspended != null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Tu cuenta está suspendida.", "ACCOUNT_SUSPENDED", false)
                    )
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Tu sesión venció. Inicia sesión nuevamente."))
                }
            }
        }
    }

    routing {
        // Liveness: confirma que el proceso Ktor sigue vivo, aunque PostgreSQL aún esté migrando.
        get("/health/live") {
            call.respondText("ALIVE", ContentType.Text.Plain, HttpStatusCode.OK)
        }

        // Endpoint de disponibilidad: Railway debe enviar tráfico cuando Ktor y PostgreSQL estén listos.
        get("/health") {
            // Railway solo debe considerar lista una instancia cuyo esquema ya esté listo.
            // Ktor abre el puerto inmediatamente, pero durante la migración se responde 503.
            val ready = startupPhase.get() == StartupPhase.READY && database.isHealthy()
            call.respondText(
                if (ready) "OK" else "STARTING",
                ContentType.Text.Plain,
                if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            )
        }
        get("/health/ready") {
            val ready = startupPhase.get() == StartupPhase.READY && database.isHealthy()
            call.respondText(
                if (ready) "READY" else "NOT_READY",
                ContentType.Text.Plain,
                if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            )
        }
        get("/") {
            call.respond(
                mapOf(
                    "service" to "Credicash",
                    "status" to "running",
                    "version" to CREDICASH_APP_VERSION,
                    "telegram" to if (telegramValidationEnabled && telegramService.enabled) "configured" else "disabled"
                )
            )
        }
        get("/api/v1/health") {
            val phase = startupPhase.get()
            val databaseStatus = when (phase) {
                StartupPhase.READY -> if (database.isHealthy()) "connected" else "reconnecting"
                StartupPhase.MIGRATING -> "migrating"
                StartupPhase.DEGRADED -> "retrying"
                StartupPhase.STARTING -> "starting"
                else -> "starting"
            }
            call.respond(
                HealthResponse(
                    status = if (phase == StartupPhase.READY) "ok" else "starting",
                    database = databaseStatus,
                    version = CREDICASH_APP_VERSION,
                    telegram = if (telegramValidationEnabled && telegramService.enabled) "configured" else "disabled",
                    push = if (pushNotifications.enabled) "configured" else "unavailable",
                    recaptcha = when {
                        recaptchaService.configured -> "configured"
                        config.recaptchaRequired -> "required_but_unavailable"
                        else -> "disabled"
                    },
                    bcv = bcvRateService.cacheStatus(),
                    schema = phase.name.lowercase(),
                    detail = if (phase == StartupPhase.DEGRADED) "migration_retrying" else null
                )
            )
        }
        get("/api/v1/health/services") {
            val databaseOk = database.isHealthy()
            val phase = startupPhase.get()
            call.respond(
                mapOf(
                    "version" to CREDICASH_APP_VERSION,
                    "database" to if (databaseOk) "connected" else "unavailable",
                    "schema" to phase.name.lowercase(),
                    "push" to if (pushNotifications.enabled) "configured" else "unavailable",
                    "telegram" to if (telegramValidationEnabled && telegramService.enabled) "configured" else "disabled",
                    "recaptcha" to when {
                        recaptchaService.configured -> "configured"
                        config.recaptchaRequired -> "required_but_unavailable"
                        else -> "disabled"
                    },
                    // No fuerza llamadas externas desde un endpoint público. La disponibilidad
                    // real se comprueba en /exchange-rate/bcv, que usa caché y límites de tiempo.
                    "bcv" to bcvRateService.cacheStatus(),
                    "migration" to if (phase == StartupPhase.DEGRADED) "retrying" else "ok"
                )
            )
        }

        get("/uploads/{path...}") {
            val relativePath = call.parameters.getAll("path")
                ?.joinToString("/")
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?: throw NotFoundException("Archivo no encontrado.")
            if (!service.canReadUpload(
                    relativePath,
                    call.request.queryParameters["expires"],
                    call.request.queryParameters["signature"]
                )
            ) {
                throw ForbiddenException("El enlace del archivo no es válido o ya venció.")
            }
            val file = resolveUploadFile(uploadRoots, relativePath)
                ?: throw NotFoundException("Archivo no encontrado.")
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header(
                HttpHeaders.CacheControl,
                if (service.isPrivateUpload(relativePath)) "private, no-store" else "public, max-age=3600"
            )
            call.respondFile(file)
        }

        get("/api/v1/version-policy") {
            call.respond(service.versionPolicy())
        }

        get("/api/v1/exchange-rate/bcv") {
            val rate = withContext(Dispatchers.IO) {
                runCatching { bcvRateService.currentUsdRate() }
                    .getOrElse { throw AppException("La tasa BCV no está disponible temporalmente.") }
            }
            call.respond(ExchangeRateDto(rate = rate.rate, date = rate.date, source = rate.source))
        }

        get("/api/v1/locations/comunidades") {
            call.respond(
                service.communityCatalog(
                    call.request.queryParameters["state"],
                    call.request.queryParameters["municipality"],
                    call.request.queryParameters["parish"]
                )
            )
        }
        get("/api/v1/locations/communities") {
            call.respond(
                service.communityCatalog(
                    call.request.queryParameters["state"],
                    call.request.queryParameters["municipality"],
                    call.request.queryParameters["parish"]
                )
            )
        }


        /**
         * Feed público, sanitizado y de solo lectura para el visor de trazabilidad.
         * No expone nombres, correos, cédulas, teléfonos, tokens ni IDs internos de usuario.
         */
        get("/api/v1/explorer/transactions") {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50
            call.respond(
                service.publicLedgerTransactions(
                    requestedPage = page,
                    requestedPageSize = pageSize,
                    search = call.request.queryParameters["search"],
                    operationType = call.request.queryParameters["type"],
                    status = call.request.queryParameters["status"],
                    wallet = call.request.queryParameters["wallet"],
                    fromDate = call.request.queryParameters["from"],
                    toDate = call.request.queryParameters["to"],
                    sort = call.request.queryParameters["sort"]
                )
            )
        }

        /** Consulta pública de una operación concreta para enlaces compartibles. */
        get("/api/v1/explorer/transactions/{transactionId}") {
            val transactionId = call.parameters["transactionId"]
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.length <= 160 }
                ?: throw NotFoundException("No se encontró la operación solicitada.")
            val page = service.publicLedgerTransactions(
                requestedPage = 1,
                requestedPageSize = 50,
                search = transactionId,
                operationType = null,
                status = null,
                wallet = null,
                fromDate = null,
                toDate = null,
                sort = "NEWEST"
            )
            val transaction = page.transactions.firstOrNull { it.transactionId == transactionId }
                ?: throw NotFoundException("No se encontró la operación solicitada.")
            call.respond(transaction)
        }

        /**
         * Canal SSE del visor. PostgreSQL emite NOTIFY cuando cambia cualquiera de
         * las tablas que alimentan el libro público. El navegador actualiza la
         * página sin esperar un intervalo fijo de sondeo.
         */
        get("/api/v1/explorer/events") {
            call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
            call.response.header(HttpHeaders.Connection, "keep-alive")
            call.response.header("X-Accel-Buffering", "no")
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                write("retry: 1500\n")
                write("event: connected\n")
                write("data: {\"status\":\"connected\",\"mode\":\"realtime\"}\n\n")
                flush()

                val subscription = ledgerRealtimeNotifier.subscribe()
                try {
                    while (coroutineContext.isActive) {
                        val payload = withTimeoutOrNull(15_000) { subscription.receive() }
                        if (payload == null) {
                            write(": heartbeat\n\n")
                        } else {
                            write("event: ledger_changed\n")
                            write("data: $payload\n\n")
                        }
                        flush()
                    }
                } finally {
                    ledgerRealtimeNotifier.unsubscribe(subscription)
                }
            }
        }

        post("/api/v1/integrations/telegram/webhook") {
            // Se conserva la ruta para una futura reactivación, pero 7.2.9 no procesa
            // vinculaciones, códigos ni validaciones del bot.
            call.respond(HttpStatusCode.Gone, mapOf("ok" to false, "status" to "disabled"))
        }

        route("/api/v1/auth") {
            post("/register") { call.respond(HttpStatusCode.Created, service.register(call.receive())) }
            post("/login") { call.respond(service.login(call.receive<LoginRequest>()).response) }

            // Credicash 7.2.9: Telegram se conserva en código para una futura reactivación,
            // pero queda completamente fuera del flujo activo de registro/validación.
            post("/verify-telegram") { throw AppException("La validación por Telegram está desactivada en esta versión.") }
            post("/telegram/verify") { throw AppException("La validación por Telegram está desactivada en esta versión.") }
            post("/resend-telegram-verification") { throw AppException("La validación por Telegram está desactivada en esta versión.") }
            post("/telegram/resend") { throw AppException("La validación por Telegram está desactivada en esta versión.") }
            post("/telegram/link/status") { throw AppException("La validación por Telegram está desactivada en esta versión.") }
            post("/telegram/status") { throw AppException("La validación por Telegram está desactivada en esta versión.") }

            // Recuperación automática temporalmente fuera de servicio para no depender del bot.
            // El restablecimiento se gestiona administrativamente en esta actualización.
            post("/forgot-password") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password/forgot") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/request-password-reset") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password-recovery/request") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/recover-password") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password/request-reset") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }

            post("/reset-password") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password/reset") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password-recovery/reset") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }
            post("/password/confirm-reset") { throw AppException("La recuperación automática está desactivada. Solicita a un Administrador el restablecimiento de tu acceso.") }

            post("/verify-pin") {
                val response = service.verifyPin(call.receive()) { userId, role, sessionId -> jwt.createAccessToken(userId, role, sessionId) }
                call.respond(response)
            }
            post("/saved-session/pin-challenge") {
                call.respond(service.createSavedSessionPinChallenge(call.receive()))
            }
            post("/refresh") {
                val response = service.refreshSession(call.receive()) { userId, role, sessionId -> jwt.createAccessToken(userId, role, sessionId) }
                call.respond(response)
            }
            post("/logout") {
                service.revokePersistentSession(call.receive())
                call.respond(mapOf("message" to "Sesión cerrada"))
            }
        }

        post("/api/v1/usuarios/{id}/document-verification") {
            handleRegistrationDocumentVerification(call, service)
        }

        post("/api/v1/users/{id}/document-verification") {
            handleRegistrationDocumentVerification(call, service)
        }

        // Cambios 32: los tokens push solo se registran dentro de una sesión autenticada.

        authenticate("auth-jwt") {
            route("/api/v1") {
                get("/me") { call.respond(service.me(call.userId())) }
                get("/me/events") {
                    call.userId() // fuerza la validación de la sesión antes de abrir el stream privado
                    call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
                    call.response.header(HttpHeaders.Connection, "keep-alive")
                    call.response.header("X-Accel-Buffering", "no")
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        write("retry: 1500\n")
                        write("event: connected\n")
                        write("data: {\"status\":\"connected\",\"mode\":\"realtime\"}\n\n")
                        flush()

                        val subscription = operationalRealtimeNotifier.subscribe()
                        try {
                            while (coroutineContext.isActive) {
                                val payload = withTimeoutOrNull(15_000) { subscription.receive() }
                                if (payload == null) {
                                    write(": heartbeat\n\n")
                                } else {
                                    write("event: changed\n")
                                    write("data: $payload\n\n")
                                }
                                flush()
                            }
                        } finally {
                            operationalRealtimeNotifier.unsubscribe(subscription)
                        }
                    }
                }
                post("/me/session/heartbeat") {
                    service.heartbeatSession(call.userId(), call.sessionId())
                    call.respond(MessageResponse("Sesión activa."))
                }
                post("/me/telegram-link") { throw AppException("La vinculación por Telegram está desactivada en esta versión.") }
                get("/me/telegram-status") { throw AppException("La vinculación por Telegram está desactivada en esta versión.") }
                post("/me/telegram-status") { throw AppException("La vinculación por Telegram está desactivada en esta versión.") }
                post("/me/persistent-session") {
                    service.heartbeatSession(call.userId(), call.sessionId())
                    call.respond(PersistentSessionResponse(call.sessionId().toString()))
                }
                post("/me/device-tokens") {
                    service.registerDeviceToken(call.userId(), call.receive())
                    call.respond(MessageResponse("Dispositivo registrado."))
                }
                delete("/me/device-tokens") {
                    service.unregisterDeviceToken(call.userId(), call.receive())
                    call.respond(MessageResponse("Dispositivo desvinculado de notificaciones."))
                }
                post("/me/biometric-credentials") {
                    call.respond(HttpStatusCode.Created, service.registerBiometricCredential(call.userId(), call.receive()))
                }
                delete("/me/biometric-credentials") {
                    service.disableBiometricCredential(call.userId(), call.receive())
                    call.respond(MessageResponse("Protección biométrica desactivada."))
                }
                get("/me/notificaciones") { call.respond(service.notificaciones(call.userId())) }
                get("/me/notifications") { call.respond(service.notificaciones(call.userId())) }
                delete("/me/notifications") {
                    service.clearNotifications(call.userId())
                    call.respond(MessageResponse("Notificaciones revisadas."))
                }
                get("/me/credit-disbursement-bank") {
                    call.respond(
                        service.creditDisbursementBank(call.userId())
                            ?: CreditDisbursementBankDto("", "", "", "", "", "")
                    )
                }
                put("/me/credit-disbursement-bank") {
                    call.respond(service.saveCreditDisbursementBank(call.userId(), call.receive()))
                }
                get("/banks") { call.respond(service.banks()) }
                get("/productos") { call.respond(service.productos()) }
                get("/products") { call.respond(service.productos()) }
                get("/jornadas") {
                    val userId = call.userId()
                    call.respond(service.jornadas(call.request.queryParameters["includeUnpublished"].toBoolean(), service.currentRole(userId)))
                }
                get("/fairs") {
                    val userId = call.userId()
                    call.respond(service.jornadas(call.request.queryParameters["includeUnpublished"].toBoolean(), service.currentRole(userId)))
                }
                get("/comunidades") { call.respond(service.comunidades()) }
                get("/communities") { call.respond(service.comunidades()) }
                get("/combos") { call.respond(service.combos()) }
                get("/me/purchases") { call.respond(service.purchases(call.userId())) }
                get("/me/role-experience") { call.respond(service.roleExperience(call.userId())) }
                get("/me/credit") { call.respond(service.creditSummary(call.userId())) }
                get("/me/credimpulso-transactions") { call.respond(service.credimpulsoTransactions(call.userId())) }

                post("/me/credit-requests") {
                    call.respond(HttpStatusCode.Created, service.createCreditRequest(call.userId(), call.receive()))
                }
                get("/me/credit-requests") { call.respond(service.creditRequests(call.userId())) }

                post("/payment-proofs") {
                    val userId = call.userId()
                    var storedPath: String? = null
                    call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                        try {
                            if (part is PartData.FileItem && part.name == "proof") {
                                val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                if (bytes.isEmpty()) throw AppException("El comprobante está vacío.")
                                if (bytes.size > 10 * 1024 * 1024) throw AppException("La captura del comprobante debe pesar menos de 10 MB.")
                                storedPath = service.storeUpload("payment-proofs", userId.toString(), part.originalFileName ?: "comprobante.jpg", bytes).relativePath
                            }
                        } finally {
                            part.dispose()
                        }
                    }
                    call.respond(HttpStatusCode.Created, PaymentProofUploadResponse(storedPath ?: throw AppException("Selecciona la captura del comprobante.")))
                }
                post("/me/payment-reports") {
                    call.respond(HttpStatusCode.Created, service.createUserPaymentReport(call.userId(), call.receive()))
                }
                post("/me/payment-reports/with-proof") {
                    val userId = call.userId()
                    val fields = mutableMapOf<String, String>()
                    var storedPath: String? = null
                    var storedFile: File? = null
                    try {
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                                    is PartData.FileItem -> if (part.name == "proof") {
                                        val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                        if (bytes.isEmpty()) throw AppException("El comprobante está vacío.")
                                        if (bytes.size > 10 * 1024 * 1024) throw AppException("La captura del comprobante debe pesar menos de 10 MB.")
                                        val stored = service.storeUpload(
                                            "payment-proofs",
                                            userId.toString(),
                                            part.originalFileName ?: "comprobante.jpg",
                                            bytes
                                        )
                                        storedPath = stored.relativePath
                                        storedFile = stored.absoluteFile
                                    }
                                    else -> Unit
                                }
                            } finally {
                                part.dispose()
                            }
                        }
                        val proofPath = storedPath ?: throw AppException("Selecciona la captura del comprobante.")
                        val request = CreateUserPaymentReportRequest(
                            targetType = fields["targetType"].orEmpty(),
                            orderId = fields["orderId"]?.toLongOrNull(),
                            installmentId = fields["installmentId"]?.toLongOrNull(),
                            method = fields["method"].orEmpty(),
                            originBankCode = fields["originBankCode"].orEmpty(),
                            originPhone = fields["originPhone"].orEmpty(),
                            referenceNumber = fields["referenceNumber"].orEmpty(),
                            amountBs = fields["amountBs"]?.toDoubleOrNull()
                                ?: throw AppException("El monto reportado no es válido."),
                            paidFromDifferentPhone = fields["paidFromDifferentPhone"]?.toBooleanStrictOrNull() ?: false,
                            proofPath = proofPath,
                            notes = fields["notes"]
                        )
                        call.respond(HttpStatusCode.Created, service.createUserPaymentReport(userId, request))
                    } catch (error: Throwable) {
                        runCatching { storedFile?.delete() }
                        throw error
                    }
                }
                get("/me/payment-reports") {
                    call.respond(service.userPaymentReports(call.userId()))
                }
                post("/purchases/with-proof") {
                    val userId = call.userId()
                    val fields = mutableMapOf<String, String>()
                    var storedPath: String? = null
                    var storedFile: File? = null
                    fun parseItems(raw: String?, label: String): List<PurchaseItemRequest> = raw.orEmpty()
                        .split(',')
                        .mapNotNull { token ->
                            val clean = token.trim()
                            if (clean.isBlank()) return@mapNotNull null
                            val parts = clean.split(':', limit = 2)
                            val id = parts.getOrNull(0)?.toLongOrNull()
                                ?: throw AppException("El identificador de $label no es válido.")
                            val quantity = parts.getOrNull(1)?.toIntOrNull()
                                ?: throw AppException("La cantidad de $label no es válida.")
                            PurchaseItemRequest(id, quantity)
                        }
                    fun parseCombos(raw: String?): List<PurchaseComboRequest> = raw.orEmpty()
                        .split(',')
                        .mapNotNull { token ->
                            val clean = token.trim()
                            if (clean.isBlank()) return@mapNotNull null
                            val parts = clean.split(':', limit = 2)
                            val id = parts.getOrNull(0)?.toLongOrNull()
                                ?: throw AppException("El identificador del combo no es válido.")
                            val quantity = parts.getOrNull(1)?.toIntOrNull()
                                ?: throw AppException("La cantidad del combo no es válida.")
                            PurchaseComboRequest(id, quantity)
                        }
                    try {
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                                    is PartData.FileItem -> if (part.name == "proof") {
                                        val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                        if (bytes.isEmpty()) throw AppException("El comprobante está vacío.")
                                        if (bytes.size > 10 * 1024 * 1024) throw AppException("La captura del comprobante debe pesar menos de 10 MB.")
                                        val stored = service.storeUpload(
                                            "payment-proofs",
                                            userId.toString(),
                                            part.originalFileName ?: "comprobante.jpg",
                                            bytes
                                        )
                                        storedPath = stored.relativePath
                                        storedFile = stored.absoluteFile
                                    }
                                    else -> Unit
                                }
                            } finally {
                                part.dispose()
                            }
                        }
                        val request = CreatePurchaseRequest(
                            fairId = fields["fairId"]?.toLongOrNull()
                                ?: throw AppException("La jornada seleccionada no es válida."),
                            items = parseItems(fields["items"], "producto"),
                            comboItems = parseCombos(fields["comboItems"]),
                            paymentMethod = fields["paymentMethod"].orEmpty(),
                            paymentReference = fields["paymentReference"].orEmpty(),
                            originBankCode = fields["originBankCode"].orEmpty(),
                            originPhone = fields["originPhone"].orEmpty(),
                            paidFromDifferentPhone = fields["paidFromDifferentPhone"]?.toBooleanStrictOrNull() ?: false,
                            proofPath = storedPath ?: throw AppException("Selecciona la captura del comprobante.")
                        )
                        call.respond(HttpStatusCode.Created, service.createPurchase(userId, request))
                    } catch (error: Throwable) {
                        runCatching { storedFile?.delete() }
                        throw error
                    }
                }
                post("/purchases") { call.respond(HttpStatusCode.Created, service.createPurchase(call.userId(), call.receive())) }
                get("/purchases/{id}/invoice") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Pedido inválido.")
                    call.respond(service.invoice(call.userId(), id))
                }
                post("/community-requests") { call.respond(HttpStatusCode.Created, service.createCommunityRequest(call.userId(), call.receive())) }

                get("/businesses") {
                    val role = service.currentRole(call.userId())
                    if (role !in setOf("ADMIN", "ACCOUNTANT")) throw ForbiddenException()
                    call.respond(service.associatedBusinesses(activeOnly = true))
                }

                route("/accountant") {
                    post("/staff") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        val fields = linkedMapOf<String, String>()
                        val storedFiles = mutableListOf<File>()
                        val uploadOwner = "${call.userId()}-${UUID.randomUUID()}"
                        var frontPath: String? = null
                        var backPath: String? = null
                        var selfiePath: String? = null
                        var accountCreated = false
                        try {
                            call.receiveMultipart(formFieldLimit = 40 * 1024 * 1024).forEachPart { part ->
                                try {
                                    when (part) {
                                        is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                                        is PartData.FileItem -> {
                                            val fieldName = part.name
                                            if (fieldName !in setOf("front", "back", "selfie")) return@forEachPart
                                            val alreadyReceived = when (fieldName) {
                                                "front" -> frontPath != null
                                                "back" -> backPath != null
                                                else -> selfiePath != null
                                            }
                                            if (alreadyReceived) throw AppException("Cada documento del personal debe enviarse una sola vez.")
                                            val bytes = part.provider().readRemaining(12L * 1024L * 1024L + 1L).readBytes()
                                            if (bytes.size > 12 * 1024 * 1024) throw AppException("Cada documento debe pesar menos de 12 MB.")
                                            val validated = UploadPolicy.validate("documents", bytes)
                                            if (fieldName == "selfie" && !validated.image) {
                                                throw AppException("La selfie del personal debe ser una imagen JPG, PNG o WEBP.")
                                            }
                                            val stored = service.storeUpload("staff-documents", uploadOwner, part.originalFileName ?: "documento.bin", bytes)
                                            storedFiles += stored.absoluteFile
                                            when (fieldName) {
                                                "front" -> frontPath = stored.relativePath
                                                "back" -> backPath = stored.relativePath
                                                "selfie" -> selfiePath = stored.relativePath
                                            }
                                        }
                                        else -> Unit
                                    }
                                } finally {
                                    part.dispose()
                                }
                            }
                            val request = StaffAccountCreationRequest(
                                role = fields["role"].orEmpty(),
                                firstName = fields["firstName"].orEmpty(),
                                middleName = fields["middleName"],
                                lastName = fields["lastName"].orEmpty(),
                                secondLastName = fields["secondLastName"],
                                phone = fields["phone"].orEmpty(),
                                birthDate = fields["birthDate"].orEmpty(),
                                state = fields["state"],
                                municipality = fields["municipality"],
                                parish = fields["parish"],
                                community = fields["community"],
                                address = fields["address"],
                                documentType = fields["documentType"] ?: "NATIONAL_ID",
                                documentNumber = fields["documentNumber"].orEmpty(),
                                operationalUsername = fields["operationalUsername"].orEmpty(),
                                operationalEmail = fields["operationalEmail"].orEmpty(),
                                operationalPassword = fields["operationalPassword"].orEmpty(),
                                operationalPin = fields["operationalPin"].orEmpty(),
                                adminSubRole = fields["adminSubRole"],
                                createBeneficiaryAccess = fields["createBeneficiaryAccess"]?.toBooleanStrictOrNull() ?: false,
                                beneficiaryUsername = fields["beneficiaryUsername"],
                                beneficiaryEmail = fields["beneficiaryEmail"],
                                beneficiaryPassword = fields["beneficiaryPassword"],
                                beneficiaryPin = fields["beneficiaryPin"]
                            )
                            val result = service.createStaffAccount(
                                actorId = call.userId(),
                                request = request,
                                frontPath = frontPath ?: throw AppException("Carga la cédula o documento de identidad."),
                                backPath = backPath,
                                selfiePath = selfiePath ?: throw AppException("Carga una selfie actual del personal.")
                            )
                            accountCreated = true
                            call.respond(HttpStatusCode.Created, result)
                        } catch (error: Throwable) {
                            if (!accountCreated) storedFiles.forEach { runCatching { it.delete() } }
                            throw error
                        }
                    }
                    get("/staff") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        call.respond(service.usuarios())
                    }
                    get("/staff/import-format.xlsx") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        val bytes = exporter.exportStaffImportFormat()
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment
                                .withParameter(ContentDisposition.Parameters.FileName, "Formato_Carga_Personal_Credicash.xlsx")
                                .toString()
                        )
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    post("/staff/{id}/beneficiary-access") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        call.respond(HttpStatusCode.Created, service.createLinkedBeneficiaryAccess(call.userId(), id, call.receive()))
                    }
                    post("/staff/import") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        var excelBytes: ByteArray? = null
                        var confirm = false
                        call.receiveMultipart(formFieldLimit = 12 * 1024 * 1024).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> if (part.name == "confirm") {
                                        confirm = part.value.trim().equals("true", ignoreCase = true)
                                    }
                                    is PartData.FileItem -> if (part.name == "file") {
                                        val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                        if (bytes.size > 10 * 1024 * 1024) throw AppException("El Excel debe pesar menos de 10 MB.")
                                        excelBytes = bytes
                                    }
                                    else -> Unit
                                }
                            } finally {
                                part.dispose()
                            }
                        }
                        val bytes = excelBytes ?: throw AppException("Selecciona un archivo Excel .xlsx.")
                        if (confirm) {
                            call.respond(HttpStatusCode.Created, service.importStaffExcel(call.userId(), bytes))
                        } else {
                            call.respond(service.previewStaffExcelImport(call.userId(), bytes))
                        }
                    }
                    delete("/staff/{id}/access") {
                        call.requirePermission(service, "MANAGE_STAFF_ROLES")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.removeAdministratorAccess(call.userId(), id)
                        call.respond(MessageResponse("Acceso de Administrador retirado. El registro y el historial fueron conservados."))
                    }
                    delete("/staff/{id}") {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.deleteUserPermanently(call.userId(), id)
                        call.respond(MessageResponse("Usuario eliminado definitivamente de la base de datos."))
                    }
                    get("/beneficiaries") {
                        call.requireAccountant(service)
                        call.respond(service.accountantBeneficiaries(call.userId()))
                    }
                    // Credicash 7.2.9: el Contador mantiene la cola global de Beneficiarios, aunque
                    // Administradores autorizados también puedan resolver su revisión documental.
                    get("/registration-requests") {
                        call.requireAccountantRegistrationFallback(service)
                        call.respond(service.registrationRequests())
                    }
                    get("/verifications") {
                        call.requireAccountantRegistrationFallback(service)
                        call.respond(service.pendingVerifications())
                    }

                    get("/businesses") {
                        call.requireAccountant(service)
                        call.respond(service.associatedBusinesses(activeOnly = false))
                    }
                    post("/businesses") {
                        call.requireAccountant(service)
                        call.respond(HttpStatusCode.Created, service.saveAssociatedBusiness(call.userId(), null, call.receive()))
                    }
                    put("/businesses/{id}") {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Negocio inválido.")
                        call.respond(service.saveAssociatedBusiness(call.userId(), id, call.receive()))
                    }
                    post("/businesses/{id}/status") {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Negocio inválido.")
                        call.respond(service.setAssociatedBusinessActive(call.userId(), id, call.receive<AssociatedBusinessStatusRequest>().active))
                    }
                    post("/businesses/{id}/logo") {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Negocio inválido.")
                        var logoPath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && part.name == "image") {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.isEmpty()) throw AppException("El archivo del logo está vacío.")
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("El logo debe pesar menos de 10 MB.")
                                    logoPath = service.storeUpload("business-logos", id.toString(), part.originalFileName ?: "logo.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateAssociatedBusinessLogo(call.userId(), id, logoPath ?: throw AppException("Selecciona un logo.")))
                    }
                    get("/wallet") {
                        call.requireAccountant(service)
                        call.respond(service.accountantWallet(call.userId()))
                    }
                    post("/allocations") {
                        call.requireAccountant(service)
                        call.respond(service.allocateAccountantBudget(call.userId(), call.receive()))
                    }
                    post("/budget-movements") {
                        call.requireAccountant(service)
                        call.respond(service.registerBudgetMovement(call.userId(), call.receive()))
                    }
                    get("/credit-loans") {
                        call.requireAccountant(service)
                        call.respond(service.adminCreditLoans())
                    }
                    get("/predictive") {
                        call.requireAccountant(service)
                        call.respond(service.predictiveDashboard(call.userId()))
                    }
                    get("/reconciliation") {
                        call.requireAccountant(service)
                        call.respond(service.accountantReconciliation())
                    }
                    post("/reconciliation/{id}/decision") {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Reporte de pago inválido.")
                        service.decideReconciliation(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Conciliación registrada."))
                    }
                    get("/monthly-close") {
                        call.requireAccountant(service)
                        val month = call.request.queryParameters["month"] ?: java.time.YearMonth.now().toString()
                        call.respond(service.monthlyClose(month))
                    }
                    post("/monthly-close") {
                        call.requireAccountant(service)
                        call.respond(service.closeMonthlyPeriod(call.userId(), call.receive()))
                    }
                }

                route("/accounts/{id}/suspension") {
                    post {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.suspendAccountForNonPayment(call.userId(), id, call.receive<AccountSuspensionRequest>())
                        call.respond(MessageResponse("Cuenta suspendida correctamente."))
                    }
                    delete {
                        call.requireAccountant(service)
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.reactivateSuspendedAccount(call.userId(), id)
                        call.respond(MessageResponse("Cuenta reactivada correctamente."))
                    }
                }

                route("/sensitive-approvals") {
                    get {
                        val role = service.currentRole(call.userId())
                        if (role !in setOf("ADMIN", "ACCOUNTANT")) throw ForbiddenException()
                        call.respond(service.sensitiveApprovals())
                    }
                    post {
                        val role = service.currentRole(call.userId())
                        if (role !in setOf("ADMIN", "ACCOUNTANT")) throw ForbiddenException()
                        call.respond(HttpStatusCode.Created, service.createSensitiveApproval(call.userId(), call.receive()))
                    }
                    post("/{id}/decision") {
                        call.requirePermission(service, "APPROVE_SENSITIVE_ACTIONS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Solicitud inválida.")
                        service.decideSensitiveApproval(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Decisión registrada."))
                    }
                }

                route("/admin") {
                    get("/usuarios") { call.requirePermission(service, "VIEW_USERS"); call.respond(service.usuarios()) }
                    get("/beneficiaries") {
                        call.respond(service.adminImportedBeneficiaries(call.userId()))
                    }
                    get("/beneficiaries/import-format.xlsx") {
                        service.adminImportedBeneficiaries(call.userId()) // valida rol ADMIN
                        val bytes = exporter.exportBeneficiaryImportFormat()
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Formato_Carga_Beneficiarios_Credicash.xlsx").toString()
                        )
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    post("/beneficiaries/import") {
                        var excelBytes: ByteArray? = null
                        var confirm = false
                        call.receiveMultipart(formFieldLimit = 12 * 1024 * 1024).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> if (part.name == "confirm") confirm = part.value.trim().equals("true", ignoreCase = true)
                                    is PartData.FileItem -> if (part.name == "file") {
                                        val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                        if (bytes.size > 10 * 1024 * 1024) throw AppException("El Excel debe pesar menos de 10 MB.")
                                        excelBytes = bytes
                                    }
                                    else -> Unit
                                }
                            } finally { part.dispose() }
                        }
                        val bytes = excelBytes ?: throw AppException("Selecciona un archivo Excel .xlsx.")
                        if (confirm) call.respond(HttpStatusCode.Created, service.importBeneficiaryExcel(call.userId(), bytes))
                        else call.respond(service.previewBeneficiaryExcelImport(call.userId(), bytes))
                    }
                    get("/usuarios/{id}/expediente") {
                        call.requirePermission(service, "VIEW_USERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        call.respond(service.adminUserDossier(id))
                    }
                    put("/usuarios/{id}/subrole") {
                        call.requirePermission(service, "MANAGE_ADMIN_ROLES")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        call.respond(service.updateAdminSubrole(call.userId(), id, call.receive()))
                    }
                    get("/purchases") { call.requirePermission(service, "VIEW_ORDERS"); call.respond(service.adminPurchases()) }
                    patch("/purchases/{id}/warehouse-status") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Pedido inválido.")
                        call.respond(service.updateWarehouseOrderStatus(call.userId(), id, call.receive()))
                    }
                    get("/inventory-demand") { call.requireAnyPermission(service, "VIEW_INVENTORY", "MANAGE_INVENTORY"); call.respond(service.inventoryDemand()) }
                    get("/credit-loans") { call.requireAnyPermission(service, "REVIEW_CREDITS", "VIEW_FINANCIALS"); call.respond(service.adminCreditLoans()) }

                    get("/credit-requests") { call.requirePermission(service, "REVIEW_CREDITS"); call.respond(service.adminCreditRequests()) }
                    post("/credit-requests/{id}/decision") {
                        call.requirePermission(service, "REVIEW_CREDITS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Solicitud de crédito inválida.")
                        call.respond(service.decideCreditRequest(call.userId(), id, call.receive()))
                    }

                    get("/credimpulso-transactions") { call.requireAnyPermission(service, "REVIEW_CREDITS", "VIEW_FINANCIALS"); call.respond(service.adminCredimpulsoTransactions()) }
                    get("/credimpulso-wallet") {
                        call.requireAnyPermission(service, "MANAGE_CREDIT_WALLET", "VIEW_FINANCIALS")
                        call.respond(service.adminCredimpulsoWallet(call.userId()))
                    }
                    post("/credimpulso-wallet/funds") {
                        call.requirePermission(service, "MANAGE_CREDIT_WALLET")
                        call.respond(service.addAdminWalletFunds(call.userId(), call.receive()))
                    }
                    post("/credimpulso-wallet/transfers") {
                        call.requirePermission(service, "MANAGE_CREDIT_WALLET")
                        call.respond(service.transferFromAdminWallet(call.userId(), call.receive()))
                    }
                    post("/credit-installments/{id}/pay") {
                        call.requirePermission(service, "REVIEW_PAYMENTS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Cuota de crédito inválida.")
                        service.markCreditInstallmentPaid(call.userId(), id)
                        call.respond(MessageResponse("Cuota Crédito Credicash registrada como pagada."))
                    }
                    get("/registration-requests") { call.requirePermission(service, "REVIEW_USERS"); call.respond(service.registrationRequests()) }
                    get("/verifications") { call.requirePermission(service, "REVIEW_USERS"); call.respond(service.pendingVerifications()) }
                    get("/community-catalog") {
                        call.requireAnyPermission(service, "VIEW_ORDERS", "MANAGE_ORDERS", "MANAGE_CATALOG")
                        call.respond(
                            service.communityCatalog(
                                call.request.queryParameters["state"],
                                call.request.queryParameters["municipality"],
                                call.request.queryParameters["parish"]
                            )
                        )
                    }
                    post("/usuarios/{id}/verification") {
                        call.requirePermission(service, "REVIEW_USERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.reviewUserVerification(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Verificación actualizada."))
                    }
                    // Compatibilidad con el contrato histórico usado por Android.
                    post("/users/{id}/verification") {
                        call.requirePermission(service, "REVIEW_USERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Usuario inválido.")
                        service.reviewUserVerification(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Verificación actualizada."))
                    }
                    post("/verifications/{id}/review") {
                        call.requirePermission(service, "REVIEW_USERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Verificación inválida.")
                        service.reviewVerification(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Verificación actualizada."))
                    }


                    // Compatibilidad completa con los endpoints históricos utilizados por Android.
                    // Se mantienen en paralelo con las rutas en español para evitar 404 entre versiones.
                    post("/products") {
                        call.requireAnyPermission(service, "MANAGE_CATALOG", "CREATE_PRODUCTS")
                        call.respond(HttpStatusCode.Created, service.createProduct(call.userId(), call.receive()))
                    }
                    get("/products/import-format.xlsx") {
                        call.requireAnyPermission(service, "CREATE_PRODUCTS", "MANAGE_CATALOG", "MANAGE_INVENTORY")
                        val bytes = exporter.exportProductImportFormat()
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Formato_Carga_Productos_Credicash.xlsx").toString()
                        )
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    post("/products/import") {
                        call.requireAnyPermission(service, "CREATE_PRODUCTS", "MANAGE_CATALOG", "MANAGE_INVENTORY")
                        var excelBytes: ByteArray? = null
                        var confirm = false
                        call.receiveMultipart(formFieldLimit = 12 * 1024 * 1024).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> if (part.name == "confirm") confirm = part.value.trim().equals("true", ignoreCase = true)
                                    is PartData.FileItem -> if (part.name == "file") {
                                        val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                        if (bytes.size > 10 * 1024 * 1024) throw AppException("El Excel debe pesar menos de 10 MB.")
                                        excelBytes = bytes
                                    }
                                    else -> Unit
                                }
                            } finally { part.dispose() }
                        }
                        val bytes = excelBytes ?: throw AppException("Selecciona un archivo Excel .xlsx.")
                        if (confirm) call.respond(HttpStatusCode.Created, service.importProductsExcel(call.userId(), bytes))
                        else call.respond(service.previewProductExcelImport(call.userId(), bytes))
                    }
                    delete("/products/{id}") {
                        call.requirePermission(service, "MANAGE_CATALOG")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        service.deleteProduct(call.userId(), id)
                        call.respond(MessageResponse("Producto eliminado."))
                    }
                    put("/products/{id}/stock") {
                        call.requirePermission(service, "MANAGE_INVENTORY")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        call.respond(service.setProductStock(call.userId(), id, call.receive<SetStockRequest>().stock))
                    }
                    put("/products/{id}/pricing") {
                        call.requirePermission(service, "MANAGE_PRICING")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        call.respond(service.setProductPricing(call.userId(), id, call.receive<UpdateProductPricingRequest>()))
                    }
                    post("/fairs") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        call.respond(HttpStatusCode.Created, service.saveFair(call.userId(), null, call.receive()))
                    }
                    put("/fairs/{id}") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.saveFair(call.userId(), id, call.receive()))
                    }
                    post("/fairs/{id}/publish") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.setFairPublished(call.userId(), id, call.receive<PublishFairRequest>().published))
                    }
                    post("/fairs/{id}/finalize") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.finalizeFair(call.userId(), id))
                    }
                    delete("/fairs/{id}") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        service.deleteFair(call.userId(), id)
                        call.respond(MessageResponse("Jornada eliminada."))
                    }
                    post("/fairs/{fairId}/products/{productId}/image") {
                        call.requireAnyPermission(service, "MANAGE_CATALOG", "MANAGE_PRODUCT_IMAGES")
                        val fairId = call.parameters["fairId"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        val productId = call.parameters["productId"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        var imagePath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && part.name == "image") {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("La imagen debe pesar menos de 10 MB.")
                                    imagePath = service.storeUpload("fair-products", "$fairId-$productId", part.originalFileName ?: "producto.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateFairProductImage(call.userId(), fairId, productId, imagePath ?: throw AppException("Selecciona una imagen.")))
                    }
                    post("/fairs/{id}/cover") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val fairId = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        var coverPath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && (part.name == "image" || part.name == "cover")) {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("La carátula debe pesar menos de 10 MB.")
                                    coverPath = service.storeUpload("fair-covers", fairId.toString(), part.originalFileName ?: "caratula.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateFairCover(call.userId(), fairId, coverPath ?: throw AppException("Selecciona una carátula.")))
                    }
                    post("/communities") {
                        call.requirePermission(service, "MANAGE_CATALOG")
                        call.respond(HttpStatusCode.Created, service.createCommunity(call.userId(), call.receive()))
                    }
                    post("/productos") { call.requireAnyPermission(service, "MANAGE_CATALOG", "CREATE_PRODUCTS"); call.respond(HttpStatusCode.Created, service.createProduct(call.userId(), call.receive())) }
                    delete("/productos/{id}") {
                        call.requirePermission(service, "MANAGE_CATALOG")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        service.deleteProduct(call.userId(), id)
                        call.respond(MessageResponse("Producto eliminado."))
                    }
                    put("/productos/{id}/stock") {
                        call.requirePermission(service, "MANAGE_INVENTORY")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        call.respond(service.setProductStock(call.userId(), id, call.receive<SetStockRequest>().stock))
                    }
                    put("/productos/{id}/pricing") {
                        call.requirePermission(service, "MANAGE_PRICING")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        call.respond(service.setProductPricing(call.userId(), id, call.receive<UpdateProductPricingRequest>()))
                    }

                    post("/jornadas") { call.requirePermission(service, "MANAGE_ORDERS"); call.respond(HttpStatusCode.Created, service.saveFair(call.userId(), null, call.receive())) }
                    put("/jornadas/{id}") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.saveFair(call.userId(), id, call.receive()))
                    }
                    post("/jornadas/{id}/publish") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.setFairPublished(call.userId(), id, call.receive<PublishFairRequest>().published))
                    }
                    post("/jornadas/{id}/finalize") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        call.respond(service.finalizeFair(call.userId(), id))
                    }
                    delete("/jornadas/{id}") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        service.deleteFair(call.userId(), id)
                        call.respond(MessageResponse("Jornada eliminada."))
                    }
                    post("/jornadas/{fairId}/productos/{productId}/image") {
                        call.requireAnyPermission(service, "MANAGE_CATALOG", "MANAGE_PRODUCT_IMAGES")
                        val fairId = call.parameters["fairId"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        val productId = call.parameters["productId"]?.toLongOrNull() ?: throw AppException("Producto inválido.")
                        var imagePath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && part.name == "image") {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("La imagen debe pesar menos de 10 MB.")
                                    imagePath = service.storeUpload("fair-productos", "$fairId-$productId", part.originalFileName ?: "producto.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateFairProductImage(call.userId(), fairId, productId, imagePath ?: throw AppException("Selecciona una imagen.")))
                    }

                    post("/jornadas/{id}/caratula") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val fairId = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Jornada inválida.")
                        var coverPath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && (part.name == "image" || part.name == "cover")) {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("La carátula debe pesar menos de 10 MB.")
                                    coverPath = service.storeUpload("fair-covers", fairId.toString(), part.originalFileName ?: "caratula.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateFairCover(call.userId(), fairId, coverPath ?: throw AppException("Selecciona una carátula.")))
                    }
                    post("/comunidades") { call.requirePermission(service, "MANAGE_ORDERS"); call.respond(HttpStatusCode.Created, service.createCommunity(call.userId(), call.receive())) }
                    post("/combos") { call.requirePermission(service, "MANAGE_CATALOG"); call.respond(HttpStatusCode.Created, service.createCombo(call.userId(), call.receive())) }
                    post("/combos/{id}/cover") {
                        call.requirePermission(service, "MANAGE_CATALOG")
                        val comboId = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Combo inválido.")
                        var coverPath: String? = null
                        call.receiveMultipart(formFieldLimit = 16 * 1024 * 1024).forEachPart { part ->
                            try {
                                if (part is PartData.FileItem && (part.name == "image" || part.name == "cover")) {
                                    val bytes = part.provider().readRemaining(10L * 1024L * 1024L + 1L).readBytes()
                                    if (bytes.size > 10 * 1024 * 1024) throw AppException("La carátula debe pesar menos de 10 MB.")
                                    coverPath = service.storeUpload("combo-covers", comboId.toString(), part.originalFileName ?: "caratula.jpg", bytes).relativePath
                                }
                            } finally { part.dispose() }
                        }
                        call.respond(service.updateComboCover(call.userId(), comboId, coverPath ?: throw AppException("Selecciona una carátula.")))
                    }
                    get("/user-payment-reports") {
                        call.requireAnyPermission(service, "VIEW_PAYMENTS", "REVIEW_PAYMENTS")
                        call.respond(service.adminUserPaymentReports())
                    }
                    post("/user-payment-reports/{id}/decision") {
                        call.requirePermission(service, "REVIEW_PAYMENTS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Reporte de pago inválido.")
                        service.decideUserPaymentReport(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Reporte de pago revisado."))
                    }
                    get("/payment-reviews") { call.requireAnyPermission(service, "VIEW_PAYMENTS", "REVIEW_PAYMENTS"); call.respond(service.paymentReviews()) }
                    post("/payments/{id}/decision") {
                        call.requirePermission(service, "REVIEW_PAYMENTS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Pago inválido.")
                        service.decidePayment(call.userId(), id, call.receive<PaymentVerificationDecisionRequest>())
                        call.respond(MessageResponse("Pago revisado."))
                    }
                    get("/payment-verifications") { call.requireAnyPermission(service, "VIEW_PAYMENTS", "REVIEW_PAYMENTS"); call.respond(service.paymentVerifications()) }
                    post("/payment-verifications/{id}/decision") {
                        call.requirePermission(service, "REVIEW_PAYMENTS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Verificación de pago inválida.")
                        service.decidePaymentVerification(call.userId(), id, call.receive())
                        call.respond(MessageResponse("Verificación de pago registrada de forma inmutable."))
                    }
                    get("/invoices/integrity") {
                        call.requirePermission(service, "VIEW_AUDIT")
                        call.respond(service.adminInvoiceIntegrityRecords())
                    }
                    get("/quality/summary") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_INVENTORY", "VIEW_FINANCIALS")
                        call.respond(service.operationalQualitySummary())
                    }
                    get("/inventory/integrity") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_INVENTORY", "MANAGE_INVENTORY")
                        call.respond(service.inventoryIntegrity())
                    }
                    get("/qr-records") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_ORDERS", "MANAGE_ORDERS")
                        call.respond(service.scannedInvoiceRecords())
                    }
                    post("/qr-records") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_ORDERS", "MANAGE_ORDERS")
                        call.respond(HttpStatusCode.Created, service.registerQrScan(call.userId(), call.receive()))
                    }
                    get("/community-requests") { call.requireAnyPermission(service, "VIEW_ORDERS", "MANAGE_ORDERS"); call.respond(service.communityRequests()) }
                    patch("/community-requests/{id}/status") {
                        call.requirePermission(service, "MANAGE_ORDERS")
                        val id = call.parameters["id"]?.toLongOrNull() ?: throw AppException("Solicitud inválida.")
                        service.updateCommunityRequestStatus(call.userId(), id, call.receive<UpdateCommunityRequestStatusRequest>().status)
                        call.respond(MessageResponse("Estado actualizado."))
                    }
                    get("/export.xlsx") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_FINANCIALS")
                        runCatching { service.refreshAllCreditHistories() }
                        val bytes = exporter.export()
                        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Nomina_Credicash.xlsx").toString())
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    get("/payroll/export.xlsx") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_FINANCIALS", "VIEW_USERS")
                        val bytes = exporter.exportPayroll()
                        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Nomina_Credicash.xlsx").toString())
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    get("/credit-history/export.xlsx") {
                        call.requireAnyPermission(service, "VIEW_AUDIT", "VIEW_FINANCIALS")
                        runCatching { service.refreshAllCreditHistories() }
                        val bytes = exporter.exportCreditHistory()
                        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Historial_Crediticio_Credicash.xlsx").toString())
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                    get("/products/export.xlsx") {
                        call.requireAnyPermission(service, "VIEW_INVENTORY", "MANAGE_INVENTORY", "CREATE_PRODUCTS", "MANAGE_CATALOG")
                        val bytes = exporter.exportProducts()
                        call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "Productos_Credicash.xlsx").toString())
                        call.respondBytes(bytes, ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                }
            }
        }
    }

    startupScope.launch {
        initializeDatabaseInBackground(
            config = config,
            database = database,
            service = service,
            logger = appLogger
        )
        if (isActive) {
            ledgerRealtimeNotifier.start(startupScope)
            operationalRealtimeNotifier.start(startupScope)
        }
    }
}


private suspend fun initializeDatabaseInBackground(
    config: AppConfig,
    database: Database,
    service: AppService,
    logger: org.slf4j.Logger
) {
    var attempt = 0
    while (true) {
        attempt++
        startupPhase.set(StartupPhase.MIGRATING)
        try {
            logger.info("Credicash {}: preparando PostgreSQL (intento {}).", CREDICASH_APP_VERSION, attempt)
            // El esquema mínimo de autenticación se confirma primero. Así los usuarios
            // existentes nunca fallan en el PIN mientras terminan las migraciones extensas.
            database.ensureAuthenticationSchema()
            database.initializeSchema()
            database.ensureAuthenticationSchema()
            database.verifyRequiredSchema()

            logger.info("Credicash 7.0.0: Administradores se asignan desde las aplicaciones; bootstrap ADMIN deshabilitado.")

            runCatching {
                if (service.hasAccountantAccount()) {
                    service.ensureBootstrapAccountant()
                    logger.info("Contador existente verificado sin sobrescribir contraseña ni PIN desde variables de entorno.")
                } else {
                    val bootstrapError = config.bootstrapAccountantValidationError()
                    when {
                        !config.hasAnyBootstrapAccountantValue -> {
                            logger.warn(
                                "No existe un Contador y no se configuró BOOTSTRAP_ACCOUNTANT_* para la primera instalación. " +
                                    "El backend seguirá disponible para no bloquear el despliegue."
                            )
                        }
                        bootstrapError != null -> {
                            // Una variable bootstrap vieja no es un fallo de PostgreSQL ni debe
                            // llenar los logs del proveedor con stack traces. Se informa de forma explícita y el
                            // servidor continúa listo; las credenciales existentes nunca se tocan.
                            logger.warn(
                                "El Contador inicial no se creó porque la configuración bootstrap requiere corrección: {}",
                                bootstrapError
                            )
                        }
                        else -> {
                            service.ensureBootstrapAccountant()
                            logger.info("Contador inicial creado desde variables protegidas de entorno.")
                        }
                    }
                }
            }.onFailure { error ->
                logger.error(
                    "No se pudo preparar la cuenta del Contador, pero PostgreSQL y el servidor continuarán disponibles: {}",
                    error.message
                )
            }

            logger.info("Credicash 7.0.0: Almacenistas se asignan desde las aplicaciones; bootstrap WAREHOUSE deshabilitado.")

            startupLastError = null
            startupPhase.set(StartupPhase.READY)
            logger.info("Credicash {}: PostgreSQL y migraciones listos.", CREDICASH_APP_VERSION)
            return
        } catch (error: Throwable) {
            startupLastError = error.message
            startupPhase.set(StartupPhase.DEGRADED)
            val waitMillis = (2_000L * attempt.coerceAtMost(15)).coerceAtMost(30_000L)
            logger.error(
                "Credicash {}: PostgreSQL todavía no está listo (intento {}). " +
                    "Se reintentará en {} ms. Causa: {}",
                CREDICASH_APP_VERSION,
                attempt,
                waitMillis,
                error.message,
                error
            )
            delay(waitMillis)
        }
    }
}

private fun ApplicationCall.userId(): Long = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asLong()
    ?: throw ForbiddenException("Sesión inválida.")

private fun ApplicationCall.sessionId(): UUID = principal<JWTPrincipal>()?.payload?.getClaim("sessionId")?.asString()
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    ?: throw ForbiddenException("Sesión inválida.")

private fun ApplicationCall.requireAdmin(service: AppService) {
    service.requireAdmin(userId())
}

private fun ApplicationCall.requireAccountant(service: AppService) {
    service.requireAccountant(userId())
}

private fun ApplicationCall.requireAccountantRegistrationFallback(service: AppService) {
    service.requireAccountantRegistrationFallback(userId())
}

private fun ApplicationCall.requirePermission(service: AppService, permission: String) {
    service.requirePermission(userId(), permission)
}

private fun ApplicationCall.requireAnyPermission(service: AppService, vararg permissions: String) {
    service.requireAnyPermission(userId(), *permissions)
}

private suspend fun handleRegistrationDocumentVerification(call: ApplicationCall, service: AppService) {
    val userId = call.parameters["id"]?.toLongOrNull()
        ?: throw AppException("Identificador de usuario inválido.")
    val registrationToken = call.request.headers["X-Registration-Token"]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: throw ForbiddenException("Falta la autorización temporal de registro.")

    // La capacidad se valida antes de leer el cuerpo y escribir en el volumen.
    service.validateRegistrationDocumentToken(userId, registrationToken)

    var documentType: String? = null
    var documentNumber: String? = null
    var frontPath: String? = null
    var backPath: String? = null
    var selfiePath: String? = null
    val storedFiles = mutableListOf<File>()
    var submitted = false

    try {
        call.receiveMultipart(formFieldLimit = 25 * 1024 * 1024).forEachPart { part ->
            try {
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "documentType" -> documentType = part.value
                        "documentNumber" -> documentNumber = part.value
                    }
                    is PartData.FileItem -> {
                        val fieldName = part.name
                        if (fieldName !in setOf("front", "back", "selfie")) return@forEachPart
                        val alreadyReceived = when (fieldName) {
                            "front" -> frontPath != null
                            "back" -> backPath != null
                            else -> selfiePath != null
                        }
                        if (alreadyReceived) throw AppException("Cada archivo de verificación debe enviarse una sola vez.")

                        val bytes = part.provider().readRemaining(12L * 1024L * 1024L + 1L).readBytes()
                        if (bytes.size > 12 * 1024 * 1024) throw AppException("Cada archivo debe pesar menos de 12 MB.")
                        val validated = UploadPolicy.validate("documents", bytes)
                        if (fieldName == "selfie" && !validated.image) {
                            throw AppException("La selfie debe ser una imagen JPG, PNG o WEBP.")
                        }
                        val stored = service.storeUpload(
                            "documents",
                            userId.toString(),
                            part.originalFileName ?: "archivo.bin",
                            bytes
                        )
                        storedFiles += stored.absoluteFile
                        when (fieldName) {
                            "front" -> frontPath = stored.relativePath
                            "back" -> backPath = stored.relativePath
                            "selfie" -> selfiePath = stored.relativePath
                        }
                    }
                    else -> Unit
                }
            } finally {
                part.dispose()
            }
        }

        service.submitDocumentVerification(
            userId = userId,
            registrationToken = registrationToken,
            documentType = documentType ?: "NATIONAL_ID",
            documentNumber = documentNumber ?: throw AppException("Ingresa el número del documento."),
            frontPath = frontPath ?: throw AppException("Debes tomar una foto de tu documento de identidad."),
            backPath = backPath,
            selfiePath = selfiePath ?: throw AppException("Debes tomar una selfie actual para validar tu identidad.")
        )
        submitted = true
    } catch (error: Throwable) {
        if (!submitted) storedFiles.forEach { file -> runCatching { file.delete() } }
        throw error
    }

    call.respond(HttpStatusCode.Created, MessageResponse("Documento enviado para revisión."))
}


private fun discoverUploadRoots(primary: File): List<File> {
    val roots = linkedSetOf<File>()
    fun add(candidate: File?) {
        if (candidate == null) return
        val normalized = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile }
        if (normalized.exists() && normalized.isDirectory) roots += normalized
    }

    add(primary)
    val workingDir = File(System.getProperty("user.dir")).absoluteFile
    add(File(workingDir, "runtime/uploads"))
    add(File(workingDir, "data/uploads"))
    add(File(workingDir.parentFile ?: workingDir, "runtime/uploads"))
    add(File(workingDir.parentFile ?: workingDir, "data/uploads"))
    add(File(workingDir, "Servidor_Local_Windows/runtime/uploads"))
    add(File(workingDir, "Servidor_Local_Windows/data/uploads"))

    // Busca instalaciones anteriores extraídas junto al proyecto actual (por ejemplo, en el Escritorio).
    val projectRoot = if (workingDir.name.equals("Servidor_Local_Windows", ignoreCase = true)) workingDir.parentFile else workingDir
    val siblingsRoot = projectRoot?.parentFile
    siblingsRoot?.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory && it.name.startsWith("Credicash_Completo_Windows_", ignoreCase = true) }
        ?.forEach { previousProject ->
            add(File(previousProject, "Servidor_Local_Windows/runtime/uploads"))
            add(File(previousProject, "Servidor_Local_Windows/data/uploads"))
            add(File(previousProject, "runtime/uploads"))
            add(File(previousProject, "data/uploads"))
        }

    return roots.toList()
}

private fun migrateLegacyUploads(primary: File, legacyRoots: List<File>, logger: org.slf4j.Logger) {
    legacyRoots
        .filter { it.exists() && it.isDirectory && it.absolutePath != primary.absolutePath }
        .forEach { legacyRoot ->
            runCatching {
                legacyRoot.walkTopDown()
                    .filter { it.isFile }
                    .forEach fileLoop@{ source ->
                        val relative = source.relativeTo(legacyRoot).invariantSeparatorsPath
                        val destination = safeUploadCandidate(primary, relative) ?: return@fileLoop
                        if (!destination.exists()) {
                            destination.parentFile?.mkdirs()
                            java.nio.file.Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
                        }
                    }
            }.onFailure { error ->
                logger.warn("No se pudieron migrar adjuntos desde ${legacyRoot.absolutePath}: ${error.message}")
            }
        }
}

private fun resolveUploadFile(roots: List<File>, relativePath: String): File? {
    if (relativePath.isBlank()) return null
    return roots.asSequence()
        .mapNotNull { root -> safeUploadCandidate(root, relativePath) }
        .firstOrNull { it.exists() && it.isFile }
}

private fun safeUploadCandidate(root: File, relativePath: String): File? {
    val normalizedRelative = relativePath.replace('\\', '/').trimStart('/')
    if (normalizedRelative.isBlank() || normalizedRelative.split('/').any { it == ".." }) return null
    val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
    val candidate = runCatching { File(canonicalRoot, normalizedRelative).canonicalFile }.getOrNull() ?: return null
    val rootPath = canonicalRoot.toPath()
    return candidate.takeIf { it.toPath().startsWith(rootPath) }
}
