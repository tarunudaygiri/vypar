package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.InvoiceEntity
import com.example.data.model.AppScreen
import com.example.data.model.DateFilter
import com.example.data.model.InvoiceStatus
import com.example.data.model.OrderStatus
import com.example.i18n.LocalStrings
import com.example.ui.components.*
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters
import java.util.Calendar

@Composable
fun DashboardScreen(
    state: AppUiState,
    onNavigate: (AppScreen) -> Unit,
    onQuickAddCustomer: () -> Unit,
    onPrintInvoice: (InvoiceEntity, Int, Boolean) -> Unit
) {
    val s = LocalStrings.current
    var selectedFilter by remember { mutableStateOf(DateFilter.THIS_MONTH) }
    var printTarget by remember { mutableStateOf<InvoiceEntity?>(null) }

    val (rangeStart, rangeEnd) = remember(selectedFilter) { Formatters.dateRangeFor(selectedFilter) }
    val (monthStart, monthEnd) = remember { Formatters.dateRangeFor(DateFilter.THIS_MONTH) }

    val activeInvoices = remember(state.invoices) {
        state.invoices.filter { it.status != InvoiceStatus.CANCELLED.name }
    }
    val periodInvoices = remember(activeInvoices, rangeStart, rangeEnd) {
        activeInvoices.filter { it.invoiceDate in rangeStart..rangeEnd }
    }
    val periodPayments = remember(state.payments, rangeStart, rangeEnd) {
        state.payments.filter { it.paymentDate in rangeStart..rangeEnd }
    }
    val periodOrders = remember(state.orders, rangeStart, rangeEnd) {
        state.orders.filter { it.orderDate in rangeStart..rangeEnd }
    }

    val periodSalesTotal = periodInvoices.sumOf { it.grandTotal }
    val monthlySalesTotal = activeInvoices.filter { it.invoiceDate in monthStart..monthEnd }.sumOf { it.grandTotal }
    val receivedTotal = periodPayments.sumOf { it.amountReceived }
    val totalOutstanding = state.customers.sumOf { state.customerBalance(it.id).coerceAtLeast(0.0) }
    val pendingOrderCount = state.orders.count {
        it.status in listOf(OrderStatus.DRAFT.name, OrderStatus.PENDING.name, OrderStatus.CONFIRMED.name, OrderStatus.PROCESSING.name)
    }

    val lowStockList = remember(state.products, state.sellingOptions) {
        state.products.flatMap { prod ->
            state.activeOptionsForProduct(prod.id)
                .filter { state.isLowStock(prod, it) }
                .map { prod to it }
        }
    }

    val totalKg = periodInvoices.sumOf { it.totalKg }
    val totalBags = periodInvoices.sumOf { it.totalBags }
    val totalPackets = periodInvoices.sumOf { it.totalPackets }
    val totalBoxes = periodInvoices.sumOf { it.totalBoxes }

    val pendingCustomers = remember(state.customers, state.ledger) {
        state.customers
            .map { it to state.customerBalance(it.id) }
            .filter { it.second > 0.5 }
            .sortedByDescending { it.second }
            .take(5)
    }

    val topProducts = remember(state.invoiceItems, periodInvoices) {
        val ids = periodInvoices.map { it.id }.toSet()
        state.invoiceItems
            .filter { it.invoiceId in ids }
            .groupBy { it.productNameSnapshot }
            .map { (name, items) -> name to items.sumOf { it.lineAmount } }
            .sortedByDescending { it.second }
            .take(5)
    }

    // 7-day sales chart data
    val dailyChartPoints = remember(activeInvoices) {
        val cal = Calendar.getInstance()
        (6 downTo 0).map { offset ->
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, -offset)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = dayStart + 86_400_000L - 1L
            val sum = activeInvoices.filter { it.invoiceDate in dayStart..dayEnd }.sumOf { it.grandTotal }
            val label = Formatters.formatShortDate(dayStart).substring(0, 5)
            label to sum
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date Filters
        item {
            DateFilterBar(selected = selectedFilter, onSelect = { selectedFilter = it })
        }

        // Hero Financial Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${s.todaysSales} (${s.dateFilterLabel(selectedFilter)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                Formatters.formatRupee(periodSalesTotal),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "${periodInvoices.size} ${s.tabBills}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroSubStat(s.monthlySales, Formatters.formatRupee(monthlySalesTotal), Modifier.weight(1f))
                        HeroSubStat(s.paymentsReceived, Formatters.formatRupee(receivedTotal), Modifier.weight(1f))
                        HeroSubStat(s.outstandingBalance, Formatters.formatRupee(totalOutstanding), Modifier.weight(1f))
                    }
                }
            }
        }

        // Quick Actions (Big Touch-Friendly Wholesale Buttons)
        item {
            SectionHeader(s.quickActions)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        label = s.navNewBill,
                        icon = Icons.Default.ReceiptLong,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onNavigate(AppScreen.NEW_BILL) },
                        modifier = Modifier.weight(1f),
                        tag = "quick_new_bill"
                    )
                    QuickActionTile(
                        label = s.newOrder,
                        icon = Icons.Default.PostAdd,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onNavigate(AppScreen.ORDERS) },
                        modifier = Modifier.weight(1f),
                        tag = "quick_new_order"
                    )
                    QuickActionTile(
                        label = s.recordPayment,
                        icon = Icons.Default.Payments,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { onNavigate(AppScreen.PAYMENTS) },
                        modifier = Modifier.weight(1f),
                        tag = "quick_record_payment"
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        label = s.addCustomer,
                        icon = Icons.Default.PersonAdd,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = onQuickAddCustomer,
                        modifier = Modifier.weight(1f),
                        tag = "quick_add_customer"
                    )
                    QuickActionTile(
                        label = s.addProduct,
                        icon = Icons.Default.AddBox,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigate(AppScreen.PRODUCTS) },
                        modifier = Modifier.weight(1f),
                        tag = "quick_add_product"
                    )
                    QuickActionTile(
                        label = s.navReports,
                        icon = Icons.Default.Assessment,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigate(AppScreen.REPORTS) },
                        modifier = Modifier.weight(1f),
                        tag = "quick_reports"
                    )
                }
            }
        }

        // KPI Counts & Units Sold Strip
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniKpiCard(s.totalCustomers, "${state.customers.size}", Modifier.weight(1f)) { onNavigate(AppScreen.CUSTOMERS) }
                MiniKpiCard(s.totalProducts, "${state.products.size}", Modifier.weight(1f)) { onNavigate(AppScreen.PRODUCTS) }
                MiniKpiCard(s.pendingOrders, "$pendingOrderCount", Modifier.weight(1f), WarningAmber) { onNavigate(AppScreen.ORDERS) }
                MiniKpiCard(s.lowStockProducts, "${lowStockList.size}", Modifier.weight(1f), if (lowStockList.isNotEmpty()) DebitRed else CreditGreen) { onNavigate(AppScreen.INVENTORY) }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.unitsSold} (${s.dateFilterLabel(selectedFilter)})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        UnitStatPill("KG", Formatters.formatQty(totalKg))
                        UnitStatPill("Bags", Formatters.formatQty(totalBags))
                        UnitStatPill("Packets", Formatters.formatQty(totalPackets))
                        UnitStatPill("Boxes", Formatters.formatQty(totalBoxes))
                    }
                }
            }
        }

        // 7-Day Sales Overview Chart
        item {
            SectionHeader(s.salesOverviewChart)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val primaryCol = MaterialTheme.colorScheme.primary
                    val trackCol = MaterialTheme.colorScheme.surfaceVariant
                    val maxVal = (dailyChartPoints.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        val barCount = dailyChartPoints.size
                        val slotW = size.width / barCount
                        val barW = slotW * 0.52f
                        dailyChartPoints.forEachIndexed { idx, (_, value) ->
                            val x = idx * slotW + (slotW - barW) / 2f
                            drawRoundRect(
                                color = trackCol,
                                topLeft = Offset(x, 8f),
                                size = Size(barW, size.height - 16f),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            val frac = (value / maxVal).toFloat().coerceIn(0f, 1f)
                            val h = ((size.height - 16f) * frac).coerceAtLeast(if (value > 0) 10f else 0f)
                            drawRoundRect(
                                color = primaryCol,
                                topLeft = Offset(x, size.height - 8f - h),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        dailyChartPoints.forEach { (label, _) ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Low Stock Alerts
        if (lowStockList.isNotEmpty()) {
            item {
                SectionHeader(s.lowStockAlerts, s.viewAll, { onNavigate(AppScreen.INVENTORY) })
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, DebitRed.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lowStockList.take(4).forEach { (prod, opt) ->
                            val avail = state.availableStockForOption(prod, opt)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(prod.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(opt.varietyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(
                                    "${Formatters.formatQty(avail)} ${opt.sellingUnit} / Min ${Formatters.formatQty(opt.minStock)}",
                                    DebitRed.copy(alpha = 0.14f),
                                    DebitRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pending Customer Balances
        if (pendingCustomers.isNotEmpty()) {
            item {
                SectionHeader(s.pendingBalances, s.viewAll, { onNavigate(AppScreen.CUSTOMERS) })
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pendingCustomers.forEach { (cust, bal) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cust.shopName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${cust.name} • ${cust.mobile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Formatters.formatRupee(bal), fontWeight = FontWeight.Bold, color = DebitRed)
                            }
                        }
                    }
                }
            }
        }

        // Recent Invoices with Direct Print Button
        item {
            SectionHeader(s.recentInvoices, s.navNewBill, { onNavigate(AppScreen.NEW_BILL) })
            if (activeInvoices.isEmpty()) {
                EmptyStateCard(Icons.Default.ReceiptLong, s.noRecordsYet, s.navNewBill) { onNavigate(AppScreen.NEW_BILL) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeInvoices.take(5).forEach { inv ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(inv.invoiceNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        StatusPill(
                                            s.paymentStatusText(inv.paymentStatus),
                                            if (inv.remainingBalance <= 0.01) CreditGreen.copy(alpha = 0.14f) else WarningAmber.copy(alpha = 0.16f),
                                            if (inv.remainingBalance <= 0.01) CreditGreen else WarningAmber
                                        )
                                    }
                                    Text(inv.shopNameSnapshot, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${Formatters.formatDate(inv.invoiceDate)} • ${s.remainingBalance}: ${Formatters.formatRupee(inv.remainingBalance)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Formatters.formatRupee(inv.grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { printTarget = inv }) {
                                        Icon(Icons.Default.Print, contentDescription = s.print, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Selling Products
        if (topProducts.isNotEmpty()) {
            item {
                SectionHeader(s.topSellingProducts, s.viewAll, { onNavigate(AppScreen.REPORTS) })
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topProducts.forEach { (name, amt) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(Formatters.formatRupee(amt), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Recent Orders & Recent Payments
        if (periodOrders.isNotEmpty()) {
            item {
                SectionHeader(s.recentOrders, s.viewAll, { onNavigate(AppScreen.ORDERS) })
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        periodOrders.take(3).forEach { ord ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${ord.orderNumber} • ${ord.shopNameSnapshot}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${ord.salesExecutiveName} • ${ord.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Formatters.formatRupee(ord.totalEstimatedAmount), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Employee Activities
        if (state.activityLogs.isNotEmpty()) {
            item {
                SectionHeader(s.employeeActivity, s.viewAll, { onNavigate(AppScreen.EMPLOYEES) })
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.activityLogs.take(4).forEach { log ->
                            Column {
                                Text("${log.employeeName}: ${log.details}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(Formatters.formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    printTarget?.let { inv ->
        PrintInvoiceDialog(
            invoice = inv,
            onDismiss = { printTarget = null },
            onPrint = { copies, shareOnly -> onPrintInvoice(inv, copies, shareOnly) }
        )
    }
}

@Composable
private fun HeroSubStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UnitStatPill(unit: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
