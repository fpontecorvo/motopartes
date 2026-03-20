package org.motopartes.desktop.print

import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.motopartes.model.Client
import org.motopartes.model.Order
import org.motopartes.model.Product
import java.awt.Color
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object DocumentGenerator {

    private val TITLE_FONT = Font(Font.HELVETICA, 18f, Font.BOLD)
    private val SUBTITLE_FONT = Font(Font.HELVETICA, 12f, Font.BOLD)
    private val HEADER_FONT = Font(Font.HELVETICA, 10f, Font.BOLD, Color.WHITE)
    private val BODY_FONT = Font(Font.HELVETICA, 10f, Font.NORMAL)
    private val TOTAL_FONT = Font(Font.HELVETICA, 14f, Font.BOLD)
    private val ACCENT_COLOR = Color(0xFF, 0xB7, 0x4D)
    private val HEADER_BG = Color(0x33, 0x33, 0x33)

    fun generateRemito(
        order: Order,
        clientName: String,
        clientInfo: Client?,
        products: Map<Long, Product>
    ): File {
        val file = createTempPdf("remito_${order.id}")
        val doc = Document(PageSize.A4, 40f, 40f, 40f, 40f)
        PdfWriter.getInstance(doc, FileOutputStream(file))
        doc.open()

        // Header
        doc.add(Paragraph("MOTOPARTES", TITLE_FONT))
        doc.add(Paragraph("REMITO #${order.id}", SUBTITLE_FONT).apply { spacingBefore = 8f })
        doc.add(Paragraph(" "))

        // Client info
        doc.add(Paragraph("Cliente: $clientName", BODY_FONT))
        clientInfo?.let {
            if (it.phone.isNotBlank()) doc.add(Paragraph("Tel: ${it.phone}", BODY_FONT))
            if (it.address.isNotBlank()) doc.add(Paragraph("Dir: ${it.address}", BODY_FONT))
        }
        doc.add(Paragraph("Fecha: ${order.createdAt.toString().take(16)}", BODY_FONT))
        doc.add(Paragraph(" "))

        // Items table (remito: no prices)
        val table = PdfPTable(3).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2f, 4f, 1f))
        }
        addHeaderCell(table, "Codigo")
        addHeaderCell(table, "Descripcion")
        addHeaderCell(table, "Cantidad")

        order.items.forEach { item ->
            val product = products[item.productId]
            addBodyCell(table, product?.code ?: "—")
            addBodyCell(table, product?.name ?: "—")
            addBodyCell(table, "${item.quantity}")
        }
        doc.add(table)

        doc.add(Paragraph(" "))
        doc.add(Paragraph("Total de items: ${order.items.sumOf { it.quantity }}", BODY_FONT))
        doc.add(Paragraph(" "))
        doc.add(Paragraph("Firma: ___________________________", BODY_FONT).apply { spacingBefore = 30f })

        doc.close()
        return file
    }

    fun generateFactura(
        order: Order,
        clientName: String,
        clientInfo: Client?,
        products: Map<Long, Product>
    ): File {
        val file = createTempPdf("factura_${order.id}")
        val doc = Document(PageSize.A4, 40f, 40f, 40f, 40f)
        PdfWriter.getInstance(doc, FileOutputStream(file))
        doc.open()

        // Header
        doc.add(Paragraph("MOTOPARTES", TITLE_FONT))
        doc.add(Paragraph("FACTURA #${order.id}", SUBTITLE_FONT).apply { spacingBefore = 8f })
        doc.add(Paragraph(" "))

        // Client info
        doc.add(Paragraph("Cliente: $clientName", BODY_FONT))
        clientInfo?.let {
            if (it.phone.isNotBlank()) doc.add(Paragraph("Tel: ${it.phone}", BODY_FONT))
            if (it.address.isNotBlank()) doc.add(Paragraph("Dir: ${it.address}", BODY_FONT))
        }
        doc.add(Paragraph("Fecha: ${order.createdAt.toString().take(16)}", BODY_FONT))
        doc.add(Paragraph(" "))

        // Items table (factura: with prices)
        val table = PdfPTable(5).apply {
            widthPercentage = 100f
            setWidths(floatArrayOf(2f, 3f, 1f, 1.5f, 1.5f))
        }
        addHeaderCell(table, "Codigo")
        addHeaderCell(table, "Descripcion")
        addHeaderCell(table, "Cant.")
        addHeaderCell(table, "P.Unitario")
        addHeaderCell(table, "Subtotal")

        order.items.forEach { item ->
            val product = products[item.productId]
            addBodyCell(table, product?.code ?: "—")
            addBodyCell(table, product?.name ?: "—")
            addBodyCell(table, "${item.quantity}")
            addBodyCell(table, "$${item.unitPriceArs.toPlainString()}")
            addBodyCell(table, "$${item.subtotalArs.toPlainString()}")
        }
        doc.add(table)

        // Total
        doc.add(Paragraph(" "))
        val totalParagraph = Paragraph("TOTAL: $${order.totalArs.toPlainString()}", TOTAL_FONT)
        totalParagraph.alignment = Element.ALIGN_RIGHT
        doc.add(totalParagraph)

        doc.close()
        return file
    }

    fun savePdfDialog(pdfFile: File, suggestedName: String): Boolean {
        val chooser = JFileChooser().apply {
            dialogTitle = "Guardar PDF"
            selectedFile = File(suggestedName)
            fileFilter = FileNameExtensionFilter("PDF", "pdf")
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val dest = chooser.selectedFile.let {
                if (it.extension != "pdf") File("${it.absolutePath}.pdf") else it
            }
            pdfFile.copyTo(dest, overwrite = true)
            true
        } else false
    }

    fun printPdf(pdfFile: File) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().print(pdfFile)
        }
    }

    private fun createTempPdf(prefix: String): File {
        val file = File.createTempFile(prefix, ".pdf")
        file.deleteOnExit()
        return file
    }

    private fun addHeaderCell(table: PdfPTable, text: String) {
        table.addCell(PdfPCell(Phrase(text, HEADER_FONT)).apply {
            backgroundColor = HEADER_BG
            horizontalAlignment = Element.ALIGN_CENTER
            paddingBottom = 6f
            paddingTop = 6f
        })
    }

    private fun addBodyCell(table: PdfPTable, text: String) {
        table.addCell(PdfPCell(Phrase(text, BODY_FONT)).apply {
            paddingBottom = 5f
            paddingTop = 5f
        })
    }
}
