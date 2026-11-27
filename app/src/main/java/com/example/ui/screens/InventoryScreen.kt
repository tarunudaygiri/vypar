package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.ProductEntity
import com.example.data.db.SellingOptionEntity
import com.example.data.model.StockMovementType
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun InventoryScreen(
    state: AppUiState,
    onAdjustStock: (Long, Long?, Double, StockMovementType, String, String, String) -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Current Stock, 1 = Movement History
    var search by remember { mutableStateOf("") }
    var onlyLowStock by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<Pair<ProductEntity, SellingOptionEntity?>?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(s.navInventory, fontWeight = FontWeight.Bold) })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(s.stockMovements, fontWeight = FontWeight.Bold) })
        }

        if (activeTab == 0) {
            val stockRows = remember(state.products, state.sellingOptions, search, onlyLowStock) {
                state.products.flatMap { prod ->
                    val opts = state.activeOptionsForProduct(prod.id)
                    if (prod.trackByBaseUnit) {
                        val anyLow = opts.any { state.isLowStock(prod, it) }
                        if ((!onlyLowStock || anyLow) && Formatters.matchesSearch(search, prod.name, prod.categoryName)) {
                            listOf(prod to null)
                        } else emptyList()
                    } else {
                        opts.filter { opt ->
                            (!onlyLowStock || state.isLowStock(prod, opt)) &&
                                Formatters.matchesSearch(search, prod.name, prod.categoryName, opt.varietyName, opt.sellingUnit)
                        }.map { prod to it }
                    }
                }
            }

            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(search, { search = it }, s.searchProductHint)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlyLowStock, onClick = { onlyLowStock = false }, label = { Text(s.viewAll) })
                    FilterChip(selected = onlyLowStock, onClick = { onlyLowStock = true }, label = { Text(s.lowStockAlerts) })
                }

                if (stockRows.isEmpty()) {
                    EmptyStateCard(Icons.Default.Warehouse, s.noRecordsYet)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(stockRows) { (prod, opt) ->
                            val isBase = opt == null
                            val physical = if (isBase) prod.basePhysicalStock else opt!!.physicalStock
                            val reserved = if (isBase) prod.baseReservedStock else opt!!.reservedStock
                            val available = Formatters.roundMoney(physical - reserved)
                            val unit = if (isBase) prod.baseUnit else opt!!.sellingUnit
                            val low = if (isBase) state.activeOptionsForProduct(prod.id).any { state.isLowStock(prod, it) } else state.isLowStock(prod, opt!!)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, if (low) DebitRed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(prod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                if (isBase) "Shared Base Stock (${prod.baseUnit})" else "${opt!!.varietyName} (${opt.sellingUnit})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (low) StatusPill(s.lowStockTag, DebitRed.copy(alpha = 0.14f), DebitRed)
                                    }

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StockMetric(s.physicalStock, "${Formatters.formatQty(physical)} $unit")
                                        StockMetric(s.reservedStock, "${Formatters.formatQty(reserved)} $unit")
                                        StockMetric(s.availableStock, "${Formatters.formatQty(available)} $unit", if (low) DebitRed else CreditGreen)
                                    }

                                    if (!isBase && (opt!!.batchNumber.isNotBlank() || opt.expiryDate.isNotBlank())) {
                                        Text(
                                            "Batch: ${opt.batchNumber.ifBlank { "-" }} • Expiry: ${opt.expiryDate.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (state.activeEmployee.canManageStock) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            FilledTonalButton(
                                                onClick = { adjustTarget = prod to opt },
                                                modifier = Modifier.testTag("adjust_stock_${prod.id}_${opt?.id ?: 0}")
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = s.adjustStock, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(s.adjustStock)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.stockMovements.isEmpty()) {
                    item { EmptyStateCard(Icons.Default.History, s.noRecordsYet) }
                } else {
                    items(state.stockMovements, key = { it.id }) { m ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.productName} (${m.optionName})", fontWeight = FontWeight.Bold)
                                    Text("${m.movementType} • ${m.reason}", style = MaterialTheme.typography.bodySmall)
                                    Text("${m.performedBy} • ${Formatters.formatDateTime(m.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val sign = if (m.quantityDelta > 0) "+" else ""
                                Text(
                                    "$sign${Formatters.formatQty(m.quantityDelta)} ${m.unit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (m.quantityDelta >= 0) CreditGreen else DebitRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    adjustTarget?.let { (prod, opt) ->
        var isAddition by remember { mutableStateOf(true) }
        var isDamaged by remember { mutableStateOf(false) }
        var qtyText by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var batch by remember { mutableStateOf(opt?.batchNumber ?: "") }
        var expiry by remember { mutableStateOf(opt?.expiryDate ?: "") }

        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("${s.adjustStock}: ${prod.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isAddition && !isDamaged, onClick = { isAddition = true; isDamaged = false }, label = { Text("+ Add Stock") })
                        FilterChip(selected = !isAddition && !isDamaged, onClick = { isAddition = false; isDamaged = false }, label = { Text("- Deduct") })
                        FilterChip(selected = isDamaged, onClick = { isAddition = false; isDamaged = true }, label = { Text(s.damagedStock) })
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("${s.qty} (${opt?.sellingUnit ?: prod.baseUnit}) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("${s.adjustmentReason} *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (opt != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text(s.batchNumber) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text(s.expiryDate) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                val q = qtyText.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        val delta = if (isAddition) q else -q
                        val type = if (isDamaged) StockMovementType.RETURN_DAMAGED else StockMovementType.ADJUSTMENT
                        onAdjustStock(prod.id, opt?.id, delta, type, reason.ifBlank { "Manual adjustment" }, batch, expiry)
                        adjustTarget = null
                    },
                    enabled = q > 0.0 && reason.isNotBlank()
                ) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun StockMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}
