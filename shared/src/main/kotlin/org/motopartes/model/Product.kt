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
    val stock: Int = 0
) {
    fun purchasePriceInArs(dollarRate: BigDecimal): BigDecimal = when (purchaseCurrency) {
        Currency.ARS -> purchasePrice
        Currency.USD -> purchasePrice.multiply(dollarRate)
    }

    fun suggestedSalePrice(dollarRate: BigDecimal, markupArs: BigDecimal, markupUsd: BigDecimal): BigDecimal {
        val markup = when (purchaseCurrency) {
            Currency.ARS -> markupArs
            Currency.USD -> markupUsd
        }
        return purchasePriceInArs(dollarRate).multiply(markup).setScale(2, RoundingMode.HALF_UP)
    }
}
