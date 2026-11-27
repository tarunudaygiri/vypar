package com.example.printing

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.db.*
import com.example.util.Formatters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfPrintEngine(private val context: Context) {

    companion object {
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
    }

    private fun resolveTypeface(fontFamily: String, bold: Boolean = false): Typeface {
        val resId = when {
            fontFamily.contains("Telugu", ignoreCase = true) -> R.font.noto_sans_telugu
            fontFamily.contains("Devanagari", ignoreCase = true) || fontFamily.contains("Hindi", ignoreCase = true) -> R.font.noto_sans_devanagari
            fontFamily.contains("Jakarta", ignoreCase = true) -> R.font.plus_jakarta_sans
            else -> R.font.noto_sans_telugu // Default supports Telugu + Latin numerals/English cleanly!
        }
        val base = runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: Typeface.SANS_SERIF
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    /**
     * Generates an A4 PDF for an Invoice supporting:
     * - 1 bill per A4 page (Full A4)
     * - 2 identical bills per A4 page (Top/Bottom with cutting guide, NO copy labels)
     * - 4 identical bills per A4 page (2x2 grid with cutting guides, NO copy labels)
     */
    fun generateInvoicePdf(
        business: BusinessEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        template: PrintTemplateEntity,
        billsPerPageOverride: Int? = null
    ): File {
        val copiesPerPage = (billsPerPageOverride ?: template.billsPerPage).coerceIn(1, 4)
        val doc = PdfDocument()
        val regularTf = resolveTypeface(template.fontFamily, bold = false)
        val boldTf = resolveTypeface(template.fontFamily, bold = true)

        when (copiesPerPage) {
            4 -> renderQuadA4Invoice(doc, business, invoice, items, template, regularTf, boldTf)
            2 -> renderTwoUpA4Invoice(doc, business, invoice, items, template, regularTf, boldTf)
            else -> renderFullA4Invoice(doc, business, invoice, items, template, regularTf, boldTf)
        }

        return savePdf(doc, "${invoice.invoiceNumber}_${copiesPerPage}up.pdf")
    }

    private fun renderFullA4Invoice(
        doc: PdfDocument,
        business: BusinessEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        template: PrintTemplateEntity,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        val margin = (template.marginMm * 2.83f).coerceIn(18f, 48f)
        val itemsPerPage = 18
        val chunks = if (items.isEmpty()) listOf(emptyList()) else items.chunked(itemsPerPage)
        chunks.forEachIndexed { pageIdx, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val bounds = RectF(margin, margin, A4_WIDTH - margin, A4_HEIGHT - margin)
            drawBillInRect(
                canvas = page.canvas,
                bounds = bounds,
                business = business,
                invoice = invoice,
                items = chunk,
                template = template,
                regularTf = regularTf,
                boldTf = boldTf,
                scale = 1.0f,
                showTotals = (pageIdx == chunks.lastIndex),
                pageLabel = if (chunks.size > 1) "${pageIdx + 1}/${chunks.size}" else ""
            )
            doc.finishPage(page)
        }
    }

    private fun renderTwoUpA4Invoice(
        doc: PdfDocument,
        business: BusinessEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        template: PrintTemplateEntity,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        // Split cleanly into continuation pages if too many items (> 10)
        val chunks = if (items.isEmpty()) listOf(emptyList()) else items.chunked(10)
        val halfH = A4_HEIGHT / 2f
        val pad = (template.marginMm * 1.8f).coerceIn(12f, 24f)

        chunks.forEachIndexed { idx, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val topRect = RectF(pad, pad, A4_WIDTH - pad, halfH - pad * 0.7f)
            val botRect = RectF(pad, halfH + pad * 0.7f, A4_WIDTH - pad, A4_HEIGHT - pad)
            val showTotals = idx == chunks.lastIndex
            val pageLabel = if (chunks.size > 1) "${idx + 1}/${chunks.size}" else ""

            // Identical bills — strictly NO Original / Duplicate / Copy labels
            drawBillInRect(canvas, topRect, business, invoice, chunk, template, regularTf, boldTf, 0.84f, showTotals, pageLabel)
            drawBillInRect(canvas, botRect, business, invoice, chunk, template, regularTf, boldTf, 0.84f, showTotals, pageLabel)

            if (template.showCuttingGuides) {
                val guidePaint = cuttingGuidePaint()
                canvas.drawLine(8f, halfH, A4_WIDTH - 8f, halfH, guidePaint)
            }
            doc.finishPage(page)
        }
    }

    private fun renderQuadA4Invoice(
        doc: PdfDocument,
        business: BusinessEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        template: PrintTemplateEntity,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        // 4 identical bills on one A4 sheet (2x2 grid). Overflow safely to continuation sheets if > 8 items!
        val chunks = if (items.isEmpty()) listOf(emptyList()) else items.chunked(8)
        val halfW = A4_WIDTH / 2f
        val halfH = A4_HEIGHT / 2f
        val pad = (template.marginMm * 1.2f).coerceIn(8f, 16f)

        chunks.forEachIndexed { idx, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val rects = listOf(
                RectF(pad, pad, halfW - pad * 0.7f, halfH - pad * 0.7f),
                RectF(halfW + pad * 0.7f, pad, A4_WIDTH - pad, halfH - pad * 0.7f),
                RectF(pad, halfH + pad * 0.7f, halfW - pad * 0.7f, A4_HEIGHT - pad),
                RectF(halfW + pad * 0.7f, halfH + pad * 0.7f, A4_WIDTH - pad, A4_HEIGHT - pad)
            )
            val showTotals = idx == chunks.lastIndex
            val pageLabel = if (chunks.size > 1) "${idx + 1}/${chunks.size}" else ""

            // Draw four completely identical bills with zero Original / Duplicate / Triplicate / Copy labels
            rects.forEach { r ->
                drawBillInRect(canvas, r, business, invoice, chunk, template, regularTf, boldTf, 0.64f, showTotals, pageLabel)
            }

            if (template.showCuttingGuides) {
                val guidePaint = cuttingGuidePaint()
                canvas.drawLine(6f, halfH, A4_WIDTH - 6f, halfH, guidePaint)
                canvas.drawLine(halfW, 6f, halfW, A4_HEIGHT - 6f, guidePaint)
            }
            doc.finishPage(page)
        }
    }

    private fun cuttingGuidePaint(): Paint = Paint().apply {
        color = Color.parseColor("#9CA3AF")
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
    }

    private fun drawBillInRect(
        canvas: Canvas,
        bounds: RectF,
        business: BusinessEntity,
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        template: PrintTemplateEntity,
        regularTf: Typeface,
        boldTf: Typeface,
        scale: Float,
        showTotals: Boolean,
        pageLabel: String
    ) {
        val baseFont = (template.fontSizeSp.coerceIn(7f, 13f)) * scale
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F5132")
            textSize = baseFont * 1.35f
            typeface = boldTf
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = baseFont
            typeface = boldTf
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = baseFont * 0.94f
            typeface = regularTf
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5563")
            textSize = baseFont * 0.82f
            typeface = regularTf
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.7f
            style = Paint.Style.STROKE
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#ECFDF5")
            style = Paint.Style.FILL
        }

        if (template.showBorders) {
            canvas.drawRect(bounds, borderPaint)
        }

        val innerPad = 7f * scale
        val left = bounds.left + innerPad
        val right = bounds.right - innerPad
        val width = right - left
        var y = bounds.top + innerPad + titlePaint.textSize

        // Header banner
        canvas.drawText(fitText(business.name, titlePaint, width), left, y, titlePaint)
        y += baseFont * 1.15f
        if (template.headerText.isNotBlank()) {
            canvas.drawText(fitText(template.headerText, mutedPaint, width), left, y, mutedPaint)
            y += baseFont * 1.05f
        }
        val bizContact = buildString {
            if (business.address.isNotBlank()) append(business.address)
            if (business.mobile.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("Ph: ${business.mobile}")
            }
            if (template.showGstDetails && business.gstin.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("GSTIN: ${business.gstin}")
            }
        }
        if (bizContact.isNotBlank()) {
            canvas.drawText(fitText(bizContact, mutedPaint, width), left, y, mutedPaint)
            y += baseFont * 1.15f
        }

        // Invoice title & number strip (NO Original/Duplicate/Copy labels!)
        val stripTop = y - baseFont * 0.7f
        val stripBot = y + baseFont * 0.65f
        canvas.drawRect(left, stripTop, right, stripBot, headerBgPaint)
        val titleStr = buildString {
            append(template.invoiceTitle.ifBlank { "WHOLESALE BILL" })
            if (pageLabel.isNotBlank()) append(" ($pageLabel)")
        }
        canvas.drawText(fitText(titleStr, boldPaint, width * 0.52f), left + 3f, y + baseFont * 0.22f, boldPaint)
        val invMeta = "${invoice.invoiceNumber}  |  ${Formatters.formatDate(invoice.invoiceDate)}"
        val metaW = boldPaint.measureText(invMeta)
        canvas.drawText(invMeta, (right - metaW - 3f).coerceAtLeast(left + width * 0.45f), y + baseFont * 0.22f, boldPaint)
        y = stripBot + baseFont * 1.15f

        // Customer snapshot
        val custTitle = "${invoice.shopNameSnapshot} (${invoice.customerNameSnapshot})"
        canvas.drawText(fitText(custTitle, boldPaint, width), left, y, boldPaint)
        y += baseFont * 1.05f
        val custSub = buildString {
            if (template.showCustomerMobile && invoice.customerMobileSnapshot.isNotBlank()) {
                append("Mob: ${invoice.customerMobileSnapshot}")
            }
            if (template.showCustomerAddress && invoice.customerAddressSnapshot.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append(invoice.customerAddressSnapshot)
            }
        }
        if (custSub.isNotBlank()) {
            canvas.drawText(fitText(custSub, mutedPaint, width), left, y, mutedPaint)
            y += baseFont * 1.1f
        }

        // Table Header
        val colItem = left
        val colUnit = left + width * 0.44f
        val colQty = left + width * 0.60f
        val colRate = left + width * 0.73f
        val colAmt = right

        canvas.drawLine(left, y, right, y, borderPaint)
        y += baseFont * 1.05f
        canvas.drawText("Product / Option", colItem + 2f, y, boldPaint)
        canvas.drawText("Unit", colUnit, y, boldPaint)
        canvas.drawText("Qty", colQty, y, boldPaint)
        canvas.drawText("Rate", colRate, y, boldPaint)
        drawRightAligned(canvas, "Amount", colAmt - 2f, y, boldPaint)
        y += baseFont * 0.45f
        canvas.drawLine(left, y, right, y, borderPaint)

        // Table rows
        val rowStep = (baseFont * 1.28f) + (template.rowSpacingDp * scale * 0.55f)
        for (item in items) {
            y += rowStep
            if (y > bounds.bottom - baseFont * 6f) break
            val label = "${item.productNameSnapshot} (${item.varietyNameSnapshot})"
            canvas.drawText(fitText(label, bodyPaint, colUnit - colItem - 4f), colItem + 2f, y, bodyPaint)
            canvas.drawText(fitText(item.sellingUnitSnapshot, bodyPaint, colQty - colUnit - 2f), colUnit, y, bodyPaint)
            canvas.drawText(Formatters.formatQty(item.quantity), colQty, y, bodyPaint)
            canvas.drawText(Formatters.formatQty(item.rate), colRate, y, bodyPaint)
            drawRightAligned(canvas, Formatters.formatRupee(item.lineAmount), colAmt - 2f, y, bodyPaint)
            if (template.showBorders) {
                canvas.drawLine(left, y + baseFont * 0.35f, right, y + baseFont * 0.35f, borderPaint)
            }
        }

        if (showTotals) {
            y += baseFont * 1.2f
            canvas.drawLine(left, y - baseFont * 0.7f, right, y - baseFont * 0.7f, borderPaint)

            // Unit summary
            if (template.showUnitSummary) {
                val parts = mutableListOf<String>()
                if (invoice.totalKg > 0) parts.add("KG: ${Formatters.formatQty(invoice.totalKg)}")
                if (invoice.totalBags > 0) parts.add("Bags: ${Formatters.formatQty(invoice.totalBags)}")
                if (invoice.totalPackets > 0) parts.add("Pkts: ${Formatters.formatQty(invoice.totalPackets)}")
                if (invoice.totalBoxes > 0) parts.add("Boxes: ${Formatters.formatQty(invoice.totalBoxes)}")
                if (invoice.totalPieces > 0) parts.add("Pcs: ${Formatters.formatQty(invoice.totalPieces)}")
                if (parts.isNotEmpty()) {
                    canvas.drawText(fitText(parts.joinToString(" | "), mutedPaint, width * 0.56f), left, y, mutedPaint)
                }
            }

            // Totals on right side
            var rightY = y
            if (invoice.discountAmount > 0 || invoice.gstEnabled || invoice.additionalCharges > 0) {
                drawRightAligned(canvas, "Subtotal: ${Formatters.formatRupee(invoice.subtotal)}", right, rightY, bodyPaint)
                rightY += baseFont * 1.05f
                if (invoice.discountAmount > 0) {
                    drawRightAligned(canvas, "Discount: -${Formatters.formatRupee(invoice.discountAmount)}", right, rightY, bodyPaint)
                    rightY += baseFont * 1.05f
                }
                if (invoice.gstEnabled && template.showGstDetails) {
                    drawRightAligned(canvas, "GST (${Formatters.formatQty(invoice.gstPercent)}%): ${Formatters.formatRupee(invoice.gstAmount)}", right, rightY, bodyPaint)
                    rightY += baseFont * 1.05f
                }
                if (invoice.additionalCharges > 0) {
                    drawRightAligned(canvas, "Charges: ${Formatters.formatRupee(invoice.additionalCharges)}", right, rightY, bodyPaint)
                    rightY += baseFont * 1.05f
                }
            }
            val totalLabel = "TOTAL: ${Formatters.formatRupee(invoice.grandTotal)}"
            drawRightAligned(canvas, totalLabel, right, rightY + baseFont * 0.2f, titlePaint)

            if (template.showPaymentInfo) {
                val payLine = "Status: ${invoice.paymentStatus}  |  Paid: ${Formatters.formatRupee(invoice.amountPaid)}  |  Bal: ${Formatters.formatRupee(invoice.remainingBalance)}"
                val payY = maxOf(y + baseFont * 1.25f, rightY + baseFont * 1.35f)
                canvas.drawText(fitText(payLine, boldPaint, width), left, payY, boldPaint)
                y = payY + baseFont * 1.15f
            } else {
                y = rightY + baseFont * 1.2f
            }

            // Footer & Terms at bottom of rect
            val footerY = bounds.bottom - innerPad - baseFont * 0.3f
            if (template.footerText.isNotBlank() && footerY > y) {
                canvas.drawText(fitText(template.footerText, mutedPaint, width), left, footerY, mutedPaint)
            }
            if (template.termsText.isNotBlank() && footerY - baseFont * 1.05f > y) {
                canvas.drawText(fitText(template.termsText, mutedPaint, width), left, footerY - baseFont * 1.05f, mutedPaint)
            }
        }
    }

    fun generateCustomerHistoryPdf(
        business: BusinessEntity,
        customer: CustomerEntity,
        invoices: List<InvoiceEntity>,
        orders: List<OrderEntity>,
        payments: List<PaymentEntity>,
        ledger: List<LedgerEntryEntity>,
        dateLabel: String
    ): File {
        val doc = PdfDocument()
        val regularTf = resolveTypeface("Noto Sans Telugu", false)
        val boldTf = resolveTypeface("Noto Sans Telugu", true)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F5132"); textSize = 15f; typeface = boldTf }
        val h2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827"); textSize = 11.5f; typeface = boldTf }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); textSize = 9.5f; typeface = regularTf }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4B5563"); textSize = 8.5f; typeface = regularTf }
        val linePaint = Paint().apply { color = Color.parseColor("#CBD5E1"); strokeWidth = 0.8f }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
        var canvas = page.canvas
        val left = 32f
        val right = A4_WIDTH - 32f
        val width = right - left
        var y = 42f

        fun ensureSpace(needed: Float) {
            if (y + needed > A4_HEIGHT - 36f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = 42f
            }
        }

        // Header
        canvas.drawText(fitText(business.name, titlePaint, width), left, y, titlePaint)
        y += 16f
        canvas.drawText(fitText("Customer Statement & History  |  Period: $dateLabel", h2Paint, width), left, y, h2Paint)
        y += 14f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 16f

        // Customer details
        canvas.drawText(fitText("${customer.shopName} — ${customer.name}", h2Paint, width), left, y, h2Paint)
        y += 14f
        canvas.drawText(fitText("Mobile: ${customer.mobile}  |  Address: ${customer.address.ifBlank { "-" }}", bodyPaint, width), left, y, bodyPaint)
        y += 16f

        val totalPurchased = invoices.filter { it.status != "CANCELLED" }.sumOf { it.grandTotal }
        val totalPaid = payments.sumOf { it.amountReceived }
        val outstanding = ledger.lastOrNull()?.runningBalance ?: (totalPurchased - totalPaid)
        canvas.drawText("Total Bills: ${invoices.size}   |   Orders: ${orders.size}   |   Total Purchase: ${Formatters.formatRupee(totalPurchased)}", left, y, bodyPaint)
        y += 14f
        canvas.drawText("Total Paid: ${Formatters.formatRupee(totalPaid)}   |   Outstanding Balance: ${Formatters.formatRupee(outstanding)}", left, y, h2Paint)
        y += 16f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 18f

        // Ledger table
        canvas.drawText("CUSTOMER LEDGER", left, y, h2Paint)
        y += 14f
        canvas.drawText("Date", left, y, h2Paint)
        canvas.drawText("Ref / Description", left + 75f, y, h2Paint)
        drawRightAligned(canvas, "Debit", right - 150f, y, h2Paint)
        drawRightAligned(canvas, "Credit", right - 75f, y, h2Paint)
        drawRightAligned(canvas, "Balance", right, y, h2Paint)
        y += 6f
        canvas.drawLine(left, y, right, y, linePaint)

        for (entry in ledger) {
            ensureSpace(18f)
            y += 14f
            canvas.drawText(Formatters.formatShortDate(entry.entryDate), left, y, bodyPaint)
            canvas.drawText(fitText("${entry.referenceNumber} ${entry.description}", bodyPaint, width - 265f), left + 75f, y, bodyPaint)
            drawRightAligned(canvas, if (entry.debitAmount > 0) Formatters.formatRupee(entry.debitAmount) else "—", right - 150f, y, bodyPaint)
            drawRightAligned(canvas, if (entry.creditAmount > 0) Formatters.formatRupee(entry.creditAmount) else "—", right - 75f, y, bodyPaint)
            drawRightAligned(canvas, Formatters.formatRupee(entry.runningBalance), right, y, bodyPaint)
        }

        if (orders.isNotEmpty()) {
            ensureSpace(40f)
            y += 22f
            canvas.drawLine(left, y, right, y, linePaint)
            y += 16f
            canvas.drawText("RECENT ORDERS (${orders.size})", left, y, h2Paint)
            for (ord in orders) {
                ensureSpace(18f)
                y += 14f
                canvas.drawText("${ord.orderNumber} (${Formatters.formatShortDate(ord.orderDate)}) — ${ord.status} — Est. ${Formatters.formatRupee(ord.totalEstimatedAmount)}", left, y, mutedPaint)
            }
        }

        doc.finishPage(page)
        return savePdf(doc, "Customer_${customer.id}_History.pdf")
    }

    fun generatePaymentReceiptPdf(
        business: BusinessEntity,
        payment: PaymentEntity,
        remainingCustomerBalance: Double
    ): File {
        val doc = PdfDocument()
        val regularTf = resolveTypeface("Noto Sans Telugu", false)
        val boldTf = resolveTypeface("Noto Sans Telugu", true)
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
        val canvas = page.canvas

        val box = RectF(36f, 36f, A4_WIDTH - 36f, 380f)
        val border = Paint().apply { color = Color.parseColor("#0F5132"); strokeWidth = 1.4f; style = Paint.Style.STROKE }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F5132"); textSize = 16f; typeface = boldTf }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827"); textSize = 11f; typeface = boldTf }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); textSize = 10.5f; typeface = regularTf }

        canvas.drawRect(box, border)
        val left = box.left + 18f
        val right = box.right - 18f
        var y = box.top + 30f

        canvas.drawText(business.name, left, y, titlePaint)
        y += 18f
        canvas.drawText("${business.address}  |  Ph: ${business.mobile}", left, y, bodyPaint)
        y += 22f
        canvas.drawText("PAYMENT RECEIPT", left, y, titlePaint)
        drawRightAligned(canvas, "Receipt No: ${payment.receiptNumber}", right, y, boldPaint)
        y += 22f
        canvas.drawText("Date: ${Formatters.formatDate(payment.paymentDate)}", left, y, bodyPaint)
        y += 20f
        canvas.drawText("Received From: ${payment.shopNameSnapshot} (${payment.customerNameSnapshot})", left, y, boldPaint)
        y += 22f
        canvas.drawText("Amount Received: ${Formatters.formatRupee(payment.amountReceived)}", left, y, titlePaint)
        y += 20f
        canvas.drawText("Payment Mode (Manual Record): ${payment.paymentMethod}", left, y, bodyPaint)
        y += 18f
        if (payment.referenceNumber.isNotBlank()) {
            canvas.drawText("Reference No.: ${payment.referenceNumber}", left, y, bodyPaint)
            y += 18f
        }
        if (payment.relatedInvoiceNumber.isNotBlank()) {
            canvas.drawText("Related Bill(s): ${payment.relatedInvoiceNumber}", left, y, bodyPaint)
            y += 18f
        }
        if (payment.unallocatedAdvance > 0.01) {
            canvas.drawText("Unallocated Advance: ${Formatters.formatRupee(payment.unallocatedAdvance)}", left, y, bodyPaint)
            y += 18f
        }
        canvas.drawText("Remaining Customer Balance: ${Formatters.formatRupee(remainingCustomerBalance)}", left, y, boldPaint)
        y += 24f
        if (payment.notes.isNotBlank()) {
            canvas.drawText("Notes: ${payment.notes}", left, y, bodyPaint)
        }
        drawRightAligned(canvas, "Authorized Signature", right, box.bottom - 18f, bodyPaint)

        doc.finishPage(page)
        return savePdf(doc, "Receipt_${payment.receiptNumber}.pdf")
    }

    fun generateOrderSlipPdf(
        business: BusinessEntity,
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): File {
        val doc = PdfDocument()
        val regularTf = resolveTypeface("Noto Sans Telugu", false)
        val boldTf = resolveTypeface("Noto Sans Telugu", true)
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
        val canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F5132"); textSize = 15f; typeface = boldTf }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827"); textSize = 11f; typeface = boldTf }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); textSize = 10f; typeface = regularTf }
        val linePaint = Paint().apply { color = Color.parseColor("#CBD5E1"); strokeWidth = 0.8f }

        val left = 36f
        val right = A4_WIDTH - 36f
        val width = right - left
        var y = 44f

        canvas.drawText(business.name, left, y, titlePaint)
        drawRightAligned(canvas, "ORDER SLIP: ${order.orderNumber}", right, y, boldPaint)
        y += 18f
        canvas.drawText("Date: ${Formatters.formatDate(order.orderDate)}  |  Status: ${order.status}  |  Executive: ${order.salesExecutiveName}", left, y, bodyPaint)
        y += 18f
        canvas.drawText("Customer: ${order.shopNameSnapshot} (${order.customerNameSnapshot})  Ph: ${order.customerMobileSnapshot}", left, y, boldPaint)
        y += 12f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 18f

        canvas.drawText("Product / Selling Option", left, y, boldPaint)
        canvas.drawText("Unit", left + width * 0.52f, y, boldPaint)
        canvas.drawText("Qty", left + width * 0.66f, y, boldPaint)
        drawRightAligned(canvas, "Est. Amount", right, y, boldPaint)
        y += 8f
        canvas.drawLine(left, y, right, y, linePaint)

        for (item in items) {
            y += 18f
            canvas.drawText(fitText("${item.productNameSnapshot} (${item.varietyNameSnapshot})", bodyPaint, width * 0.50f), left, y, bodyPaint)
            canvas.drawText(item.sellingUnitSnapshot, left + width * 0.52f, y, bodyPaint)
            canvas.drawText(Formatters.formatQty(item.quantity), left + width * 0.66f, y, bodyPaint)
            drawRightAligned(canvas, Formatters.formatRupee(item.lineAmount), right, y, bodyPaint)
        }

        y += 16f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 20f
        drawRightAligned(canvas, "Estimated Total: ${Formatters.formatRupee(order.totalEstimatedAmount)}", right, y, titlePaint)
        if (order.notes.isNotBlank()) {
            y += 24f
            canvas.drawText("Notes: ${order.notes}", left, y, bodyPaint)
        }

        doc.finishPage(page)
        return savePdf(doc, "Order_${order.orderNumber}.pdf")
    }

    fun generateReportPdf(business: BusinessEntity, title: String, lines: List<Pair<String, String>>): File {
        val doc = PdfDocument()
        val regularTf = resolveTypeface("Noto Sans Telugu", false)
        val boldTf = resolveTypeface("Noto Sans Telugu", true)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F5132"); textSize = 15f; typeface = boldTf }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827"); textSize = 10.5f; typeface = boldTf }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); textSize = 10f; typeface = regularTf }
        val linePaint = Paint().apply { color = Color.parseColor("#CBD5E1"); strokeWidth = 0.7f }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
        var canvas = page.canvas
        val left = 36f
        val right = A4_WIDTH - 36f
        val width = right - left
        var y = 44f

        canvas.drawText(business.name, left, y, titlePaint)
        y += 18f
        canvas.drawText("$title  |  Generated: ${Formatters.formatDateTime(System.currentTimeMillis())}", left, y, boldPaint)
        y += 12f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 16f

        for ((label, value) in lines) {
            if (y > A4_HEIGHT - 40f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
            if (value.isEmpty()) {
                y += 6f
                canvas.drawText(fitText(label, boldPaint, width), left, y, boldPaint)
                y += 14f
            } else {
                canvas.drawText(fitText(label, bodyPaint, width * 0.65f), left, y, bodyPaint)
                drawRightAligned(canvas, value, right, y, boldPaint)
                y += 16f
            }
        }
        doc.finishPage(page)
        return savePdf(doc, "Report_${System.currentTimeMillis() % 100000}.pdf")
    }

    fun exportCsvFile(fileName: String, headers: List<String>, rows: List<List<String>>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens Telugu/Hindi Unicode cleanly!
        sb.append('\uFEFF')
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun escapeCsv(s: String): String {
        val escaped = s.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun savePdf(doc: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun printPdfDirect(pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, adapter, attrs)
    }

    fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share / Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun drawRightAligned(canvas: Canvas, text: String, rightX: Float, y: Float, paint: Paint) {
        val w = paint.measureText(text)
        canvas.drawText(text, rightX - w, y, paint)
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
