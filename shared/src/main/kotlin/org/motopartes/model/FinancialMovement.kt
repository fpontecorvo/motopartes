package org.motopartes.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class FinancialMovement(
    val id: Long = 0,
    val type: MovementType,
    val amount: BigDecimal,
    val date: LocalDateTime,
    val clientId: Long? = null,
    val supplierId: Long? = null,
    val orderId: Long? = null,
    val description: String = ""
)
