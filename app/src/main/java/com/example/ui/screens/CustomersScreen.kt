package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomerEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.PaymentEntity
import com.example.data.model.DateFilter
import com.example.data.model.EmployeeRole
import com.example.data.model.InvoiceStatus
import com.example.i18n.LocalStrings
import com.example.ui.components.*
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun CustomersScreen(
    state: AppUiState,
    onSaveCustomer: (name: String, shopName: String, mobile: String, address: String, existingId: Long) -> Unit,
    onPrintInvoice: (InvoiceEntity, Int, Boolean) -> Unit,
    onPrintReceipt: (PaymentEntity, Boolean) -> Unit,
    onPrintHistory: (CustomerEntity, DateFilter, Boolean) -> Unit
) {
    val s = LocalStrings.current
    var search by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    val selectedCustomer = remember(selectedCustomerId, state.customers) {
        state.customers.firstOrNull { it.id == selectedCustomerId }
    }

    if (selectedCustomer != null) {
        BackHandler { selectedCustomerId = null }
        CustomerProfileView(
            customer = selectedCustomer,
            state = state,
            onBack = { selectedCustomerId = null },
            onEdit = { editingCustomer = selectedCustomer },
            onPrintInvoice = onPrintInvoice,
            onPrintReceipt = onPrintReceipt,
            onPrintHistory = onPrintHistory
        )
    } else {
        val visibleCustomers = remember(state.customers, state.activeEmployee, search) {
            val base = if (state.activeEmployee.role == EmployeeRole.SALES_EXECUTIVE.name) {
                state.customers.filter { it.assignedEmployeeId == null || it.assignedEmployeeId == state.activeEmployee.id }
            } else {
                state.customers
            }
            base.filter { Formatters.matchesSearch(search, it.name, it.shopName, it.mobile, it.address) }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (state.activeEmployee.canAddCustomer) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddDialog = true },
                        icon = { Icon(Icons.Default.PersonAdd, contentDescription = s.addCustomer) },
                        text = { Text(s.addCustomer) },
                        modifier = Modifier.testTag("add_customer_fab")
                    )
                }
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = s.searchCustomerHint,
                    tag = "customer_search_input"
                )

                if (visibleCustomers.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.People,
                        message = s.noRecordsYet,
                        actionLabel = if (state.activeEmployee.canAddCustomer) s.addCustomer else null,
                        onAction = { showAddDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(visibleCustomers, key = { it.id }) { customer ->
                            val bal = state.customerBalance(customer.id)
                            val billCount = state.invoices.count { it.customerId == customer.id && it.status != InvoiceStatus.CANCELLED.name }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCustomerId = customer.id }
                                    .testTag("customer_card_${customer.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(customer.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${customer.name} • ${customer.mobile}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (customer.address.isNotBlank()) {
                                            Text(customer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            Formatters.formatRupee(bal),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (bal > 0.01) DebitRed else CreditGreen
                                        )
                                        Text("$billCount ${s.tabBills}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditCustomerDialog(
            existing = null,
            existingCustomers = state.customers,
            onDismiss = { showAddDialog = false },
            onSave = { name, shop, mob, addr ->
                onSaveCustomer(name, shop, mob, addr, 0L)
                showAddDialog = false
            }
        )
    }

    editingCustomer?.let { existing ->
        AddEditCustomerDialog(
            existing = existing,
            existingCustomers = state.customers,
            onDismiss = { editingCustomer = null },
            onSave = { name, shop, mob, addr ->
                onSaveCustomer(name, shop, mob, addr, existing.id)
                editingCustomer = null
            }
        )
    }
}

@Composable
private fun CustomerProfileView(
    customer: CustomerEntity,
    state: AppUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPrintInvoice: (InvoiceEntity, Int, Boolean) -> Unit,
    onPrintReceipt: (PaymentEntity, Boolean) -> Unit,
    onPrintHistory: (CustomerEntity, DateFilter, Boolean) -> Unit
) {
    val s = LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var printTarget by remember { mutableStateOf<InvoiceEntity?>(null) }
    var showPrintHistoryDialog by remember { mutableStateOf(false) }

    val custInvoices = remember(state.invoices, customer.id) { state.invoices.filter { it.customerId == customer.id } }
    val activeInvoices = remember(custInvoices) { custInvoices.filter { it.status != InvoiceStatus.CANCELLED.name } }
    val custOrders = remember(state.orders, customer.id) { state.orders.filter { it.customerId == customer.id } }
    val custPayments = remember(state.payments, customer.id) { state.payments.filter { it.customerId == customer.id } }
    val custLedger = remember(state.ledger, customer.id) { state.ledger.filter { it.customerId == customer.id } }

    val totalPurchase = activeInvoices.sumOf { it.grandTotal }
    val totalPaid = custPayments.sumOf { it.amountReceived }
    val balance = state.customerBalance(customer.id)
    val totalBags = activeInvoices.sumOf { it.totalBags }
    val totalPackets = activeInvoices.sumOf { it.totalPackets }
    val lastPurchase = activeInvoices.maxOfOrNull { it.invoiceDate }
    val lastPayment = custPayments.maxOfOrNull { it.paymentDate }

    val tabs = listOf(s.tabOverview, s.tabOrders, s.tabBills, s.tabPayments, s.tabHistory, s.tabLedger)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Profile Top Header
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(customer.shopName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${customer.name} • ${customer.mobile}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = s.edit)
                    }
                    FilledTonalIconButton(
                        onClick = { showPrintHistoryDialog = true },
                        modifier = Modifier.testTag("print_customer_history_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = s.printCustomerHistory)
                    }
                }
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 4.dp) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> { // Overview
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (customer.address.isNotBlank()) {
                                    ProfileRow(s.address, customer.address)
                                }
                                ProfileRow(s.totalPurchase, Formatters.formatRupee(totalPurchase))
                                ProfileRow(s.totalPaid, Formatters.formatRupee(totalPaid), CreditGreen)
                                ProfileRow(s.remainingBalance, Formatters.formatRupee(balance), if (balance > 0) DebitRed else CreditGreen)
                                HorizontalDivider()
                                ProfileRow(s.tabBills, "${activeInvoices.size}")
                                ProfileRow(s.tabOrders, "${custOrders.size}")
                                ProfileRow(s.bagsPurchased, Formatters.formatQty(totalBags))
                                ProfileRow(s.packetsPurchased, Formatters.formatQty(totalPackets))
                                ProfileRow(s.lastPurchase, lastPurchase?.let { Formatters.formatDate(it) } ?: "—")
                                ProfileRow(s.lastPayment, lastPayment?.let { Formatters.formatDate(it) } ?: "—")
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { showPrintHistoryDialog = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = s.printCustomerHistory)
                            Spacer(Modifier.width(8.dp))
                            Text(s.printCustomerHistory)
                        }
                    }
                }
                1 -> { // Orders
                    if (custOrders.isEmpty()) item { EmptyStateCard(Icons.Default.Assignment, s.noRecordsYet) }
                    items(custOrders, key = { it.id }) { ord ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(ord.orderNumber, fontWeight = FontWeight.Bold)
                                    Text("${Formatters.formatDate(ord.orderDate)} • ${ord.status}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Formatters.formatRupee(ord.totalEstimatedAmount), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                2 -> { // Bills
                    if (custInvoices.isEmpty()) item { EmptyStateCard(Icons.Default.ReceiptLong, s.noRecordsYet) }
                    items(custInvoices, key = { it.id }) { inv ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${inv.invoiceNumber} (${inv.status})", fontWeight = FontWeight.Bold)
                                    Text("${Formatters.formatDate(inv.invoiceDate)} • Paid: ${Formatters.formatRupee(inv.amountPaid)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Formatters.formatRupee(inv.grandTotal), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { printTarget = inv }) {
                                    Icon(Icons.Default.Print, contentDescription = s.print)
                                }
                            }
                        }
                    }
                }
                3 -> { // Payments
                    if (custPayments.isEmpty()) item { EmptyStateCard(Icons.Default.Payments, s.noRecordsYet) }
                    items(custPayments, key = { it.id }) { pay ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${pay.receiptNumber} • ${pay.paymentMethod}", fontWeight = FontWeight.Bold)
                                    Text(Formatters.formatDate(pay.paymentDate), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Formatters.formatRupee(pay.amountReceived), fontWeight = FontWeight.Bold, color = CreditGreen)
                                IconButton(onClick = { onPrintReceipt(pay, false) }) {
                                    Icon(Icons.Default.Print, contentDescription = s.printReceipt)
                                }
                            }
                        }
                    }
                }
                4 -> { // Unified History Timeline
                    val combined = (custInvoices.map { it.invoiceDate to "${it.invoiceNumber} (Bill) • ${Formatters.formatRupee(it.grandTotal)}" } +
                        custPayments.map { it.paymentDate to "${it.receiptNumber} (${it.paymentMethod}) • -${Formatters.formatRupee(it.amountReceived)}" } +
                        custOrders.map { it.orderDate to "${it.orderNumber} (Order - ${it.status}) • ${Formatters.formatRupee(it.totalEstimatedAmount)}" })
                        .sortedByDescending { it.first }
                    if (combined.isEmpty()) item { EmptyStateCard(Icons.Default.History, s.noRecordsYet) }
                    items(combined) { (ts, text) ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(text, fontWeight = FontWeight.SemiBold)
                                Text(Formatters.formatDateTime(ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                5 -> { // Ledger Table
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(s.date, Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(s.debit, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(s.credit, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(s.balance, Modifier.weight(1.1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (custLedger.isEmpty()) item { EmptyStateCard(Icons.Default.MenuBook, s.noRecordsYet) }
                    items(custLedger, key = { it.id }) { row ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("${row.referenceNumber} — ${row.description}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Text(Formatters.formatShortDate(row.entryDate), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                                    Text(if (row.debitAmount > 0) Formatters.formatRupee(row.debitAmount) else "—", Modifier.weight(1f), color = DebitRed, style = MaterialTheme.typography.bodySmall)
                                    Text(if (row.creditAmount > 0) Formatters.formatRupee(row.creditAmount) else "—", Modifier.weight(1f), color = CreditGreen, style = MaterialTheme.typography.bodySmall)
                                    Text(Formatters.formatRupee(row.runningBalance), Modifier.weight(1.1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    printTarget?.let { inv ->
        PrintInvoiceDialog(
            invoice = inv,
            onDismiss = { printTarget = null },
            onPrint = { copies, share -> onPrintInvoice(inv, copies, share) }
        )
    }

    if (showPrintHistoryDialog) {
        var filter by remember { mutableStateOf(DateFilter.ALL_TIME) }
        AlertDialog(
            onDismissRequest = { showPrintHistoryDialog = false },
            title = { Text(s.printCustomerHistory, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${customer.shopName} (${customer.name})")
                    DateFilterBar(selected = filter, onSelect = { filter = it })
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPrintHistory(customer, filter, true); showPrintHistoryDialog = false }) {
                        Text(s.sharePdf)
                    }
                    Button(onClick = { onPrintHistory(customer, filter, false); showPrintHistoryDialog = false }) {
                        Text(s.print)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showPrintHistoryDialog = false }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor, style = MaterialTheme.typography.bodyLarge)
    }
}
