package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DateFilter
import com.example.data.model.InvoiceStatus
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.StockMovementType
import com.example.i18n.LocalStrings
import com.example.ui.components.DateFilterBar
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun ReportsScreen(
    state: AppUiState,
    onPrintReport: (title: String, lines: List<Pair<String, String>>, shareOnly: Boolean) -> Unit,
    onExportCsv: (fileName: String, headers: List<String>, rows: List<List<String>>) -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 Sales, 1 Payments, 2 Inventory, 3 Orders, 4 Financial
    var filter by remember { mutableStateOf(DateFilter.THIS_MONTH) }

    val (startMs, endMs) = remember(filter) { Formatters.dateRangeFor(filter) }

    val activeInvoices = remember(state.invoices, startMs, endMs) {
        state.invoices.filter { it.status != InvoiceStatus.CANCELLED.name && it.invoiceDate in startMs..endMs }
    }
    val periodPayments = remember(state.payments, startMs, endMs) {
        state.payments.filter { it.paymentDate in startMs..endMs }
    }
    val periodOrders = remember(state.orders, startMs, endMs) {
        state.orders.filter { it.orderDate in startMs..endMs }
    }
    val periodPurchases = remember(state.purchases, startMs, endMs) {
        state.purchases.filter { it.purchaseDate in startMs..endMs }
    }

    val invIds = remember(activeInvoices) { activeInvoices.map { it.id }.toSet() }
    val periodInvItems = remember(state.invoiceItems, invIds) { state.invoiceItems.filter { it.invoiceId in invIds } }

    val tabs = listOf(s.salesReport, s.paymentReport, s.inventoryReport, s.orderReport, s.financialReport)

    fun buildReportLines(): Pair<String, List<Pair<String, String>>> {
        val periodName = s.dateFilterLabel(filter)
        return when (activeTab) {
            0 -> {
                val byProduct = periodInvItems.groupBy { it.productNameSnapshot }.map { (k, v) -> k to Formatters.formatRupee(v.sumOf { it.lineAmount }) }
                val byCustomer = activeInvoices.groupBy { it.shopNameSnapshot }.map { (k, v) -> k to Formatters.formatRupee(v.sumOf { it.grandTotal }) }
                "Sales Report ($periodName)" to buildList {
                    add("Total Invoiced Sales" to Formatters.formatRupee(activeInvoices.sumOf { it.grandTotal }))
                    add("Bills Count" to "${activeInvoices.size}")
                    add("Product-wise Sales" to "")
                    addAll(byProduct)
                    add("Customer-wise Sales" to "")
                    addAll(byCustomer)
                }
            }
            1 -> {
                val byMethod = PaymentMethod.entries.map { m ->
                    m.name to Formatters.formatRupee(periodPayments.filter { it.paymentMethod == m.name }.sumOf { it.amountReceived })
                }
                "Payment Report ($periodName)" to buildList {
                    add("Total Payments Received" to Formatters.formatRupee(periodPayments.sumOf { it.amountReceived }))
                    add("Total Customer Receivables" to Formatters.formatRupee(state.customers.sumOf { state.customerBalance(it.id).coerceAtLeast(0.0) }))
                    add("By Manual Mode" to "")
                    addAll(byMethod)
                }
            }
            2 -> {
                val valuation = state.products.sumOf { prod ->
                    val opts = state.activeOptionsForProduct(prod.id)
                    if (prod.trackByBaseUnit) {
                        val refRate = opts.firstOrNull { it.conversionFactorToBase == 1.0 }?.sellingPrice ?: opts.firstOrNull()?.sellingPrice ?: 0.0
                        prod.basePhysicalStock * refRate
                    } else {
                        opts.sumOf { it.physicalStock * it.sellingPrice }
                    }
                }
                "Inventory Report" to buildList {
                    add("Total Products" to "${state.products.size}")
                    add("Estimated Stock Value" to Formatters.formatRupee(valuation))
                    state.products.forEach { p ->
                        val desc = if (p.trackByBaseUnit) "${Formatters.formatQty(p.basePhysicalStock)} ${p.baseUnit}"
                        else state.activeOptionsForProduct(p.id).joinToString(", ") { "${it.varietyName}: ${Formatters.formatQty(it.physicalStock)}" }
                        add(p.name to desc)
                    }
                }
            }
            3 -> {
                val byExec = periodOrders.groupBy { it.salesExecutiveName }.map { (k, v) -> k to "${v.size} orders (${Formatters.formatRupee(v.sumOf { it.totalEstimatedAmount })})" }
                "Orders Report ($periodName)" to buildList {
                    add("Total Orders" to "${periodOrders.size}")
                    add("Pending Orders" to "${periodOrders.count { it.status == OrderStatus.PENDING.name }}")
                    add("Delivered Orders" to "${periodOrders.count { it.status == OrderStatus.DELIVERED.name }}")
                    add("Cancelled Orders" to "${periodOrders.count { it.status == OrderStatus.CANCELLED.name }}")
                    add("Executive-wise Orders" to "")
                    addAll(byExec)
                }
            }
            else -> {
                val rev = activeInvoices.sumOf { it.grandTotal }
                val received = periodPayments.sumOf { it.amountReceived }
                val costEst = periodInvItems.sumOf { it.purchaseCostEstimate }
                val purchaseTotal = periodPurchases.sumOf { it.totalAmount }
                val receivables = state.customers.sumOf { state.customerBalance(it.id).coerceAtLeast(0.0) }
                val payables = state.purchases.sumOf { it.remainingPayable }
                val grossProfit = Formatters.roundMoney(rev - costEst)
                "Financial Summary ($periodName)" to listOf(
                    "Invoiced Sales Revenue" to Formatters.formatRupee(rev),
                    "Actual Payments Received" to Formatters.formatRupee(received),
                    "Customer Receivables (Outstanding)" to Formatters.formatRupee(receivables),
                    "Product Purchase Costs (Period)" to Formatters.formatRupee(purchaseTotal),
                    "Supplier Payables" to Formatters.formatRupee(payables),
                    "Estimated Gross Profit (Sales - Est. Cost)" to Formatters.formatRupee(grossProfit)
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = activeTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = activeTab == idx, onClick = { activeTab = idx }, text = { Text(label, fontWeight = FontWeight.Bold) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DateFilterBar(selected = filter, onSelect = { filter = it }) }

            // Export Toolbar (PDF Print, Share PDF, CSV for Excel)
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val (t, lines) = buildReportLines()
                            onPrintReport(t, lines, false)
                        },
                        modifier = Modifier.weight(1f).testTag("report_print_pdf")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = s.print, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.print)
                    }
                    OutlinedButton(
                        onClick = {
                            val (t, lines) = buildReportLines()
                            onPrintReport(t, lines, true)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = s.sharePdf, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            val (t, lines) = buildReportLines()
                            onExportCsv("${t.replace(" ", "_")}.csv", listOf("Metric / Item", "Value"), lines.map { listOf(it.first, it.second) })
                        },
                        modifier = Modifier.weight(1f).testTag("report_export_csv")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = s.exportCsv, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CSV")
                    }
                }
            }

            when (activeTab) {
                0 -> { // Sales Report
                    val totalSales = activeInvoices.sumOf { it.grandTotal }
                    val byProduct = periodInvItems.groupBy { it.productNameSnapshot }
                        .map { (k, v) -> k to v.sumOf { it.lineAmount } }
                        .sortedByDescending { it.second }
                    val byCategory = periodInvItems.groupBy { item ->
                        state.products.firstOrNull { it.id == item.productId }?.categoryName ?: "Other"
                    }.map { (k, v) -> k to v.sumOf { it.lineAmount } }.sortedByDescending { it.second }
                    val byCustomer = activeInvoices.groupBy { it.shopNameSnapshot }
                        .map { (k, v) -> k to v.sumOf { it.grandTotal } }
                        .sortedByDescending { it.second }

                    item {
                        ReportCard("Sales Summary") {
                            ReportRow("Invoiced Sales", Formatters.formatRupee(totalSales), CreditGreen)
                            ReportRow("Total Bills", "${activeInvoices.size}")
                            ReportRow("KG Sold", Formatters.formatQty(activeInvoices.sumOf { it.totalKg }))
                            ReportRow("Bags Sold", Formatters.formatQty(activeInvoices.sumOf { it.totalBags }))
                            ReportRow("Packets Sold", Formatters.formatQty(activeInvoices.sumOf { it.totalPackets }))
                            ReportRow("Boxes Sold", Formatters.formatQty(activeInvoices.sumOf { it.totalBoxes }))
                        }
                    }
                    item {
                        ReportCard("Product-wise Sales") {
                            if (byProduct.isEmpty()) Text(s.noRecordsYet)
                            byProduct.forEach { (k, v) -> ReportRow(k, Formatters.formatRupee(v)) }
                        }
                    }
                    item {
                        ReportCard("Category-wise Sales") {
                            if (byCategory.isEmpty()) Text(s.noRecordsYet)
                            byCategory.forEach { (k, v) -> ReportRow(k, Formatters.formatRupee(v)) }
                        }
                    }
                    item {
                        ReportCard("Customer-wise Sales") {
                            if (byCustomer.isEmpty()) Text(s.noRecordsYet)
                            byCustomer.forEach { (k, v) -> ReportRow(k, Formatters.formatRupee(v)) }
                        }
                    }
                }
                1 -> { // Payment Report
                    val totalRec = periodPayments.sumOf { it.amountReceived }
                    val outstanding = state.customers.sumOf { state.customerBalance(it.id).coerceAtLeast(0.0) }
                    item {
                        ReportCard("Collections Summary") {
                            ReportRow(s.paymentsReceived, Formatters.formatRupee(totalRec), CreditGreen)
                            ReportRow(s.outstandingBalance, Formatters.formatRupee(outstanding), DebitRed)
                            HorizontalDivider()
                            PaymentMethod.entries.forEach { m ->
                                val sum = periodPayments.filter { it.paymentMethod == m.name }.sumOf { it.amountReceived }
                                ReportRow(s.paymentMethodLabel(m), Formatters.formatRupee(sum))
                            }
                        }
                    }
                }
                2 -> { // Inventory Report
                    val lowCount = state.products.sumOf { p -> state.activeOptionsForProduct(p.id).count { state.isLowStock(p, it) } }
                    val outCount = state.products.sumOf { p -> state.activeOptionsForProduct(p.id).count { state.availableStockForOption(p, it) <= 0.0 } }
                    val damagedMoves = state.stockMovements.count { it.movementType == StockMovementType.RETURN_DAMAGED.name }
                    val valuation = state.products.sumOf { prod ->
                        val opts = state.activeOptionsForProduct(prod.id)
                        if (prod.trackByBaseUnit) {
                            val r = opts.firstOrNull { it.conversionFactorToBase == 1.0 }?.sellingPrice ?: opts.firstOrNull()?.sellingPrice ?: 0.0
                            prod.basePhysicalStock * r
                        } else opts.sumOf { it.physicalStock * it.sellingPrice }
                    }
                    item {
                        ReportCard("Inventory Overview") {
                            ReportRow(s.stockValuation, Formatters.formatRupee(valuation), CreditGreen)
                            ReportRow(s.lowStockProducts, "$lowCount", DebitRed)
                            ReportRow("Out of Stock Variants", "$outCount", DebitRed)
                            ReportRow("Damaged Stock Entries", "$damagedMoves")
                        }
                    }
                }
                3 -> { // Order Report
                    val byExec = periodOrders.groupBy { it.salesExecutiveName }
                    item {
                        ReportCard("Orders Overview") {
                            ReportRow(s.totalOrders, "${periodOrders.size}")
                            ReportRow("Pending", "${periodOrders.count { it.status == OrderStatus.PENDING.name }}")
                            ReportRow("Delivered", "${periodOrders.count { it.status == OrderStatus.DELIVERED.name }}", CreditGreen)
                            ReportRow("Cancelled", "${periodOrders.count { it.status == OrderStatus.CANCELLED.name }}", DebitRed)
                        }
                    }
                    item {
                        ReportCard("Sales Executive-wise Orders") {
                            if (byExec.isEmpty()) Text(s.noRecordsYet)
                            byExec.forEach { (name, list) ->
                                ReportRow(name, "${list.size} (${Formatters.formatRupee(list.sumOf { it.totalEstimatedAmount })})")
                            }
                        }
                    }
                }
                4 -> { // Financial Report (NO Expenses module!)
                    val invoicedSales = activeInvoices.sumOf { it.grandTotal }
                    val actualReceived = periodPayments.sumOf { it.amountReceived }
                    val receivables = state.customers.sumOf { state.customerBalance(it.id).coerceAtLeast(0.0) }
                    val purchaseCosts = periodPurchases.sumOf { it.totalAmount }
                    val payables = state.purchases.sumOf { it.remainingPayable }
                    val estCostOfSold = periodInvItems.sumOf { it.purchaseCostEstimate }
                    val grossProfitEst = Formatters.roundMoney(invoicedSales - estCostOfSold)
                    item {
                        ReportCard("Wholesale Financial Statement") {
                            ReportRow("Invoiced Sales Revenue", Formatters.formatRupee(invoicedSales))
                            ReportRow("Actual Payments Received", Formatters.formatRupee(actualReceived), CreditGreen)
                            ReportRow(s.customerReceivables, Formatters.formatRupee(receivables), DebitRed)
                            HorizontalDivider()
                            ReportRow(s.purchaseCosts, Formatters.formatRupee(purchaseCosts))
                            ReportRow(s.supplierPayables, Formatters.formatRupee(payables), DebitRed)
                            HorizontalDivider()
                            ReportRow(s.estimatedGrossProfit, Formatters.formatRupee(grossProfitEst), if (grossProfitEst >= 0) CreditGreen else DebitRed)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
    }
}
