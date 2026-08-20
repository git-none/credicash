package com.impulsosocial.server.integrations

import java.time.OffsetDateTime

/**
 * Contrato preparado para integrar en el futuro APIs bancarias u Open Banking.
 * El presupuesto operativo se registra en la base de datos. Ningún proveedor externo se invoca
 * hasta que exista una integración bancaria configurada y confirmada.
 */
interface BankBudgetProvider {
    val providerId: String

    fun connectionStatus(): BankConnectionStatus

    fun fetchAvailableBudget(externalAccountId: String): BankBudgetSnapshot

    fun verifyIncomingTransaction(externalTransactionId: String): BankTransactionVerification
}

data class BankConnectionStatus(
    val connected: Boolean,
    val status: String,
    val checkedAt: String = OffsetDateTime.now().toString()
)

data class BankBudgetSnapshot(
    val externalAccountId: String,
    val availableUsd: Double,
    val currency: String = "USD",
    val synchronizedAt: String = OffsetDateTime.now().toString()
)

data class BankTransactionVerification(
    val externalTransactionId: String,
    val verified: Boolean,
    val amountUsd: Double? = null,
    val verifiedAt: String = OffsetDateTime.now().toString()
)

/**
 * Adaptador seguro de espera. Hace explícito que la estructura está lista, pero
 * impide llamadas bancarias ficticias y evita acreditar dinero sin confirmación real.
 */
class PreparedBankBudgetProvider : BankBudgetProvider {
    override val providerId: String = "PENDING_BANK_PROVIDER"

    override fun connectionStatus(): BankConnectionStatus =
        BankConnectionStatus(connected = false, status = "READY_FOR_BANK_API")

    override fun fetchAvailableBudget(externalAccountId: String): BankBudgetSnapshot =
        throw UnsupportedOperationException("La API bancaria todavía no ha sido configurada.")

    override fun verifyIncomingTransaction(externalTransactionId: String): BankTransactionVerification =
        BankTransactionVerification(externalTransactionId = externalTransactionId, verified = false)
}
