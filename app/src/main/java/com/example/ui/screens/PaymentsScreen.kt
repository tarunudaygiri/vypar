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
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomerEntity
import com.example.data.db.PaymentEntity
import com.example.data.model.InvoiceStatus
import com.example.data.model.PaymentMethod
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun PaymentsScreen(
    state: AppUiState,
    onRecordPayment: (
        customer: CustomerEntity,
        paymentDate: Long,
        amount: Double,
        method: PaymentMethod,
        ref: String,
        invoiceIds: List<Long>,
        notes: String,
        onSuccess: (PaymentEntity) -> Unit
    ) -> Unit,
    onPrintReceipt: (PaymentEntity, Boolean) -> Unit
) {
    val s = LocalStrings.current
    var search by remember { mutableStateOf("") }
    var showRecordDialog by remember { mutableStateOf(false) }

    val totalReceived = remember(state.payments) { state.payments.sumOf { it.amountReceived } }
    val totalAdvances = remember(state.payments) { state.payments.sumOf { it.unallocatedAdvance } }
    val filteredPayments = remember(state.payments, search) {
        state.payments.filter {
            Formatters.matchesSearch(search, it.receiptNumber, it.customerNameSnapshot, it.shopNameSnapshot, it.paymentMethod, it.referenceNumber)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.activeEmployee.canRecordPayment) {
                ExtendedFloatingActionButton(
                    onClick = { showRecordDialog = true },
                    icon = { Icon(Icons.Default.Payments, contentDescription = s.recordPayment) },
                    text = { Text(s.recordPayment) },
                    modifier = Modifier.testTag("record_payment_fab")
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
            // Manual Recording Policy Banner (No bank/UPI connections)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(s.manualMethodNotice, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${s.paymentsReceived}: ${Formatters.formatRupee(totalReceived)}  •  ${s.unallocatedAdvance}: ${Formatters.formatRupee(totalAdvances)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SearchField(search, { search = it }, "${s.search} ${s.navPayments}...")

            if (filteredPayments.isEmpty()) {
                EmptyStateCard(Icons.Default.Payments, s.noRecordsYet, s.recordPayment) { showRecordDialog = true }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredPayments, key = { it.id }) { pay ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(pay.receiptNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        StatusPill(pay.paymentMethod, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    Text("${pay.shopNameSnapshot} (${pay.customerNameSnapshot})", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        buildString {
                                            append(Formatters.formatDate(pay.paymentDate))
                                            if (pay.relatedInvoiceNumber.isNotBlank()) append(" • Bill: ${pay.relatedInvoiceNumber}")
                                            if (pay.referenceNumber.isNotBlank()) append(" • Ref: ${pay.referenceNumber}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (pay.unallocatedAdvance > 0.01) {
                                        Text(
                                            "${s.unallocatedAdvance}: ${Formatters.formatRupee(pay.unallocatedAdvance)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CreditGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Formatters.formatRupee(pay.amountReceived), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CreditGreen)
                                    Row {
                                        IconButton(onClick = { onPrintReceipt(pay, false) }) {
                                            Icon(Icons.Default.Print, contentDescription = s.printReceipt, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onPrintReceipt(pay, true) }) {
                                            Icon(Icons.Default.Share, contentDescription = s.sharePdf)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecordDialog) {
        RecordPaymentDialog(
            state = state,
            onDismiss = { showRecordDialog = false },
            onSave = { cust, date, amt, method, ref, invIds, notes ->
                onRecordPayment(cust, date, amt, method, ref, invIds, notes) {
                    showRecordDialog = false
                }
            }
        )
    }
}

@Composable
private fun RecordPaymentDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity, Long, Double, PaymentMethod, String, List<Long>, String) -> Unit
) {
    val s = LocalStrings.current
    var customerId by remember { mutableStateOf<Long?>(null) }
    var custSearch by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var refNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedInvoiceIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val customer = state.customers.firstOrNull { it.id == customerId }
    val unpaidBills = remember(customer, state.invoices) {
        customer?.let { c ->
            state.invoices.filter { it.customerId == c.id && it.status != InvoiceStatus.CANCELLED.name && it.remainingBalance > 0.01 }
        } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.recordPayment, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.manualMethodNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                if (customer != null) {
                    val bal = state.customerBalance(customer.id)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(customer.shopName, fontWeight = FontWeight.Bold)
                                Text("${s.outstandingBalance}: ${Formatters.formatRupee(bal)}", color = if (bal > 0) DebitRed else CreditGreen, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { customerId = null; selectedInvoiceIds = emptySet() }) { Text("Change") }
                        }
                    }
                } else {
                    SearchField(custSearch, { custSearch = it }, s.searchCustomerHint)
                    state.customers.filter { Formatters.matchesSearch(custSearch, it.name, it.shopName, it.mobile) }.take(5).forEach { c ->
                        val bal = state.customerBalance(c.id)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                customerId = c.id
                                if (bal > 0) amountText = Formatters.formatQty(bal)
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.shopName, fontWeight = FontWeight.SemiBold)
                                Text(Formatters.formatRupee(bal), color = if (bal > 0) DebitRed else CreditGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("${s.amountReceived} *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )

                Text(s.paymentMethod, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(s.paymentMethodLabel(m)) }
                        )
                    }
                }

                if (unpaidBills.isNotEmpty()) {
                    Text(s.allocateInvoices, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    unpaidBills.forEach { bill ->
                        val checked = bill.id in selectedInvoiceIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedInvoiceIds = if (checked) selectedInvoiceIds - bill.id else selectedInvoiceIds + bill.id
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedInvoiceIds = if (it) selectedInvoiceIds + bill.id else selectedInvoiceIds - bill.id
                                }
                            )
                            Text("${bill.invoiceNumber} — Bal: ${Formatters.formatRupee(bill.remainingBalance)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = refNo,
                    onValueChange = { refNo = it },
                    label = { Text(s.transactionRef) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("${s.notes} (${s.optional})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    customer?.let { onSave(it, System.currentTimeMillis(), amt, method, refNo, selectedInvoiceIds.toList(), notes) }
                },
                enabled = customer != null && amt > 0.0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_payment_button")
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
