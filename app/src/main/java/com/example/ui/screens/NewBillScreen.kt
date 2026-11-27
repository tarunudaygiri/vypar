package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductEntity
import com.example.data.db.SellingOptionEntity
import com.example.data.model.InvoiceStatus
import com.example.data.model.PaymentStatus
import com.example.data.repository.DraftLineItem
import com.example.i18n.LocalStrings
import com.example.ui.components.*
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBillScreen(
    state: AppUiState,
    onQuickSaveCustomer: (String, String, String, String, (Long) -> Unit) -> Unit,
    onCreateInvoice: (
        customer: CustomerEntity,
        items: List<DraftLineItem>,
        invoiceDate: Long,
        discount: Double,
        additionalCharges: Double,
        gstEnabled: Boolean,
        gstPercent: Double,
        paymentStatus: PaymentStatus,
        amountPaid: Double,
        notes: String,
        sourceOrderId: Long?,
        onSuccess: (InvoiceEntity) -> Unit
    ) -> Unit,
    onCancelInvoice: (Long, String) -> Unit,
    onPrintInvoice: (InvoiceEntity, Int, Boolean) -> Unit,
    onClearOrderConversion: () -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Main Billing Screen, 1 = Saved Bills
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customerSearch by remember { mutableStateOf("") }
    var showInlineAddCustomer by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var lineItems by remember { mutableStateOf<List<DraftLineItem>>(emptyList()) }
    var sourceOrderId by remember { mutableStateOf<Long?>(null) }
    var sourceOrderNumber by remember { mutableStateOf("") }

    var invoiceDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf("") }
    var extraChargesText by remember { mutableStateOf("") }
    var gstEnabled by remember { mutableStateOf(false) } // Optional GST disabled by default
    var gstPercentText by remember { mutableStateOf(Formatters.formatQty(state.business.defaultGstPercent)) }

    var paymentStatus by remember { mutableStateOf(PaymentStatus.POSTPAID) }
    var amountPaidText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Default A4 layout quick-selector on the billing bar (1, 2, or 4 identical bills per A4 sheet)
    var selectedA4Copies by remember(state.defaultTemplate.billsPerPage) {
        mutableIntStateOf(state.defaultTemplate.billsPerPage.coerceIn(1, 4))
    }

    var lastSavedInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var printDialogTarget by remember { mutableStateOf<InvoiceEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<InvoiceEntity?>(null) }

    // Pre-fill when converting a Manual Order to a Bill
    LaunchedEffect(state.pendingOrderConversion) {
        val conv = state.pendingOrderConversion ?: return@LaunchedEffect
        activeTab = 0
        selectedCustomerId = conv.order.customerId
        sourceOrderId = conv.order.id
        sourceOrderNumber = conv.order.orderNumber
        notes = conv.order.notes
        lineItems = conv.items.map {
            DraftLineItem(
                productId = it.productId,
                sellingOptionId = it.sellingOptionId,
                productName = it.productNameSnapshot,
                varietyName = it.varietyNameSnapshot,
                sellingUnit = it.sellingUnitSnapshot,
                quantity = it.quantity,
                rate = it.rate
            )
        }
    }

    val selectedCustomer = remember(selectedCustomerId, state.customers) {
        state.customers.firstOrNull { it.id == selectedCustomerId }
    }

    // Live Total Amount Calculations
    val subtotal = remember(lineItems) { Formatters.roundMoney(lineItems.sumOf { it.lineAmount }) }
    val discount = discountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val taxable = Formatters.roundMoney((subtotal - discount).coerceAtLeast(0.0))
    val gstPct = gstPercentText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val gstAmount = if (gstEnabled && gstPct > 0) Formatters.roundMoney(taxable * gstPct / 100.0) else 0.0
    val extra = extraChargesText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val grandTotal = Formatters.roundMoney(taxable + gstAmount + extra)

    LaunchedEffect(paymentStatus, grandTotal) {
        if (paymentStatus == PaymentStatus.PREPAID) {
            amountPaidText = if (grandTotal > 0) Formatters.formatQty(grandTotal) else ""
        }
    }

    val paidVal = amountPaidText.toDoubleOrNull()?.coerceIn(0.0, grandTotal) ?: 0.0
    val remainingVal = Formatters.roundMoney((grandTotal - paidVal).coerceAtLeast(0.0))

    val unitSummary = remember(lineItems) {
        lineItems.groupBy { it.sellingUnit }
            .map { (unit, list) -> "${Formatters.formatQty(list.sumOf { it.quantity })} $unit" }
            .joinToString(" • ")
    }

    fun submitBill(triggerImmediateA4Print: Boolean) {
        val cust = selectedCustomer ?: return
        onCreateInvoice(
            cust, lineItems, invoiceDate, discount, extra, gstEnabled, gstPct,
            paymentStatus, paidVal, notes, sourceOrderId
        ) { saved ->
            lastSavedInvoice = saved
            lineItems = emptyList()
            discountText = ""
            extraChargesText = ""
            amountPaidText = ""
            notes = ""
            sourceOrderId = null
            sourceOrderNumber = ""
            if (triggerImmediateA4Print) {
                onPrintInvoice(saved, selectedA4Copies, false)
            } else {
                printDialogTarget = saved
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text(s.navNewBill, fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("tab_main_billing")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("${s.tabBills} (${state.invoices.size})", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("tab_saved_bills")
            )
        }

        if (activeTab == 0) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 720.dp

                if (isWide) {
                    // Adaptive 2-Pane Main Billing Layout for Tablets / Foldables
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Pane: Customer + Added Products List
                        LazyColumn(
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (sourceOrderId != null) {
                                item {
                                    OrderConversionBanner(sourceOrderNumber) {
                                        sourceOrderId = null
                                        sourceOrderNumber = ""
                                        onClearOrderConversion()
                                    }
                                }
                            }
                            item {
                                CustomerPickerCard(
                                    state = state,
                                    selectedCustomer = selectedCustomer,
                                    customerSearch = customerSearch,
                                    onSearchChange = { customerSearch = it },
                                    onSelectCustomer = { selectedCustomerId = it; customerSearch = "" },
                                    onClearCustomer = { selectedCustomerId = null },
                                    onAddNewCustomer = { showInlineAddCustomer = true }
                                )
                            }
                            item {
                                AddedProductsHeader(
                                    itemCount = lineItems.size,
                                    unitSummary = unitSummary,
                                    onAddProduct = { showAddItemDialog = true }
                                )
                            }
                            if (lineItems.isEmpty()) {
                                item {
                                    EmptyBillProductsCard(onAddProduct = { showAddItemDialog = true })
                                }
                            } else {
                                itemsIndexed(lineItems, key = { idx, it -> "${it.sellingOptionId}_$idx" }) { idx, item ->
                                    AddedProductRowCard(
                                        item = item,
                                        onDecrement = {
                                            val newQty = Formatters.roundMoney(item.quantity - 1.0)
                                            lineItems = if (newQty <= 0.0) {
                                                lineItems.filterIndexed { i, _ -> i != idx }
                                            } else {
                                                lineItems.mapIndexed { i, l -> if (i == idx) l.copy(quantity = newQty) else l }
                                            }
                                        },
                                        onIncrement = {
                                            val newQty = Formatters.roundMoney(item.quantity + 1.0)
                                            lineItems = lineItems.mapIndexed { i, l -> if (i == idx) l.copy(quantity = newQty) else l }
                                        },
                                        onRemove = { lineItems = lineItems.filterIndexed { i, _ -> i != idx } }
                                    )
                                }
                            }
                        }

                        // Right Pane: Total Amount Calculation + Payment + A4 PDF Print Actions
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BillAdjustmentsAndPaymentCard(
                                state = state,
                                invoiceDate = invoiceDate,
                                onEditDate = { showDatePicker = true },
                                discountText = discountText,
                                onDiscountChange = { discountText = it },
                                extraChargesText = extraChargesText,
                                onExtraChargesChange = { extraChargesText = it },
                                gstEnabled = gstEnabled,
                                onGstToggle = { gstEnabled = it },
                                gstPercentText = gstPercentText,
                                onGstPercentChange = { gstPercentText = it },
                                paymentStatus = paymentStatus,
                                onPaymentStatusChange = {
                                    paymentStatus = it
                                    amountPaidText = if (it == PaymentStatus.PREPAID && grandTotal > 0) Formatters.formatQty(grandTotal) else ""
                                },
                                amountPaidText = amountPaidText,
                                onAmountPaidChange = { amountPaidText = it },
                                remainingBalance = remainingVal,
                                notes = notes,
                                onNotesChange = { notes = it }
                            )
                            TotalAndPrintA4SummaryBar(
                                subtotal = subtotal,
                                discount = discount,
                                gstEnabled = gstEnabled,
                                gstPct = gstPct,
                                gstAmount = gstAmount,
                                extra = extra,
                                grandTotal = grandTotal,
                                itemCount = lineItems.size,
                                unitSummary = unitSummary,
                                selectedA4Copies = selectedA4Copies,
                                onSelectA4Copies = { selectedA4Copies = it },
                                canSubmit = selectedCustomer != null && lineItems.isNotEmpty() && state.activeEmployee.canCreateBill,
                                lastSavedInvoice = lastSavedInvoice,
                                onSaveBill = { submitBill(triggerImmediateA4Print = false) },
                                onPrintA4Pdf = {
                                    if (selectedCustomer != null && lineItems.isNotEmpty() && state.activeEmployee.canCreateBill) {
                                        submitBill(triggerImmediateA4Print = true)
                                    } else {
                                        lastSavedInvoice?.let { printDialogTarget = it }
                                    }
                                },
                                onOpenPrintOptions = { lastSavedInvoice?.let { printDialogTarget = it } }
                            )
                        }
                    }
                } else {
                    // Compact Phone Main Billing Screen: Scrollable Added Products + Sticky Bottom Total & A4 Print Bar
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (sourceOrderId != null) {
                                item {
                                    OrderConversionBanner(sourceOrderNumber) {
                                        sourceOrderId = null
                                        sourceOrderNumber = ""
                                        onClearOrderConversion()
                                    }
                                }
                            }

                            // 1. Customer Selector
                            item {
                                CustomerPickerCard(
                                    state = state,
                                    selectedCustomer = selectedCustomer,
                                    customerSearch = customerSearch,
                                    onSearchChange = { customerSearch = it },
                                    onSelectCustomer = { selectedCustomerId = it; customerSearch = "" },
                                    onClearCustomer = { selectedCustomerId = null },
                                    onAddNewCustomer = { showInlineAddCustomer = true }
                                )
                            }

                            // 2. Added Products List Header + Add Button
                            item {
                                AddedProductsHeader(
                                    itemCount = lineItems.size,
                                    unitSummary = unitSummary,
                                    onAddProduct = { showAddItemDialog = true }
                                )
                            }

                            // 3. Added Products List
                            if (lineItems.isEmpty()) {
                                item {
                                    EmptyBillProductsCard(onAddProduct = { showAddItemDialog = true })
                                }
                            } else {
                                itemsIndexed(lineItems, key = { idx, it -> "${it.sellingOptionId}_$idx" }) { idx, item ->
                                    AddedProductRowCard(
                                        item = item,
                                        onDecrement = {
                                            val newQty = Formatters.roundMoney(item.quantity - 1.0)
                                            lineItems = if (newQty <= 0.0) {
                                                lineItems.filterIndexed { i, _ -> i != idx }
                                            } else {
                                                lineItems.mapIndexed { i, l -> if (i == idx) l.copy(quantity = newQty) else l }
                                            }
                                        },
                                        onIncrement = {
                                            val newQty = Formatters.roundMoney(item.quantity + 1.0)
                                            lineItems = lineItems.mapIndexed { i, l -> if (i == idx) l.copy(quantity = newQty) else l }
                                        },
                                        onRemove = { lineItems = lineItems.filterIndexed { i, _ -> i != idx } }
                                    )
                                }
                            }

                            // 4. Date, Optional GST, Discount & Prepaid/Postpaid Payment Card
                            item {
                                BillAdjustmentsAndPaymentCard(
                                    state = state,
                                    invoiceDate = invoiceDate,
                                    onEditDate = { showDatePicker = true },
                                    discountText = discountText,
                                    onDiscountChange = { discountText = it },
                                    extraChargesText = extraChargesText,
                                    onExtraChargesChange = { extraChargesText = it },
                                    gstEnabled = gstEnabled,
                                    onGstToggle = { gstEnabled = it },
                                    gstPercentText = gstPercentText,
                                    onGstPercentChange = { gstPercentText = it },
                                    paymentStatus = paymentStatus,
                                    onPaymentStatusChange = {
                                        paymentStatus = it
                                        amountPaidText = if (it == PaymentStatus.PREPAID && grandTotal > 0) Formatters.formatQty(grandTotal) else ""
                                    },
                                    amountPaidText = amountPaidText,
                                    onAmountPaidChange = { amountPaidText = it },
                                    remainingBalance = remainingVal,
                                    notes = notes,
                                    onNotesChange = { notes = it }
                                )
                            }

                            item { Spacer(Modifier.height(8.dp)) }
                        }

                        // Sticky Bottom Total Amount Calculation & A4 PDF Print Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            TotalAndPrintA4SummaryBar(
                                subtotal = subtotal,
                                discount = discount,
                                gstEnabled = gstEnabled,
                                gstPct = gstPct,
                                gstAmount = gstAmount,
                                extra = extra,
                                grandTotal = grandTotal,
                                itemCount = lineItems.size,
                                unitSummary = unitSummary,
                                selectedA4Copies = selectedA4Copies,
                                onSelectA4Copies = { selectedA4Copies = it },
                                canSubmit = selectedCustomer != null && lineItems.isNotEmpty() && state.activeEmployee.canCreateBill,
                                lastSavedInvoice = lastSavedInvoice,
                                onSaveBill = { submitBill(triggerImmediateA4Print = false) },
                                onPrintA4Pdf = {
                                    if (selectedCustomer != null && lineItems.isNotEmpty() && state.activeEmployee.canCreateBill) {
                                        submitBill(triggerImmediateA4Print = true)
                                    } else {
                                        lastSavedInvoice?.let { printDialogTarget = it }
                                    }
                                },
                                onOpenPrintOptions = { lastSavedInvoice?.let { printDialogTarget = it } },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            SavedBillsHistoryTab(
                state = state,
                onCancel = { cancelTarget = it },
                onPrint = { printDialogTarget = it }
            )
        }
    }

    if (showInlineAddCustomer) {
        AddEditCustomerDialog(
            existing = null,
            existingCustomers = state.customers,
            onDismiss = { showInlineAddCustomer = false },
            onSave = { name, shop, mob, addr ->
                onQuickSaveCustomer(name, shop, mob, addr) { newId ->
                    selectedCustomerId = newId
                }
                showInlineAddCustomer = false
            }
        )
    }

    if (showAddItemDialog) {
        AddLineItemDialog(
            state = state,
            canEditRate = state.activeEmployee.canEditPrices,
            onDismiss = { showAddItemDialog = false },
            onAdd = { item ->
                lineItems = lineItems + item
                showAddItemDialog = false
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = invoiceDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { invoiceDate = it }
                    showDatePicker = false
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(s.cancel) } }
        ) { DatePicker(state = pickerState) }
    }

    printDialogTarget?.let { inv ->
        PrintInvoiceDialog(
            invoice = inv,
            onDismiss = { printDialogTarget = null },
            onPrint = { copies, share -> onPrintInvoice(inv, copies, share) }
        )
    }

    cancelTarget?.let { inv ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("${s.cancelInvoice} ${inv.invoiceNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cancelling restores stock and posts a reversing ledger entry (keeps full audit history).")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelInvoice(inv.id, reason.ifBlank { "Cancelled by user" })
                        cancelTarget = null
                    },
                    enabled = reason.isNotBlank()
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun OrderConversionBanner(orderNumber: String, onCancel: () -> Unit) {
    val s = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text("Converting Order $orderNumber", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(s.cancel) }
        }
    }
}

