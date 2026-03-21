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

    /**
     * Import products from CSV content.
     *
     * Supports two formats:
     *
     * 1. Supplier format (auto-detected):
     *    Header: Articulo, Descripcion, Lista IMP. U$S DÓLAR, Lista NAC. $ PESOS
     *    - Categories (code without '/') are skipped
     *    - Currency determined by which price column has a value > 0
     *
     * 2. Generic format:
     *    Header: code/codigo, name/nombre, purchasePrice/precio, purchaseCurrency/moneda, ...
     */
    fun import(csvContent: String, hasHeader: Boolean = true): ImportResult {
        // Normalize multiline headers: CSV headers can span lines when quoted
        val normalized = normalizeCsvContent(csvContent)
        val lines = normalized.trimEnd().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(0, 0, errors = listOf("Archivo vacio"))

        val headerLine = if (hasHeader) lines.first() else ""
        val dataLines = if (hasHeader) lines.drop(1) else lines

        // Auto-detect supplier format
        return if (isSupplierFormat(headerLine)) {
            importSupplierFormat(dataLines)
        } else {
            importGenericFormat(headerLine, dataLines, hasHeader)
        }
    }

    /** Detect supplier CSV by header keywords */
    private fun isSupplierFormat(header: String): Boolean {
        val h = header.lowercase()
        return h.contains("articulo") && h.contains("descripcion") && (h.contains("u\$s") || h.contains("dólar") || h.contains("dolar"))
    }

    /** Import from supplier format: Articulo, Descripcion, USD price, ARS price */
    private fun importSupplierFormat(dataLines: List<String>): ImportResult {
        val dollarRate = dollarRateRepo.getLatest()?.rate
        var created = 0
        var updated = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val rowNum = idx + 2
            try {
                val cols = parseCsvLine(line)
                val code = cols.getOrNull(0)?.trim() ?: return@forEachIndexed
                val name = cols.getOrNull(1)?.trim() ?: return@forEachIndexed

                if (code.isBlank() || name.isBlank()) return@forEachIndexed

                // Skip category rows (code without '/')
                if (!code.contains("/")) { skipped++; return@forEachIndexed }

                val usdStr = cols.getOrNull(2)?.trim()?.replace(",", ".") ?: "0.00"
                val arsStr = cols.getOrNull(3)?.trim()?.replace(",", ".") ?: "0.00"
                val usdPrice = usdStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val arsPrice = arsStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

                // Determine currency and price
                val (purchasePrice, currency) = when {
                    usdPrice > BigDecimal.ZERO -> usdPrice to Currency.USD
                    arsPrice > BigDecimal.ZERO -> arsPrice to Currency.ARS
                    else -> { skipped++; return@forEachIndexed } // Both zero, skip
                }

                // Calculate sale price
                if (currency == Currency.USD && dollarRate == null) {
                    errors.add("Fila $rowNum: producto USD '$code' pero no hay cotizacion del dolar")
                    return@forEachIndexed
                }
                val salePrice = Product.defaultSalePrice(purchasePrice, currency, dollarRate ?: BigDecimal.ONE)

                val existing = productRepo.findByCode(code)
                if (existing != null) {
                    productRepo.update(existing.copy(
                        name = name,
                        purchasePrice = purchasePrice,
                        purchaseCurrency = currency,
                        salePrice = salePrice
                    ))
                    updated++
                } else {
                    productRepo.insert(Product(
                        code = code,
                        name = name,
                        purchasePrice = purchasePrice,
                        purchaseCurrency = currency,
                        salePrice = salePrice
                    ))
                    created++
                }
            } catch (e: Exception) {
                errors.add("Fila $rowNum: ${e.message}")
            }
        }

        return ImportResult(created, updated, skipped, errors)
    }

    /** Import from generic CSV format with flexible headers */
    private fun importGenericFormat(headerLine: String, dataLines: List<String>, hasHeader: Boolean): ImportResult {
        val dollarRate = dollarRateRepo.getLatest()?.rate

        val headers = if (hasHeader) headerLine.split(",", ";").map { it.trim().lowercase() } else emptyList()
        val colCode = headers.indexOfFirst { it in listOf("code", "codigo", "cod", "articulo") }
        val colName = headers.indexOfFirst { it in listOf("name", "nombre", "descripcion") }
        val colPrice = headers.indexOfFirst { it in listOf("purchaseprice", "precio", "price", "preciocompra", "costo", "cost") }
        val colCurrency = headers.indexOfFirst { it in listOf("purchasecurrency", "moneda", "currency", "mon") }
        val colDescription = headers.indexOfFirst { it in listOf("description", "descripcion", "desc") }
        val colSalePrice = headers.indexOfFirst { it in listOf("saleprice", "precioventa", "venta") }
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

        return ImportResult(created, updated, errors = errors)
    }

    /**
     * Import purchase invoice from supplier CSV.
     *
     * Format: Codigo, Articulo, Cantidad, Precio Unitario, Importe
     * Prices have format: "$ 3,701.21" (ARS with thousands separator)
     *
     * Returns list of PurchaseItems with the actual invoice unit cost (not the catalog price).
     * Products not found in DB are reported as errors but other items still process.
     */
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

    /** Parse Argentine price format: "$ 3,701.21" or "$3701.21" → BigDecimal */
    private fun parseArgentinePrice(raw: String): BigDecimal? {
        val cleaned = raw
            .replace("$", "")
            .replace(" ", "")
            .trim()
        // Handle Argentine format: 3,701.21 (comma = thousands, dot = decimal)
        // or 3.701,21 (dot = thousands, comma = decimal)
        val normalized = if (cleaned.contains('.') && cleaned.contains(',')) {
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                // Format: 3.701,21 → 3701.21
                cleaned.replace(".", "").replace(",", ".")
            } else {
                // Format: 3,701.21 → 3701.21
                cleaned.replace(",", "")
            }
        } else if (cleaned.contains(',') && !cleaned.contains('.')) {
            // Could be 3,701 (thousands) or 3,50 (decimal)
            val afterComma = cleaned.substringAfter(',')
            if (afterComma.length == 3) cleaned.replace(",", "") // thousands
            else cleaned.replace(",", ".") // decimal
        } else {
            cleaned
        }
        return normalized.toBigDecimalOrNull()
    }

    /**
     * Normalize CSV content: merge multiline quoted headers into single line.
     * Handles headers like: "Lista IMP.\nU$S DÓLAR"
     */
    private fun normalizeCsvContent(content: String): String {
        val result = StringBuilder()
        var inQuotes = false
        for (ch in content) {
            when {
                ch == '"' -> { inQuotes = !inQuotes; result.append(ch) }
                ch == '\n' && inQuotes -> result.append(' ') // Replace newline inside quotes with space
                else -> result.append(ch)
            }
        }
        return result.toString()
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
