package com.impulsosocial.server.db

import com.impulsosocial.server.CREDICASH_APP_VERSION
import com.impulsosocial.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class Database(private val config: AppConfig) {
    @Volatile private var authenticationSchemaReady = false
    private val authenticationSchemaLock = Any()
    private val hikariDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.dbMaximumPoolSize
            minimumIdle = config.dbMinimumIdle
            connectionTimeout = config.dbConnectionTimeoutMs
            validationTimeout = config.dbValidationTimeoutMs
            idleTimeout = config.dbIdleTimeoutMs
            maxLifetime = config.dbMaxLifetimeMs
            keepaliveTime = config.dbKeepaliveTimeMs.coerceAtMost(config.dbMaxLifetimeMs - 1_000L)
            leakDetectionThreshold = config.dbLeakDetectionThresholdMs
            initializationFailTimeout = -1
            isAutoCommit = true
            poolName = "CredicashPool"
            addDataSourceProperty("ApplicationName", "Credicash-$CREDICASH_APP_VERSION")
            addDataSourceProperty("tcpKeepAlive", "true")
            addDataSourceProperty("reWriteBatchedInserts", "true")
        })
    }

    private val hikari: HikariDataSource by hikariDelegate

    val dataSource: DataSource
        get() = hikari

    /**
     * Repara de forma idempotente la parte crítica del esquema usada por el PIN y la
     * sesión persistente. El servidor abre /health antes de terminar todas las migraciones;
     * por eso una instalación antigua podía aceptar usuario/contraseña y fallar justo al
     * verificar el PIN si las columnas de sesión única todavía no existían.
     *
     * No elimina ni modifica usuarios. Las sesiones antiguas sin identificador de
     * dispositivo se revocan porque no pueden cumplir la política de sesión única.
     */
    fun ensureAuthenticationSchema() {
        if (authenticationSchemaReady) return
        synchronized(authenticationSchemaLock) {
            if (authenticationSchemaReady) return
            dataSource.connection.use { connection ->
                val previousAutoCommit = connection.autoCommit
                connection.autoCommit = false
                try {
                    val applied = applyAuthenticationSchema(connection)
                    connection.commit()
                    if (applied) authenticationSchemaReady = true
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = previousAutoCommit
                }
            }
        }
    }

    /**
     * Variante para una transacción ya abierta. No marca la caché como lista porque la
     * transacción llamadora todavía podría revertirse por un PIN incorrecto u otro error.
     */
    fun ensureAuthenticationSchema(connection: Connection) {
        if (authenticationSchemaReady) return
        synchronized(authenticationSchemaLock) {
            if (!authenticationSchemaReady) {
                val applied = applyAuthenticationSchema(connection)
                if (applied && connection.autoCommit) authenticationSchemaReady = true
            }
        }
    }

    private fun applyAuthenticationSchema(connection: Connection): Boolean {
        val usersTableExists = connection.prepareStatement("SELECT to_regclass('public.usuarios') IS NOT NULL").use { statement ->
            statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
        }
        if (!usersTableExists) return false

        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sesiones_usuario (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                    device_id_hash VARCHAR(64),
                    device_name VARCHAR(255),
                    app_version VARCHAR(80),
                    ip_address INET,
                    expires_at TIMESTAMPTZ NOT NULL,
                    revoked_at TIMESTAMPTZ,
                    ended_reason VARCHAR(80),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    last_used_at TIMESTAMPTZ,
                    last_heartbeat_at TIMESTAMPTZ
                )
                """.trimIndent()
            )
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS device_id_hash VARCHAR(64)")
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS device_name VARCHAR(255)")
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS app_version VARCHAR(80)")
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ")
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ")
            statement.execute("ALTER TABLE sesiones_usuario ADD COLUMN IF NOT EXISTS ended_reason VARCHAR(80)")
            statement.execute(
                """
                UPDATE sesiones_usuario
                SET revoked_at=COALESCE(revoked_at,NOW()),
                    ended_reason=COALESCE(ended_reason,'LEGACY_SESSION_WITHOUT_DEVICE')
                WHERE revoked_at IS NULL AND (device_id_hash IS NULL OR BTRIM(device_id_hash)='')
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active
                ON sesiones_usuario(user_id, revoked_at, expires_at)
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_user_sessions_heartbeat
                ON sesiones_usuario(user_id, last_heartbeat_at DESC)
                WHERE revoked_at IS NULL
                """.trimIndent()
            )
        }
        return true
    }

    fun initializeSchema() {
        val schema = requireNotNull(javaClass.classLoader.getResourceAsStream("db/schema.sql")) {
            "No se encontró db/schema.sql dentro del servidor."
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

        val statements = SqlScriptParser.split(schema)
            .filter { !it.equals("BEGIN", ignoreCase = true) && !it.equals("COMMIT", ignoreCase = true) }

        transaction { connection ->
            // Evita que dos despliegues intenten modificar el esquema al mismo tiempo.
            connection.createStatement().use { lockStatement ->
                lockStatement.execute("SELECT pg_advisory_xact_lock(5041001)")
            }

            connection.createStatement().use { statement ->
                statements.forEachIndexed { index, sql ->
                    val savepoint = connection.setSavepoint("schema_$index")
                    try {
                        statement.execute(sql)
                        runCatching { connection.releaseSavepoint(savepoint) }
                    } catch (error: SQLException) {
                        connection.rollback(savepoint)

                        if (isRecoverableSchemaMigration(error, sql)) {
                            System.err.println(
                                "Credicash: migración histórica omitida de forma segura " +
                                    "(${error.sqlState ?: "sin SQLSTATE"}): ${error.message.orEmpty().take(280)}"
                            )
                            runCatching { connection.releaseSavepoint(savepoint) }
                        } else {
                            val preview = sql
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(320)
                            throw SQLException(
                                "Falló la sentencia ${index + 1}/${statements.size} del esquema: $preview",
                                error.sqlState,
                                error.errorCode,
                                error
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isRecoverableSchemaMigration(error: SQLException, sql: String): Boolean {
        val message = error.message.orEmpty()
        val normalizedSql = sql.lowercase()
        val knownLegacyConstraint =
            normalizedSql.contains("cuotas_credito_installment_number_check") ||
                normalizedSql.contains("prestamos_credito_installment_count_check") ||
                normalizedSql.contains("cuotas_credito_loan_id_installment_number_key")

        val recoverableSqlState = error.sqlState in setOf(
            "23514", // check_violation
            "23505", // unique_violation
            "42710"  // duplicate_object
        )

        return knownLegacyConstraint && (
            recoverableSqlState ||
                message.contains("constraint", ignoreCase = true)
        )
    }


    fun verifyRequiredSchema() {
        val requiredTables = listOf(
            "versiones_esquema",
            "usuarios",
            "perfiles_usuario",
            "sesiones_usuario",
            "notificaciones",
            "verificaciones_documentos",
            "credenciales_biometricas_dispositivo",
            "consentimientos_usuario",
            "transacciones_carteras_continuas",
            "movimientos_presupuestarios",
            "evaluaciones_predictivas",
            "corridas_predictivas_presupuesto",
            "vinculaciones_telegram",
            "enlaces_vinculacion_telegram",
            "reportes_pago_usuario",
            "conciliaciones_pago",
            "solicitudes_doble_aprobacion",
            "cierres_contables",
            "negocios_asociados"
        )

        dataSource.connection.use { connection ->
            val missing = requiredTables.filter { table ->
                connection.prepareStatement("SELECT to_regclass(?)").use { statement ->
                    statement.setString(1, "public.$table")
                    statement.executeQuery().use { result ->
                        !result.next() || result.getString(1) == null
                    }
                }
            }

            check(missing.isEmpty()) {
                "El esquema PostgreSQL está incompleto. Faltan: ${missing.joinToString(", ")}"
            }

            connection.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM versiones_esquema WHERE version = 74)"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    check(result.next() && result.getBoolean(1)) {
                        "La migración 74 de Credicash 7.0.0 no está registrada en versiones_esquema."
                    }
                }
            }

            connection.prepareStatement(
                """
                SELECT COUNT(*)
                FROM prestamos_credito
                WHERE order_id IS NULL
                  AND (invoice_number IS NULL OR BTRIM(invoice_number)='')
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    check(result.next() && result.getLong(1) == 0L) {
                        "Existen préstamos directos sin pedido y sin número de préstamo/factura propio."
                    }
                }
            }

            val requiredColumns = mapOf(
                "productos" to listOf("base_price_usd", "bcv_rate", "pricing_mode", "price_updated_at", "minimum_stock", "last_counted_at", "technical_details"),
                "desafios_autenticacion" to listOf("attempts"),
                "sesiones_usuario" to listOf("device_id_hash", "last_heartbeat_at", "ended_reason"),
                "movimientos_presupuestarios" to listOf("tipo", "monto_usd", "tasa_bcv", "saldo_antes_usd", "saldo_despues_usd", "idempotency_key"),
                "facturas" to listOf("integrity_status", "integrity_score", "calculated_total_bs", "integrity_difference_bs", "document_hash", "algorithm_version", "validation_warnings", "integrity_verified_at"),
                "usuarios" to listOf("admin_subrole"),
                "reportes_pago_usuario" to listOf("risk_score", "risk_level", "proof_sha256", "proof_visual_hash", "bank_confirmed", "amount_difference_bs", "amount_difference_percent", "decision_version"),
                "conciliaciones_pago" to listOf("payment_report_id", "accountant_id", "status", "confidence_percent"),
                "solicitudes_doble_aprobacion" to listOf("action_type", "requested_by", "approved_by", "status"),
                "cierres_contables" to listOf("period_month", "accountant_id", "status", "pending_differences"),
                "negocios_asociados" to listOf("commercial_name", "legal_name", "rif", "logo_path", "active", "payment_mode", "created_by"),
                "jornadas" to listOf("business_id"),
                "prestamos_credito" to listOf(
                    "invoice_number", "credit_request_id", "lender_type", "lender_business_id",
                    "repayment_business_id", "disbursement_destination_type", "repayment_payment_mode",
                    "repayment_business_commercial_name", "repayment_business_rif"
                )
            )
            val missingColumns = buildList {
                requiredColumns.forEach { (table, columns) ->
                    columns.forEach { column ->
                        val exists = connection.prepareStatement(
                            """
                            SELECT EXISTS(
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema='public' AND table_name=? AND column_name=?
                            )
                            """.trimIndent()
                        ).use { statement ->
                            statement.setString(1, table)
                            statement.setString(2, column)
                            statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
                        }
                        if (!exists) add("$table.$column")
                    }
                }
            }
            check(missingColumns.isEmpty()) {
                "El esquema de Credicash 7.0.0 está incompleto. Faltan columnas: ${missingColumns.joinToString(", ")}"
            }

            connection.prepareStatement(
                """
                SELECT EXISTS(
                    SELECT 1
                    FROM pg_constraint
                    WHERE conrelid='public.usuarios'::regclass
                      AND conname='usuarios_admin_subrole_check'
                      AND pg_get_constraintdef(oid) ILIKE '%WAREHOUSE%'
                )
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    check(result.next() && result.getBoolean(1)) {
                        "La restricción usuarios_admin_subrole_check no admite el subrol WAREHOUSE."
                    }
                }
            }

            connection.prepareStatement(
                """
                SELECT COUNT(*)
                FROM usuarios
                WHERE NOT (
                    (role='BENEFICIARY' AND admin_subrole IS NULL)
                    OR (role='ADMIN' AND admin_subrole IN ('GENERAL','SUPERVISOR','ANALYST','SUPPORT','AUDITOR','ANTIFRAUD'))
                    OR (role='ACCOUNTANT' AND admin_subrole='ACCOUNTING')
                    OR (role='WAREHOUSE' AND admin_subrole='WAREHOUSE')
                )
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    check(result.next() && result.getLong(1) == 0L) {
                        "Existen usuarios con una combinación de rol y subrol incompatible."
                    }
                }
            }
        }
    }

    fun isHealthy(): Boolean = runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1").use { statement ->
                statement.executeQuery().use { result -> result.next() && result.getInt(1) == 1 }
            }
        }
    }.getOrDefault(false)

    fun <T> transaction(block: (Connection) -> T): T {
        dataSource.connection.use { connection ->
            val previous = connection.autoCommit
            connection.autoCommit = false
            return try {
                val result = block(connection)
                connection.commit()
                result
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = previous
            }
        }
    }

    fun close() {
        if (hikariDelegate.isInitialized()) {
            hikari.close()
        }
    }
}
