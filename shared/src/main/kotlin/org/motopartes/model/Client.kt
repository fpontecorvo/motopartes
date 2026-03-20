package org.motopartes.model

import java.math.BigDecimal

data class Client(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val balance: BigDecimal = BigDecimal.ZERO
)
