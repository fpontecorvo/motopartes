package org.motopartes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class DollarRate(
    val id: Long = 0,
    val rate: BigDecimal,
    val date: LocalDate
)