@Composable
private fun CustomerPickerCard(
    state: AppUiState,
    selectedCustomer: CustomerEntity?,
    customerSearch: String,
    onSearchChange: (String) -> Unit,
    onSelectCustomer: (Long) -> Unit,
    onClearCustomer: () -> Unit,
    onAddNewCustomer: () -> Unit
) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("1. ${s.selectCustomer}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = onAddNewCustomer,
                    modifier = Modifier.testTag("bill_inline_add_customer")
                ) {
                    Text(s.addNewCustomerInline, fontWeight = FontWeight.Bold)
                }
            }

            if (selectedCustomer != null) {
                val bal = state.customerBalance(selectedCustomer.id)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedCustomer.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedCustomer.name} • ${selectedCustomer.mobile}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${s.outstandingBalance}: ${Formatters.formatRupee(bal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (bal > 0) DebitRed else CreditGreen
                            )
                        }
                        IconButton(onClick = onClearCustomer) {
                            Icon(Icons.Default.Close, contentDescription = "Change customer")
                        }
                    }
                }
            } else {
                SearchField(
                    value = customerSearch,
                    onValueChange = onSearchChange,
                    placeholder = s.searchCustomerHint,
                    tag = "bill_customer_search"
                )
                val matches = remember(customerSearch, state.customers) {
                    state.customers.filter {
                        Formatters.matchesSearch(customerSearch, it.name, it.shopName, it.mobile)
                    }.take(5)
                }
                matches.forEach { c ->
                    val bal = state.customerBalance(c.id)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectCustomer(c.id) }
                            .testTag("bill_pick_customer_${c.id}"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.shopName, fontWeight = FontWeight.Bold)
                                Text("${c.name} • ${c.mobile}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Formatters.formatRupee(bal), fontWeight = FontWeight.Bold, color = if (bal > 0) DebitRed else CreditGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddedProductsHeader(
    itemCount: Int,
    unitSummary: String,
    onAddProduct: () -> Unit
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "2. ${s.billItems} ($itemCount)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (unitSummary.isNotBlank()) {
                Text(
                    unitSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Button(
            onClick = onAddProduct,
            modifier = Modifier.heightIn(min = 48.dp).testTag("bill_add_item_btn")
        ) {
            Icon(Icons.Default.Add, contentDescription = s.addItem)
            Spacer(Modifier.width(4.dp))
            Text(s.add)
        }
    }
}

@Composable
private fun EmptyBillProductsCard(onAddProduct: () -> Unit) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.ShoppingBasket,
                contentDescription = s.billItems,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            )
            Text(
                s.addItem,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onAddProduct,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(s.addItem, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AddedProductRowCard(
    item: DraftLineItem,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onRemove: () -> Unit
) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("added_product_${item.sellingOptionId}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = "${item.varietyName} • ${item.sellingUnit}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "@ ${Formatters.formatRupee(item.rate)}/${item.sellingUnit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = s.delete, tint = DebitRed)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large touch-friendly inline quantity stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrement,
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                    }
                    Text(
                        "${Formatters.formatQty(item.quantity)} ${item.sellingUnit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalIconButton(
                        onClick = onIncrement,
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                    }
                }

                // Line amount calculation
                Text(
                    Formatters.formatRupee(item.lineAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BillAdjustmentsAndPaymentCard(
    state: AppUiState,
    invoiceDate: Long,
    onEditDate: () -> Unit,
    discountText: String,
    onDiscountChange: (String) -> Unit,
    extraChargesText: String,
    onExtraChargesChange: (String) -> Unit,
    gstEnabled: Boolean,
    onGstToggle: (Boolean) -> Unit,
    gstPercentText: String,
    onGstPercentChange: (String) -> Unit,
    paymentStatus: PaymentStatus,
    onPaymentStatusChange: (PaymentStatus) -> Unit,
    amountPaidText: String,
    onAmountPaidChange: (String) -> Unit,
    remainingBalance: Double,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${s.invoiceDate}: ${Formatters.formatDate(invoiceDate)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (state.activeEmployee.canEditInvoiceDate) {
                    TextButton(onClick = onEditDate) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = s.invoiceDate, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.edit)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = discountText,
                    onValueChange = onDiscountChange,
                    label = { Text(s.discount) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = extraChargesText,
                    onValueChange = onExtraChargesChange,
                    label = { Text(s.additionalCharges) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Optional GST checkbox (Disabled by default)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onGstToggle(!gstEnabled) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = gstEnabled, onCheckedChange = onGstToggle, modifier = Modifier.testTag("gst_checkbox"))
                Text(s.enableGst, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (gstEnabled) {
                    OutlinedTextField(
                        value = gstPercentText,
                        onValueChange = onGstPercentChange,
                        label = { Text(s.gstPercent) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp)
                    )
                }
            }

            HorizontalDivider()

            Text("3. ${s.paymentStatusLabel}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = paymentStatus == PaymentStatus.PREPAID,
                    onClick = { onPaymentStatusChange(PaymentStatus.PREPAID) },
                    label = { Text(s.prepaid, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("status_prepaid")
                )
                FilterChip(
                    selected = paymentStatus == PaymentStatus.POSTPAID,
                    onClick = { onPaymentStatusChange(PaymentStatus.POSTPAID) },
                    label = { Text(s.postpaid, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("status_postpaid")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amountPaidText,
                    onValueChange = onAmountPaidChange,
                    label = { Text("${s.amountPaid} (${s.optional})") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f).testTag("bill_amount_paid_input")
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(s.remainingBalance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formatters.formatRupee(remainingBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingBalance > 0.01) DebitRed else CreditGreen
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("${s.notes} (${s.optional})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Sticky / Side Total Amount Calculation & A4 PDF Print Action Bar
 */
@Composable
private fun TotalAndPrintA4SummaryBar(
    subtotal: Double,
    discount: Double,
    gstEnabled: Boolean,
    gstPct: Double,
    gstAmount: Double,
    extra: Double,
    grandTotal: Double,
    itemCount: Int,
    unitSummary: String,
    selectedA4Copies: Int,
    onSelectA4Copies: (Int) -> Unit,
    canSubmit: Boolean,
    lastSavedInvoice: InvoiceEntity?,
    onSaveBill: () -> Unit,
    onPrintA4Pdf: () -> Unit,
    onOpenPrintOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Compact breakdown when adjustments/GST apply
        if (discount > 0 || (gstEnabled && gstAmount > 0) || extra > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${s.subtotal}: ${Formatters.formatRupee(subtotal)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (discount > 0) Text("${s.discount}: -${Formatters.formatRupee(discount)}", style = MaterialTheme.typography.labelMedium, color = CreditGreen)
                if (gstEnabled && gstAmount > 0) Text("GST (${Formatters.formatQty(gstPct)}%): +${Formatters.formatRupee(gstAmount)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (extra > 0) Text("+${Formatters.formatRupee(extra)}", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Grand Total Calculation Row + A4 Copy Selector (1 / 2 / 4 Identical Bills on A4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (itemCount > 0) "${s.grandTotal} ($itemCount ${s.billItems})" else s.grandTotal,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Formatters.formatRupee(grandTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("billing_grand_total")
                )
                if (unitSummary.isNotBlank()) {
                    Text(unitSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Quick A4 layout toggle: 1-up, 2-up, or 4-up identical bills
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1 to "1×A4", 2 to "2×A4", 4 to "4×A4").forEach { (count, label) ->
                    FilterChip(
                        selected = selectedA4Copies == count,
                        onClick = { onSelectA4Copies(count) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("a4_mode_$count")
                    )
                }
            }
        }

        // Primary Action Buttons: Save Bill + Print A4 PDF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSaveBill,
                enabled = canSubmit,
                modifier = Modifier
                    .weight(0.9f)
                    .height(54.dp)
                    .testTag("save_bill_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = s.saveBill, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.saveBill, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            val printEnabled = canSubmit || lastSavedInvoice != null
            Button(
                onClick = onPrintA4Pdf,
                enabled = printEnabled,
                modifier = Modifier
                    .weight(1.25f)
                    .height(54.dp)
                    .testTag("print_a4_pdf_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = "Print A4 PDF", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (canSubmit) "${s.print} A4 PDF (${selectedA4Copies}×)"
                    else "${s.print} ${lastSavedInvoice?.invoiceNumber ?: "A4 PDF"}",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!canSubmit && lastSavedInvoice != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Last bill: ${lastSavedInvoice.invoiceNumber} (${Formatters.formatRupee(lastSavedInvoice.grandTotal)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreditGreen,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onOpenPrintOptions) {
                    Text("A4 Print Options")
                }
            }
        }
    }
}

@Composable
private fun SavedBillsHistoryTab(
    state: AppUiState,
    onCancel: (InvoiceEntity) -> Unit,
    onPrint: (InvoiceEntity) -> Unit
) {
    val s = LocalStrings.current
    var billSearch by remember { mutableStateOf("") }
    val filteredBills = remember(state.invoices, billSearch) {
        state.invoices.filter {
            Formatters.matchesSearch(billSearch, it.invoiceNumber, it.customerNameSnapshot, it.shopNameSnapshot, it.customerMobileSnapshot)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(billSearch, { billSearch = it }, "${s.search} ${s.tabBills}...")
        if (filteredBills.isEmpty()) {
            EmptyStateCard(Icons.Default.ReceiptLong, s.noRecordsYet)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredBills, key = { it.id }) { inv ->
                    val cancelled = inv.status == InvoiceStatus.CANCELLED.name
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(inv.invoiceNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    StatusPill(
                                        if (cancelled) "CANCELLED" else s.paymentStatusText(inv.paymentStatus),
                                        if (cancelled) DebitRed.copy(alpha = 0.14f) else if (inv.remainingBalance <= 0.01) CreditGreen.copy(alpha = 0.14f) else WarningAmber.copy(alpha = 0.16f),
                                        if (cancelled) DebitRed else if (inv.remainingBalance <= 0.01) CreditGreen else WarningAmber
                                    )
                                }
                                Text(Formatters.formatRupee(inv.grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("${inv.shopNameSnapshot} (${inv.customerNameSnapshot})", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${Formatters.formatDate(inv.invoiceDate)} • Paid: ${Formatters.formatRupee(inv.amountPaid)} • Bal: ${Formatters.formatRupee(inv.remainingBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (!cancelled && state.activeEmployee.canCancelInvoice) {
                                    TextButton(onClick = { onCancel(inv) }) {
                                        Text(s.cancelInvoice, color = DebitRed)
                                    }
                                }
                                FilledTonalButton(onClick = { onPrint(inv) }) {
                                    Icon(Icons.Default.Print, contentDescription = s.print, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${s.print} A4 PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddLineItemDialog(
    state: AppUiState,
    canEditRate: Boolean,
    usePurchasePrice: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (DraftLineItem) -> Unit
) {
    val s = LocalStrings.current
    var prodSearch by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var selectedOption by remember { mutableStateOf<SellingOptionEntity?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var rateText by remember { mutableStateOf("") }

    val matchingProducts = remember(prodSearch, state.products) {
        state.products.filter { Formatters.matchesSearch(prodSearch, it.name, it.categoryName) }
    }
    val productOptions = remember(selectedProduct, state.sellingOptions) {
        selectedProduct?.let { state.activeOptionsForProduct(it.id) } ?: emptyList()
    }

    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val rate = rateText.toDoubleOrNull() ?: 0.0
    val lineAmt = Formatters.multiplyMoney(qty, rate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addItem, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedProduct == null) {
                    SearchField(prodSearch, { prodSearch = it }, s.searchProductHint, tag = "item_product_search")
                    matchingProducts.take(8).forEach { prod ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedProduct = prod
                                    val firstOpt = state.activeOptionsForProduct(prod.id).firstOrNull()
                                    selectedOption = firstOpt
                                    rateText = firstOpt?.let {
                                        val r = if (usePurchasePrice && it.purchasePrice > 0) it.purchasePrice else it.sellingPrice
                                        Formatters.formatQty(r)
                                    } ?: ""
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(prod.name, fontWeight = FontWeight.Bold)
                                Text(prod.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedProduct!!.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selectedProduct = null; selectedOption = null }) { Text("Change") }
                        }
                    }

                    Text(s.selectSellingOption, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (productOptions.isEmpty()) {
                        Text("No active selling options for this product. Add one in Products.", color = DebitRed)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            productOptions.forEach { opt ->
                                val avail = state.availableStockForOption(selectedProduct!!, opt)
                                val chosen = selectedOption?.id == opt.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedOption = opt
                                            val r = if (usePurchasePrice && opt.purchasePrice > 0) opt.purchasePrice else opt.sellingPrice
                                            rateText = Formatters.formatQty(r)
                                        },
                                    color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${opt.varietyName} (${opt.sellingUnit})", fontWeight = FontWeight.Bold)
                                            Text("Stock: ${Formatters.formatQty(avail)} ${opt.sellingUnit}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("${Formatters.formatRupee(opt.sellingPrice)}/${opt.sellingUnit}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (selectedOption != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = { qtyText = it },
                                label = { Text("${s.qty} (${selectedOption!!.sellingUnit})") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("item_qty_input")
                            )
                            OutlinedTextField(
                                value = rateText,
                                onValueChange = { if (canEditRate || usePurchasePrice) rateText = it },
                                label = { Text("${s.rate} (₹)") },
                                singleLine = true,
                                readOnly = !canEditRate && !usePurchasePrice,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(1, 2, 5, 10, 20, 50).forEach { q ->
                                AssistChip(onClick = { qtyText = q.toString() }, label = { Text("$q") })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(Formatters.formatRupee(lineAmt), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = selectedProduct ?: return@Button
                    val o = selectedOption ?: return@Button
                    onAdd(DraftLineItem(p.id, o.id, p.name, o.varietyName, o.sellingUnit, qty, rate))
                },
                enabled = selectedProduct != null && selectedOption != null && qty > 0.0 && rate >= 0.0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_add_item_btn")
            ) { Text(s.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
