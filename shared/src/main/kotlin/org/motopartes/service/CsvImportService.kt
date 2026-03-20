package org.motopartes.service

import org.motopartes.model.Currency
import org.motopartes.model.Product
import org.motopartes.repository.DollarRateRepository
import org.motopartes.repository.ProductRepository
import java.math.BigDecimal

data class ImportResult(
    val created: Int,
    val updated: Int,
    val errors: List<String>
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (created > 0) parts.add("$created creados")
        if (updated > 0) parts.add("$updated actualizados")
        if (errors.isNotEmpty()) parts.add("${errors.size} errores")
        return parts.joinToString(", ").ifEmpty { "Sin cambios" }
    }
}

class CsvImportService(
    private val productRepo: ProductRepository,
    private val dollarRateRepo: DollarRateRepository
) {

    /**
     * Import products from CSV content.
     *
     * Expected columns (by header name, order flexible):
     *   code, name, purchasePrice, purchaseCurrency (USD/ARS), description, salePrice, stock
     *
     * Minimum required: code, name, purchasePrice
     * If purchaseCurrency missing → ARS
     * If salePrice missing → auto-calculated (cost * 1.30)
     * If stock missing → 0 for new products, unchanged for existing
     */
    fun import(csvContent: String, hasHeader: Boolean = true): ImportResult {
        val lines = csvContent.trimEnd().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(0, 0, listOf("Archivo vacio"))

        val dollarRate = dollarRateRepo.getLatest()?.rate

        val headerLine = if (hasHeader) lines.first() else "code,name,purchasePrice,purchaseCurrency,description,salePrice,stock"
        val dataLines = if (hasHeader) lines.drop(1) else lines

        val headers = headerLine.split(",", ";").map { it.trim().lowercase() }
        val colCode = headers.indexOfFirst { it in listOf("code", "codigo", "cod") }
        val colName = headers.indexOfFirst { it in listOf("name", "nombre") }
        val colPrice = headers.indexOfFirst { it in listOf("purchaseprice", "precio", "price", "preciocompra", "costo", "cost") }
        val colCurrency = headers.indexOfFirst { it in listOf("purchasecurrency", "moneda", "currency", "mon") }
        val colDescription = headers.indexOfFirst { it in listOf("description", "descripcion", "desc") }
        val colSalePrice = headers.indexOfFirst { it in listOf("saleprice", "precioventa", "venta") }
        val colStock = headers.indexOfFirst { it in listOf("stock", "cantidad", "qty") }

        if (colCode < 0) return ImportResult(0, 0, listOf("Columna 'code' o 'codigo' no encontrada en el header"))
        if (colName < 0) return ImportResult(0, 0, listOf("Columna 'name' o 'nombre' no encontrada en el header"))
        if (colPrice < 0) return ImportResult(0, 0, listOf("Columna de precio no encontrada en el header (purchasePrice/precio/costo)"))

        var created = 0
        var updated = 0
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val rowNum = idx + (if (hasHeader) 2 else 1)
            try {
                val cols = parseCsvLine(line)

                val code = cols.getOrNull(colCode)?.trim()
                if (code.isNullOrBlank()) { errors.add("Fila $rowNum: codigo vacio"); return@forEachIndexed }

                val name = cols.getOrNull(colName)?.trim()
                if (name.isNullOrBlank()) { errors.add("Fila $rowNum: nombre vacio"); return@forEachIndexed }

                val priceStr = cols.getOrNull(colPrice)?.trim()?.replace("$", "")?.replace(",", ".")
                val purchasePrice = priceStr?.toBigDecimalOrNull()
                if (purchasePrice == null) { errors.add("Fila $rowNum: precio invalido '$priceStr'"); return@forEachIndexed }

                val currencyStr = cols.getOrNull(colCurrency)?.trim()?.uppercase() ?: "ARS"
                val currency = try { Currency.valueOf(currencyStr) } catch (_: Exception) {
                    errors.add("Fila $rowNum: moneda invalida '$currencyStr' (usar USD o ARS)"); return@forEachIndexed
                }

                val description = cols.getOrNull(colDescription)?.trim() ?: ""
                val stock = cols.getOrNull(colStock)?.trim()?.toIntOrNull()

                val salePriceStr = cols.getOrNull(colSalePrice)?.trim()?.replace("$", "")?.replace(",", ".")
                val salePrice = salePriceStr?.toBigDecimalOrNull()
                    ?: run {
                        if (currency == Currency.USD && dollarRate == null) {
                            errors.add("Fila $rowNum: producto en USD pero no hay cotizacion del dolar configurada")
                            return@forEachIndexed
                        }
                        Product.defaultSalePrice(purchasePrice, currency, dollarRate ?: BigDecimal.ONE)
                    }

                val existing = productRepo.findByCode(code)
                if (existing != null) {
                    productRepo.update(existing.copy(
                        name = name,
                        description = description.ifEmpty { existing.description },
                        purchasePrice = purchasePrice,
                        purchaseCurrency = currency,
                        salePrice = salePrice,
                        stock = stock ?: existing.stock
                    ))
                    updated++
                } else {
                    productRepo.insert(Product(
                        code = code,
                        name = name,
                        description = description,
                        purchasePrice = purchasePrice,
                        purchaseCurrency = currency,
                        salePrice = salePrice,
                        stock = stock ?: 0
                    ))
                    created++
                }
            } catch (e: Exception) {
                errors.add("Fila $rowNum: ${e.message}")
            }
        }

        return ImportResult(created, updated, errors)
    }

    /** Simple CSV line parser that handles quoted fields */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        val sep = if (line.contains(';') && !line.contains(',')) ';' else ','

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
