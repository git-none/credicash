package com.impulsosocial.server.service

import kotlin.test.Test
import kotlin.test.assertEquals
import java.math.BigDecimal

class BudgetMathTest {
    @Test
    fun `calcula presupuesto consolidado sin duplicar inversiones internas`() {
        val result = BudgetMath.calculate(
            BudgetMathInput(
                bankBudgetUsd = BigDecimal("1000.00"),
                centralAvailableUsd = BigDecimal("400.00"),
                administratorsAvailableUsd = BigDecimal("350.00"),
                investedUsd = BigDecimal("500.00"),
                operatingExpensesUsd = BigDecimal("50.00"),
                loansDisbursedUsd = BigDecimal("300.00"),
                loansRecoveredUsd = BigDecimal("100.00"),
                loansOutstandingUsd = BigDecimal("200.00"),
                overdueLoansUsd = BigDecimal("40.00"),
                reservedUsd = BigDecimal("25.00"),
                expectedCollections30DaysUsd = BigDecimal("80.00")
            )
        )

        assertEquals(BigDecimal("750.00"), result.consolidatedAvailableUsd)
        assertEquals(BigDecimal("350.00"), result.totalExpensesUsd)
        assertEquals(BigDecimal("225.00"), result.totalCommittedUsd)
        assertEquals(BigDecimal("805.00"), result.projectedAvailable30DaysUsd)
        assertEquals(BigDecimal("750.00"), result.expectedCashUsd)
        assertEquals(BigDecimal("0.00"), result.integrityDifferenceUsd)
        assertEquals(BigDecimal("35.00"), result.executionPercent)
        assertEquals(BigDecimal("33.33"), result.recoveryPercent)
        assertEquals(BigDecimal("333.33"), result.liquidityCoveragePercent)
        assertEquals("BALANCED", result.integrityStatus)
    }

    @Test
    fun `la proyeccion nunca produce disponibilidad negativa`() {
        val result = BudgetMath.calculate(
            BudgetMathInput(
                bankBudgetUsd = BigDecimal("100.00"),
                centralAvailableUsd = BigDecimal("10.00"),
                administratorsAvailableUsd = BigDecimal("5.00"),
                investedUsd = BigDecimal("0.00"),
                operatingExpensesUsd = BigDecimal("90.00"),
                loansDisbursedUsd = BigDecimal("0.00"),
                loansRecoveredUsd = BigDecimal("0.00"),
                loansOutstandingUsd = BigDecimal("0.00"),
                overdueLoansUsd = BigDecimal("0.00"),
                reservedUsd = BigDecimal("20.00"),
                expectedCollections30DaysUsd = BigDecimal("0.00")
            )
        )

        assertEquals(BigDecimal("0.00"), result.projectedAvailable30DaysUsd)
    }
}
