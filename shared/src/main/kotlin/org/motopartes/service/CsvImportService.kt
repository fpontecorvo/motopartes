package org.motopartes.service

import org.motopartes.model.Currency
import org.motopartes.model.Product
import org.motopartes.repository.DollarRateRepository
import org.motopartes.repository.ProductRepository
import java.math.BigDecimal

/** A product from the invoice CSV that doesn't exist in the DB yet */
data class MissingProduct(val code: String, val name: String, val invoiceUnitCost: BigDecimal, val quantity: Int)

data class PurchaseInvoiceResult(
    val items: List<PurchaseItem>,
    val missingProducts: List<MissingProduct>,
    val errors: List<String>
)

data class ImportResult(
    val created: Int,
    val updated: Int,
    val skipped: Int = 0,
    val errors: List<String> = emptyList()
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (created > 0) parts.add("$created creados")
        if (updated > 0) parts.add("$updated actualizados")
        if (skipped > 0) parts.add("$skipped omitidos")
        if (errors.isNotEmpty()) parts.add("${errors.size} errores")
        return parts.joinToString(", ").ifEmpty { "Sin cambios" }
    }
}

class CsvImportService(
    private val productRepo: ProductRepository,
    private val dollarRateRepo: DollarRateRepository
) {

    fun import(csvContent: String, hasHeader: Boolean = true): ImportResult {
        val normalized = normalizeCsvContent(csvContent)
        val lines = normalized.trimEnd().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(0, 0, errors = listOf("Archivo vacio"))

        val headerLine = if (hasHeader) lines.first() else ""
        val dataLines = if (hasHeader) lines.drop(1) else lines

        return if (isSupplierFormat(headerLine)) {
            importSupplierFormat(dataLines)
        } else {
            importGenericFormat(headerLine, dataLines, hasHeader)
        }
    }

    private fun isSupplierFormat(header: String): Boolean {
        val h = header.lowercase()
        return h.contains("articulo") && h.contains("descripcion") && (h.contains("u\$s") || h.contains("dólar") || h.contains("dolar"))
    }

    private fun importSupplierFormat(dataLines: List<String>): ImportResult {
        var skipped = 0
        val errors = mutableListOf<String>()
        val productsToUpsert = mutableListOf<Product>()

        dataLines.forEachIndexed { idx, line ->
            val rowNum = idx + 2
            try {
                val cols = parseCsvLine(line)
                val code = cols.getOrNull(0)?.trim() ?: return@forEachIndexed
                val name = cols.getOrNull(1)?.trim() ?: return@forEachIndexed

                if (code.isBlank() || name.isBlank()) return@forEachIndexed
                if (!code.contains("/")) { skipped++; return@forEachIndexed }

                val usdStr = cols.getOrNull(2)?.trim()?.replace(",", ".") ?: "0.00"
                val arsStr = cols.getOrNull(3)?.trim()?.replace(",", ".") ?: "0.00"
                val usdPrice = usdStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val arsPrice = arsStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

                val (purchasePrice, currency) = when {
                    usdPrice > BigDecimal.ZERO -> usdPrice to Currency.USD
                    arsPrice > BigDecimal.ZERO -> arsPrice to Currency.ARS
                    else -> { skipped++; return@forEachIndexed }
                }

                productsToUpsert.add(Product(code = code, name = name, purchasePrice = purchasePrice, purchaseCurrency = currency))
            } catch (e: Exception) {
                errors.add("Fila $rowNum: ${e.message}")
            }
        }

        val (created, updated) = productRepo.upsertBatch(productsToUpsert)
        return ImportResult(created, updated, skipped, errors)
    }

    private fun importGenericFormat(headerLine: String, dataLines: List<String>, hasHeader: Boolean): ImportResult {
        val headers = if (hasHeader) headerLine.split(",", ";").map { it.trim().lowercase() } else emptyList()
        val colCode = headers.indexOfFirst { it in listOf("code", "codigo", "cod", "articulo") }
        val colName = headers.indexOfFirst { it in listOf("name", "nombre", "descripcion") }
        val colPrice = headers.indexOfFirst { it in listOf("purchaseprice", "precio", "price", "preciocompra", "costo", "cost") }
        val colCurrency = headers.indexOfFirst { it in listOf("purchasecurrency", "moneda", "currency", "mon") }
        val colDescription = headers.indexOfFirst { it in listOf("description", "descripcion", "desc") }
        val colStock = headers.indexOfFirst { it in listOf("stock", "cantidad", "qty") }

        if (colCode < 0) return ImportResult(0, 0, errors = listOf("Columna 'code' o 'codigo' no encontrada en el header"))
        if (colName < 0) return ImportResult(0, 0, errors = listOf("Columna 'name' o 'nombre' no encontrada en el header"))
        if (colPrice < 0) return ImportResult(0, 0, errors = listOf("Columna de precio no encontrada en el header (purchasePrice/precio/costo)"))

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

                val existing = productRepo.findByCode(code)
                if (existing != null) {
                    productRepo.update(existing.copy(
                        name = name,
                        description = description.ifEmpty { existing.description },
                        purchasePrice = purchasePrice,
                        purchaseCurrency = currency,
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
                        stock = stock ?: 0
                    ))
                    created++
                }
            } catch (e: Exception) {
                errors.add("Fila $rowNum: ${e.message}")
            }
        }

        return ImportResult(created, updated, errors = errors)
    }

    fun importPurchaseInvoice(csvContent: String): PurchaseInvoiceResult {
        val normalized = normalizeCsvContent(csvContent)
        val lines = normalized.trimEnd().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return PurchaseInvoiceResult(emptyList(), emptyList(), listOf("Archivo vacio"))

        val dataLines = lines.drop(1)
        val items = mutableListOf<PurchaseItem>()
        val missing = mutableListOf<MissingProduct>()
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val rowNum = idx + 2
            try {
                val cols = parseCsvLine(line)
                val code = cols.getOrNull(0)?.trim() ?: return@forEachIndexed
                if (code.isBlank()) return@forEachIndexed

                val name = cols.getOrNull(1)?.trim() ?: ""

                val quantity = cols.getOrNull(2)?.trim()?.toIntOrNull()
                if (quantity == null || quantity <= 0) { errors.add("Fila $rowNum: cantidad invalida para '$code'"); return@forEachIndexed }

                val unitPriceStr = cols.getOrNull(3)?.trim() ?: ""
                val unitPrice = parseArgentinePrice(unitPriceStr)
                if (unitPrice == null || unitPrice <= BigDecimal.ZERO) { errors.add("Fila $rowNum: precio invalido '$unitPriceStr' para '$code'"); return@forEachIndexed }

                val product = productRepo.findByCode(code)
                if (product == null) {
                    missing.add(MissingProduct(code, name, unitPrice, quantity))
                    return@forEachIndexed
                }

                items.add(PurchaseItem(product.id, quantity, unitPrice))
            } catch (e: Exception) {
                errors.add("Fila $rowNum: ${e.message}")
            }
        }

        return PurchaseInvoiceResult(items, missing, errors)
    }

    private fun parseArgentinePrice(raw: String): BigDecimal? {
        val cleaned = raw.replace("$", "").replace(" ", "").trim()
        val normalized = if (cleaned.contains('.') && cleaned.contains(',')) {
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                cleaned.replace(".", "").replace(",", ".")
            } else {
                cleaned.replace(",", "")
            }
        } else if (cleaned.contains(',') && !cleaned.contains('.')) {
            val afterComma = cleaned.substringAfter(',')
            if (afterComma.length == 3) cleaned.replace(",", "") else cleaned.replace(",", ".")
        } else {
            cleaned
        }
        return normalized.toBigDecimalOrNull()
    }

    private fun normalizeCsvContent(content: String): String {
        val result = StringBuilder()
        var inQuotes = false
        for (ch in content) {
            when {
                ch == '"' -> { inQuotes = !inQuotes; result.append(ch) }
                ch == '\n' && inQuotes -> result.append(' ')
                else -> result.append(ch)
            }
        }
        return result.toString()
    }

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
