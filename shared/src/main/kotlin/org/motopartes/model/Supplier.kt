package org.motopartes.model

import java.math.BigDecimal

data class Supplier(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val balance: BigDecimal = BigDecimal.ZERO
)
