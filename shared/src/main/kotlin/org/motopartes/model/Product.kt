package org.motopartes.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Product(
    val id: Long = 0,
    val code: String,
    val name: String,
    val description: String = "",
    val purchasePrice: BigDecimal,
    val purchaseCurrency: Currency,
    val salePrice: BigDecimal,
    val stock: Int = 0
) {
    fun purchasePriceInArs(dollarRate: BigDecimal): BigDecimal = when (purchaseCurrency) {
        Currency.ARS -> purchasePrice
        Currency.USD -> purchasePrice.multiply(dollarRate)
    }

    companion object {
        private val MARKUP = BigDecimal("1.30")

        fun defaultSalePrice(purchasePrice: BigDecimal, purchaseCurrency: Currency, dollarRate: BigDecimal): BigDecimal {
            val costArs = when (purchaseCurrency) {
                Currency.ARS -> purchasePrice
                Currency.USD -> purchasePrice.multiply(dollarRate)
            }
            return costArs.multiply(MARKUP).setScale(2, RoundingMode.HALF_UP)
        }
    }
}
