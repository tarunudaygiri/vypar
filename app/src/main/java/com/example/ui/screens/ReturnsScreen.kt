package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.InvoiceEntity
import com.example.data.model.InvoiceStatus
import com.example.data.model.ReturnCondition
import com.example.data.model.ReturnSettlementType
import com.example.data.repository.DraftReturnItem
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun ReturnsScreen(
    state: AppUiState,
    onRecordReturn: (InvoiceEntity, List<DraftReturnItem>, ReturnSettlementType, String, () -> Unit) -> Unit
) {
    val s = LocalStrings.current
    var showNewReturn by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewReturn = true },
                icon = { Icon(Icons.Default.AssignmentReturn, contentDescription = s.newReturn) },
                text = { Text(s.newReturn) },
                modifier = Modifier.testTag("new_return_fab")
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.returns.isEmpty()) {
                item { EmptyStateCard(Icons.Default.AssignmentReturn, s.noRecordsYet, s.newReturn) { showNewReturn = true } }
            } else {
                items(state.returns, key = { it.id }) { ret ->
                    val items = state.returnItems.filter { it.returnId == ret.id }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ret.creditNoteNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    StatusPill(ret.settlementType, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Text(Formatters.formatRupee(ret.totalReturnAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DebitRed)
                            }
                            Text("${ret.shopNameSnapshot} • Ref: ${ret.originalInvoiceNumber}", fontWeight = FontWeight.SemiBold)
                            Text("${Formatters.formatDate(ret.returnDate)} • ${ret.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            items.forEach { it ->
                                val isSellable = it.condition == ReturnCondition.SELLABLE.name
                                Text(
                                    "• ${it.productNameSnapshot} (${it.varietyNameSnapshot}) — ${Formatters.formatQty(it.returnedQty)} ${it.sellingUnitSnapshot} [${if (isSellable) "Restocked" else "Damaged - Not Restocked"}]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSellable) CreditGreen else DebitRed
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showNewReturn) {
        RecordReturnDialog(
            state = state,
            onDismiss = { showNewReturn = false },
            onSave = { inv, items, settle, reason ->
                onRecordReturn(inv, items, settle, reason) { showNewReturn = false }
            }
        )
    }
}

@Composable
private fun RecordReturnDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSave: (InvoiceEntity, List<DraftReturnItem>, ReturnSettlementType, String) -> Unit
) {
    val s = LocalStrings.current
    val activeInvoices = remember(state.invoices) { state.invoices.filter { it.status != InvoiceStatus.CANCELLED.name } }
    var selectedInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var draftItems by remember { mutableStateOf<List<DraftReturnItem>>(emptyList()) }
    var settlement by remember { mutableStateOf(ReturnSettlementType.CUSTOMER_CREDIT) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newReturn, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedInvoice == null) {
                    Text(s.selectInvoice, fontWeight = FontWeight.Bold)
                    if (activeInvoices.isEmpty()) {
                        Text(s.noRecordsYet)
                    } else {
                        activeInvoices.take(8).forEach { inv ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                    selectedInvoice = inv
                                    draftItems = state.invoiceItems.filter { it.invoiceId == inv.id }.map {
                                        DraftReturnItem(it.productId, it.sellingOptionId, it.productNameSnapshot, it.varietyNameSnapshot, it.sellingUnitSnapshot, 0.0, it.rate, ReturnCondition.SELLABLE)
                                    }
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("${inv.invoiceNumber} • ${inv.shopNameSnapshot}", fontWeight = FontWeight.Bold)
                                        Text(Formatters.formatDate(inv.invoiceDate), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(Formatters.formatRupee(inv.grandTotal), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${selectedInvoice!!.invoiceNumber} — ${selectedInvoice!!.shopNameSnapshot}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selectedInvoice = null; draftItems = emptyList() }) { Text("Change") }
                        }
                    }

                    draftItems.forEachIndexed { idx, item ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${item.productName} (${item.varietyName})", fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = if (item.returnedQty > 0) Formatters.formatQty(item.returnedQty) else "",
                                    onValueChange = { txt ->
                                        val q = txt.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                        draftItems = draftItems.mapIndexed { i, it -> if (i == idx) it.copy(returnedQty = q) else it }
                                    },
                                    label = { Text("${s.returnQty} (${item.sellingUnit}) @ ${Formatters.formatRupee(item.rate)}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = item.condition == ReturnCondition.SELLABLE,
                                        onClick = { draftItems = draftItems.mapIndexed { i, it -> if (i == idx) it.copy(condition = ReturnCondition.SELLABLE) else it } },
                                        label = { Text(s.sellableRestock) }
                                    )
                                    FilterChip(
                                        selected = item.condition == ReturnCondition.DAMAGED,
                                        onClick = { draftItems = draftItems.mapIndexed { i, it -> if (i == idx) it.copy(condition = ReturnCondition.DAMAGED) else it } },
                                        label = { Text(s.damagedNoRestock) }
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settlement == ReturnSettlementType.CUSTOMER_CREDIT, onClick = { settlement = ReturnSettlementType.CUSTOMER_CREDIT }, label = { Text(s.customerCredit) })
                        FilterChip(selected = settlement == ReturnSettlementType.CASH_REFUND, onClick = { settlement = ReturnSettlementType.CASH_REFUND }, label = { Text(s.cashRefund) })
                    }

                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("${s.returnReason} *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    val totalRet = Formatters.roundMoney(draftItems.sumOf { it.amount })
                    Text("${s.creditNote} ${s.total}: ${Formatters.formatRupee(totalRet)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedInvoice?.let { onSave(it, draftItems, settlement, reason) } },
                enabled = selectedInvoice != null && draftItems.any { it.returnedQty > 0 } && reason.isNotBlank()
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
