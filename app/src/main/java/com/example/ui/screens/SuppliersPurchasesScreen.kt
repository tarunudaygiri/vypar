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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.SupplierEntity
import com.example.data.repository.DraftLineItem
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchField
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun SuppliersPurchasesScreen(
    state: AppUiState,
    initialTab: Int = 0, // 0 = Suppliers, 1 = Purchases
    onSaveSupplier: (SupplierEntity) -> Unit,
    onRecordPurchase: (SupplierEntity, String, Long, List<DraftLineItem>, Double, String, () -> Unit) -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var search by remember { mutableStateOf("") }
    var showAddSupplier by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var showNewPurchase by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (activeTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSupplier = true },
                    icon = { Icon(Icons.Default.LocalShipping, contentDescription = s.addSupplier) },
                    text = { Text(s.addSupplier) },
                    modifier = Modifier.testTag("add_supplier_fab")
                )
            } else if (state.activeEmployee.canManageStock) {
                ExtendedFloatingActionButton(
                    onClick = { showNewPurchase = true },
                    icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = s.newPurchase) },
                    text = { Text(s.newPurchase) },
                    modifier = Modifier.testTag("new_purchase_fab")
                )
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("${s.navSuppliers} (${state.suppliers.size})", fontWeight = FontWeight.Bold) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("${s.navPurchases} (${state.purchases.size})", fontWeight = FontWeight.Bold) })
            }

            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(search, { search = it }, "${s.search}...")

                if (activeTab == 0) {
                    val filtered = state.suppliers.filter { Formatters.matchesSearch(search, it.name, it.businessName, it.mobile) }
                    if (filtered.isEmpty()) {
                        EmptyStateCard(Icons.Default.LocalShipping, s.noRecordsYet, s.addSupplier) { showAddSupplier = true }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 84.dp)) {
                            items(filtered, key = { it.id }) { sup ->
                                val supPurchases = state.purchases.filter { it.supplierId == sup.id }
                                val payable = supPurchases.sumOf { it.remainingPayable }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { editingSupplier = sup },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(sup.businessName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("${sup.name} • ${sup.mobile}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (sup.gstin.isNotBlank()) Text("GSTIN: ${sup.gstin}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(Formatters.formatRupee(payable), fontWeight = FontWeight.Bold, color = if (payable > 0) DebitRed else CreditGreen)
                                            Text("${s.payableBalance} (${supPurchases.size})", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val filtered = state.purchases.filter { Formatters.matchesSearch(search, it.purchaseInvoiceNumber, it.supplierNameSnapshot, it.supplierBusinessSnapshot) }
                    if (filtered.isEmpty()) {
                        EmptyStateCard(Icons.Default.ShoppingCart, s.noRecordsYet, s.newPurchase) { showNewPurchase = true }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 84.dp)) {
                            items(filtered, key = { it.id }) { pur ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(pur.purchaseInvoiceNumber, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(Formatters.formatRupee(pur.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${pur.supplierBusinessSnapshot} (${pur.supplierNameSnapshot})", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${Formatters.formatDate(pur.purchaseDate)} • Paid: ${Formatters.formatRupee(pur.amountPaid)} • ${s.remainingPayable}: ${Formatters.formatRupee(pur.remainingPayable)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSupplier || editingSupplier != null) {
        val ex = editingSupplier
        var name by remember(ex) { mutableStateOf(ex?.name ?: "") }
        var biz by remember(ex) { mutableStateOf(ex?.businessName ?: "") }
        var mob by remember(ex) { mutableStateOf(ex?.mobile ?: "") }
        var addr by remember(ex) { mutableStateOf(ex?.address ?: "") }
        var gstin by remember(ex) { mutableStateOf(ex?.gstin ?: "") }

        AlertDialog(
            onDismissRequest = { showAddSupplier = false; editingSupplier = null },
            title = { Text(if (ex == null) s.addSupplier else "${s.edit} ${s.navSuppliers}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("${s.supplierName} *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = biz, onValueChange = { biz = it }, label = { Text("${s.businessName} *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mob, onValueChange = { mob = it }, label = { Text("${s.mobileNumber} *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text(s.address) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gstin, onValueChange = { gstin = it }, label = { Text(s.gstinOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSupplier((ex ?: SupplierEntity(name = "", businessName = "", mobile = "")).copy(
                            name = name.trim(), businessName = biz.trim(), mobile = mob.trim(), address = addr.trim(), gstin = gstin.trim()
                        ))
                        showAddSupplier = false; editingSupplier = null
                    },
                    enabled = name.isNotBlank() && biz.isNotBlank() && mob.isNotBlank()
                ) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showAddSupplier = false; editingSupplier = null }) { Text(s.cancel) } }
        )
    }

    if (showNewPurchase) {
        NewPurchaseDialog(
            state = state,
            onDismiss = { showNewPurchase = false },
            onSave = { sup, invNo, items, paid, notes ->
                onRecordPurchase(sup, invNo, System.currentTimeMillis(), items, paid, notes) {
                    showNewPurchase = false
                }
            }
        )
    }
}

@Composable
private fun NewPurchaseDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSave: (SupplierEntity, String, List<DraftLineItem>, Double, String) -> Unit
) {
    val s = LocalStrings.current
    var supplierId by remember { mutableStateOf(state.suppliers.firstOrNull()?.id) }
    var invNo by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<DraftLineItem>>(emptyList()) }
    var showAddItem by remember { mutableStateOf(false) }
    var paidText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val selectedSupplier = state.suppliers.firstOrNull { it.id == supplierId }
    val total = Formatters.roundMoney(items.sumOf { it.lineAmount })
    val paid = paidText.toDoubleOrNull()?.coerceIn(0.0, total) ?: 0.0
    val payable = Formatters.roundMoney((total - paid).coerceAtLeast(0.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newPurchase, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.navSuppliers, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (state.suppliers.isEmpty()) {
                    Text("Add a supplier first in the Suppliers tab.", color = DebitRed)
                } else {
                    state.suppliers.take(6).forEach { sup ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { supplierId = sup.id },
                            color = if (supplierId == sup.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text("${sup.businessName} (${sup.name})", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedTextField(value = invNo, onValueChange = { invNo = it }, label = { Text(s.purchaseInvoiceNo) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.billItems} (${items.size})", fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { showAddItem = true }) { Text("+ ${s.add}") }
                }
                items.forEachIndexed { idx, line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.SemiBold)
                            Text("${line.varietyName} • ${Formatters.formatQty(line.quantity)} ${line.sellingUnit} × ${Formatters.formatRupee(line.rate)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Formatters.formatRupee(line.lineAmount), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { items = items.filterIndexed { i, _ -> i != idx } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = s.delete, tint = DebitRed)
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.total, fontWeight = FontWeight.Bold)
                    Text(Formatters.formatRupee(total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(value = paidText, onValueChange = { paidText = it }, label = { Text(s.amountPaid) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.remainingPayable, fontWeight = FontWeight.SemiBold)
                    Text(Formatters.formatRupee(payable), fontWeight = FontWeight.Bold, color = DebitRed)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(s.notes) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSupplier?.let { onSave(it, invNo, items, paid, notes) } },
                enabled = selectedSupplier != null && items.isNotEmpty()
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )

    if (showAddItem) {
        AddLineItemDialog(
            state = state,
            canEditRate = true,
            usePurchasePrice = true,
            onDismiss = { showAddItem = false },
            onAdd = { item -> items = items + item; showAddItem = false }
        )
    }
}
