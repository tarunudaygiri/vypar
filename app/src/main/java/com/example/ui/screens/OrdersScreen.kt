package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomerEntity
import com.example.data.db.OrderEntity
import com.example.data.db.OrderItemEntity
import com.example.data.model.DateFilter
import com.example.data.model.EmployeeRole
import com.example.data.model.OrderStatus
import com.example.data.repository.DraftLineItem
import com.example.i18n.LocalStrings
import com.example.ui.components.*
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun OrdersScreen(
    state: AppUiState,
    onQuickSaveCustomer: (String, String, String, String, (Long) -> Unit) -> Unit,
    onSaveOrder: (Long?, CustomerEntity, List<DraftLineItem>, OrderStatus, String, (OrderEntity) -> Unit) -> Unit,
    onUpdateStatus: (Long, OrderStatus) -> Unit,
    onDuplicateOrder: (Long) -> Unit,
    onConvertToBill: (OrderEntity, List<OrderItemEntity>) -> Unit,
    onPrintOrderSlip: (OrderEntity, Boolean) -> Unit
) {
    val s = LocalStrings.current
    var search by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(DateFilter.ALL_TIME) }
    var executiveFilterId by remember { mutableStateOf<Long?>(null) }
    var showCreateOrder by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<OrderEntity?>(null) }
    var statusChangeTarget by remember { mutableStateOf<OrderEntity?>(null) }

    val (startMs, endMs) = remember(dateFilter) { Formatters.dateRangeFor(dateFilter) }

    val filteredOrders = remember(state.orders, state.activeEmployee, search, startMs, endMs, executiveFilterId) {
        state.orders.filter { ord ->
            val roleOk = if (state.activeEmployee.role == EmployeeRole.SALES_EXECUTIVE.name) {
                ord.salesExecutiveId == state.activeEmployee.id
            } else true
            val execOk = executiveFilterId == null || ord.salesExecutiveId == executiveFilterId
            val dateOk = ord.orderDate in startMs..endMs
            val queryOk = Formatters.matchesSearch(search, ord.orderNumber, ord.customerNameSnapshot, ord.shopNameSnapshot, ord.salesExecutiveName)
            roleOk && execOk && dateOk && queryOk
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.activeEmployee.canCreateOrder) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateOrder = true },
                    icon = { Icon(Icons.Default.PostAdd, contentDescription = s.newOrder) },
                    text = { Text(s.newOrder) },
                    modifier = Modifier.testTag("new_order_fab")
                )
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchField(search, { search = it }, "${s.search} ${s.navOrders}...")
            DateFilterBar(selected = dateFilter, onSelect = { dateFilter = it })

            // Filter by Sales Executive (for Owner/Manager)
            if (state.activeEmployee.role != EmployeeRole.SALES_EXECUTIVE.name && state.employees.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = executiveFilterId == null,
                        onClick = { executiveFilterId = null },
                        label = { Text("${s.salesExecutive}: ${s.viewAll}") }
                    )
                    state.employees.forEach { emp ->
                        FilterChip(
                            selected = executiveFilterId == emp.id,
                            onClick = { executiveFilterId = if (executiveFilterId == emp.id) null else emp.id },
                            label = { Text(emp.name) }
                        )
                    }
                }
            }

            if (filteredOrders.isEmpty()) {
                EmptyStateCard(Icons.Default.Assignment, s.noRecordsYet, s.newOrder) { showCreateOrder = true }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { ord ->
                        val itemsForOrder = state.orderItems.filter { it.orderId == ord.id }
                        val statusEnum = runCatching { OrderStatus.valueOf(ord.status) }.getOrDefault(OrderStatus.PENDING)
                        val alreadyConverted = ord.convertedInvoiceId != null
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(ord.orderNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        StatusPill(
                                            s.orderStatusText(statusEnum),
                                            when (statusEnum) {
                                                OrderStatus.DELIVERED -> CreditGreen.copy(alpha = 0.14f)
                                                OrderStatus.CANCELLED -> DebitRed.copy(alpha = 0.14f)
                                                else -> WarningAmber.copy(alpha = 0.16f)
                                            },
                                            when (statusEnum) {
                                                OrderStatus.DELIVERED -> CreditGreen
                                                OrderStatus.CANCELLED -> DebitRed
                                                else -> WarningAmber
                                            }
                                        )
                                        if (alreadyConverted) {
                                            StatusPill(s.alreadyInvoiced, CreditGreen.copy(alpha = 0.14f), CreditGreen)
                                        }
                                    }
                                    Text(Formatters.formatRupee(ord.totalEstimatedAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }

                                Text("${ord.shopNameSnapshot} (${ord.customerNameSnapshot})", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Formatters.formatDate(ord.orderDate)} • ${s.salesExecutive}: ${ord.salesExecutiveName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Items summary
                                itemsForOrder.forEach { it ->
                                    Text(
                                        "• ${it.productNameSnapshot} (${it.varietyNameSnapshot}) — ${Formatters.formatQty(it.quantity)} ${it.sellingUnitSnapshot}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (ord.notes.isNotBlank()) {
                                    Text("${s.notes}: ${ord.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!alreadyConverted && statusEnum != OrderStatus.CANCELLED && state.activeEmployee.canCreateBill) {
                                        Button(
                                            onClick = { onConvertToBill(ord, itemsForOrder) },
                                            modifier = Modifier.heightIn(min = 42.dp).testTag("convert_order_${ord.id}")
                                        ) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = s.convertToBill, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(s.convertToBill)
                                        }
                                    }
                                    OutlinedButton(onClick = { statusChangeTarget = ord }) { Text(s.status) }
                                    if (!alreadyConverted && statusEnum != OrderStatus.CANCELLED) {
                                        OutlinedButton(onClick = { editingOrder = ord }) { Text(s.edit) }
                                    }
                                    OutlinedButton(onClick = { onDuplicateOrder(ord.id) }) { Text(s.duplicateOrder) }
                                    OutlinedButton(onClick = { onPrintOrderSlip(ord, false) }) {
                                        Icon(Icons.Default.Print, contentDescription = s.printOrderSlip, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(s.print)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateOrder || editingOrder != null) {
        val existingItems = editingOrder?.let { ord ->
            state.orderItems.filter { it.orderId == ord.id }.map {
                DraftLineItem(it.productId, it.sellingOptionId, it.productNameSnapshot, it.varietyNameSnapshot, it.sellingUnitSnapshot, it.quantity, it.rate)
            }
        } ?: emptyList()

        CreateEditOrderDialog(
            state = state,
            existingOrder = editingOrder,
            initialItems = existingItems,
            onQuickSaveCustomer = onQuickSaveCustomer,
            onDismiss = { showCreateOrder = false; editingOrder = null },
            onSave = { cust, items, status, notes ->
                onSaveOrder(editingOrder?.id, cust, items, status, notes) {
                    showCreateOrder = false
                    editingOrder = null
                }
            }
        )
    }

    statusChangeTarget?.let { ord ->
        AlertDialog(
            onDismissRequest = { statusChangeTarget = null },
            title = { Text("${s.updateStatus} — ${ord.orderNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrderStatus.entries.forEach { st ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateStatus(ord.id, st)
                                    statusChangeTarget = null
                                },
                            color = if (ord.status == st.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(s.orderStatusText(st), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { statusChangeTarget = null }) { Text(s.close) } }
        )
    }
}

@Composable
private fun CreateEditOrderDialog(
    state: AppUiState,
    existingOrder: OrderEntity?,
    initialItems: List<DraftLineItem>,
    onQuickSaveCustomer: (String, String, String, String, (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity, List<DraftLineItem>, OrderStatus, String) -> Unit
) {
    val s = LocalStrings.current
    var customerId by remember { mutableStateOf(existingOrder?.customerId) }
    var custSearch by remember { mutableStateOf("") }
    var showAddCust by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(initialItems) }
    var showAddItem by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(existingOrder?.notes ?: "") }
    var status by remember { mutableStateOf(existingOrder?.let { runCatching { OrderStatus.valueOf(it.status) }.getOrDefault(OrderStatus.PENDING) } ?: OrderStatus.PENDING) }

    val selectedCustomer = state.customers.firstOrNull { it.id == customerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingOrder == null) s.createOrder else "${s.edit} ${existingOrder.orderNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedCustomer != null) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(selectedCustomer.shopName, fontWeight = FontWeight.Bold)
                                Text(selectedCustomer.name, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { customerId = null }) { Text("Change") }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(s.selectCustomer, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddCust = true }) { Text(s.addNewCustomerInline) }
                    }
                    SearchField(custSearch, { custSearch = it }, s.searchCustomerHint)
                    state.customers.filter { Formatters.matchesSearch(custSearch, it.name, it.shopName, it.mobile) }.take(5).forEach { c ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { customerId = c.id },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text("${c.shopName} (${c.name})", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.billItems} (${items.size})", fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { showAddItem = true }) { Text("+ ${s.add}") }
                }
                items.forEachIndexed { idx, line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${line.varietyName} • ${Formatters.formatQty(line.quantity)} ${line.sellingUnit} = ${Formatters.formatRupee(line.lineAmount)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { items = items.filterIndexed { i, _ -> i != idx } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = s.delete, tint = DebitRed)
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("${s.notes} (${s.optional})") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedCustomer?.let { onSave(it, items, status, notes) } },
                enabled = selectedCustomer != null && items.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_order_btn")
            ) { Text(s.saveOrder) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )

    if (showAddCust) {
        AddEditCustomerDialog(
            existing = null,
            existingCustomers = state.customers,
            onDismiss = { showAddCust = false },
            onSave = { n, sh, m, a ->
                onQuickSaveCustomer(n, sh, m, a) { newId -> customerId = newId }
                showAddCust = false
            }
        )
    }
    if (showAddItem) {
        AddLineItemDialog(
            state = state,
            canEditRate = state.activeEmployee.canEditPrices,
            onDismiss = { showAddItem = false },
            onAdd = { item -> items = items + item; showAddItem = false }
        )
    }
}
