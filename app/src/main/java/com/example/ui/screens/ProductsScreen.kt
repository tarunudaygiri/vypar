package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import com.example.data.db.ProductEntity
import com.example.data.db.SellingOptionEntity
import com.example.data.model.SellingUnit
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchField
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun ProductsScreen(
    state: AppUiState,
    onSaveProduct: (ProductEntity, onSaved: (Long) -> Unit) -> Unit,
    onSaveSellingOption: (SellingOptionEntity, String) -> Unit,
    onAddCategory: (String) -> Unit
) {
    val s = LocalStrings.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var optionDialogTarget by remember { mutableStateOf<Pair<ProductEntity, SellingOptionEntity?>?>(null) }

    val selectedProduct = remember(selectedProductId, state.products) {
        state.products.firstOrNull { it.id == selectedProductId }
    }

    if (selectedProduct != null) {
        BackHandler { selectedProductId = null }
        ProductDetailView(
            product = selectedProduct,
            state = state,
            onBack = { selectedProductId = null },
            onEditProduct = { editingProduct = selectedProduct },
            onAddOption = { optionDialogTarget = selectedProduct to null },
            onEditOption = { opt -> optionDialogTarget = selectedProduct to opt },
            onToggleOptionActive = { opt ->
                onSaveSellingOption(opt.copy(isActive = !opt.isActive), selectedProduct.name)
            }
        )
    } else {
        val filteredProducts = remember(state.products, search, selectedCategory) {
            state.products.filter { prod ->
                (selectedCategory == null || prod.categoryName.equals(selectedCategory, true)) &&
                    Formatters.matchesSearch(search, prod.name, prod.categoryName)
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    icon = { Icon(Icons.Default.AddBox, contentDescription = s.addProduct) },
                    text = { Text(s.addProduct) },
                    modifier = Modifier.testTag("add_product_fab")
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = s.searchProductHint,
                    tag = "product_search_input"
                )

                // Category Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(s.viewAll) }
                    )
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = if (selectedCategory == cat.name) null else cat.name },
                            label = { Text(cat.name) }
                        )
                    }
                }

                if (filteredProducts.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.Inventory2,
                        message = s.noRecordsYet,
                        actionLabel = s.addProduct,
                        onAction = { showAddProductDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { prod ->
                            val opts = state.activeOptionsForProduct(prod.id)
                            val anyLow = opts.any { state.isLowStock(prod, it) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductId = prod.id }
                                    .testTag("product_card_${prod.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(prod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                if (prod.trackByBaseUnit) "${prod.categoryName} • ${s.baseUnitStock}: ${Formatters.formatQty(prod.basePhysicalStock)} ${prod.baseUnit}"
                                                else prod.categoryName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (anyLow) {
                                            StatusPill(s.lowStockTag, DebitRed.copy(alpha = 0.14f), DebitRed)
                                        }
                                    }

                                    // Flexible Selling Options Preview Strip
                                    if (opts.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            opts.forEach { opt ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        "${opt.varietyName}: ${Formatters.formatRupee(opt.sellingPrice)}/${opt.sellingUnit}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        TextButton(onClick = { optionDialogTarget = prod to null }) {
                                            Text(s.addSellingOption, fontWeight = FontWeight.Bold)
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

    if (showAddProductDialog || editingProduct != null) {
        AddEditProductDialog(
            existing = editingProduct,
            categories = state.categories.map { it.name },
            onDismiss = { showAddProductDialog = false; editingProduct = null },
            onAddCategory = onAddCategory,
            onSave = { prod ->
                val isNew = prod.id == 0L
                onSaveProduct(prod) { newId ->
                    if (isNew) {
                        selectedProductId = newId
                        optionDialogTarget = prod.copy(id = newId) to null
                    }
                }
                showAddProductDialog = false
                editingProduct = null
            }
        )
    }

    optionDialogTarget?.let { (prod, existingOpt) ->
        AddEditSellingOptionDialog(
            product = prod,
            existing = existingOpt,
            onDismiss = { optionDialogTarget = null },
            onSave = { opt ->
                onSaveSellingOption(opt, prod.name)
                optionDialogTarget = null
            }
        )
    }
}

@Composable
private fun ProductDetailView(
    product: ProductEntity,
    state: AppUiState,
    onBack: () -> Unit,
    onEditProduct: () -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (SellingOptionEntity) -> Unit,
    onToggleOptionActive: (SellingOptionEntity) -> Unit
) {
    val s = LocalStrings.current
    val options = remember(state.sellingOptions, product.id) { state.optionsForProduct(product.id) }
    val movements = remember(state.stockMovements, product.id) { state.stockMovements.filter { it.productId == product.id }.take(15) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddOption,
                icon = { Icon(Icons.Default.Add, contentDescription = s.addSellingOption) },
                text = { Text(s.addSellingOption) },
                modifier = Modifier.testTag("add_selling_option_fab")
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (product.trackByBaseUnit) "${product.categoryName} • ${s.baseUnitStock}: ${Formatters.formatQty(product.basePhysicalStock)} ${product.baseUnit}"
                            else product.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditProduct) {
                        Icon(Icons.Default.Edit, contentDescription = s.edit)
                    }
                }
            }

            item {
                Text("${s.sellingOptions} (${options.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (options.isEmpty()) {
                item { EmptyStateCard(Icons.Default.Style, s.noRecordsYet, s.addSellingOption, onAddOption) }
            } else {
                items(options, key = { it.id }) { opt ->
                    val avail = state.availableStockForOption(product, opt)
                    val low = opt.isActive && state.isLowStock(product, opt)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (opt.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, if (low) DebitRed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(opt.varietyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val contentsDesc = if (opt.packageContentsUnit.isNotBlank()) {
                                        "${opt.sellingUnit} • ${Formatters.formatQty(opt.packageContentsQty)} ${opt.packageContentsUnit}"
                                    } else opt.sellingUnit
                                    Text(contentsDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${Formatters.formatRupee(opt.sellingPrice)} / ${opt.sellingUnit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${s.availableStock}: ${Formatters.formatQty(avail)} ${opt.sellingUnit} (Min: ${Formatters.formatQty(opt.minStock)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (low) DebitRed else CreditGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onEditOption(opt) }) { Text(s.edit) }
                                    TextButton(onClick = { onToggleOptionActive(opt) }) {
                                        Text(if (opt.isActive) s.deactivateOption else s.activateOption)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (movements.isNotEmpty()) {
                item {
                    Text(s.stockMovements, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(movements, key = { it.id }) { m ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.optionName} • ${m.movementType}", fontWeight = FontWeight.SemiBold)
                                Text("${m.reason} • ${Formatters.formatShortDate(m.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val sign = if (m.quantityDelta > 0) "+" else ""
                            Text(
                                "$sign${Formatters.formatQty(m.quantityDelta)} ${m.unit}",
                                fontWeight = FontWeight.Bold,
                                color = if (m.quantityDelta >= 0) CreditGreen else DebitRed
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

/**
 * Step 1: Simple Product Creation — asks ONLY Product Name & Category (plus optional base-unit stock tracking toggle, NO HSN!)
 */
@Composable
private fun AddEditProductDialog(
    existing: ProductEntity?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.categoryName ?: categories.firstOrNull() ?: "Flour") }
    var customCategory by remember { mutableStateOf("") }
    var trackByBase by remember { mutableStateOf(existing?.trackByBaseUnit ?: false) }
    var baseUnit by remember { mutableStateOf(existing?.baseUnit ?: "KG") }
    var baseStock by remember { mutableStateOf(if ((existing?.basePhysicalStock ?: 0.0) > 0) Formatters.formatQty(existing!!.basePhysicalStock) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.addProduct else "${s.edit} ${s.navProducts}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("${s.productName} *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                Text(s.category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                OutlinedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = { Text("Or new category (${s.optional})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Switch(checked = trackByBase, onCheckedChange = { trackByBase = it })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.trackByBaseUnit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(s.trackByBaseUnitDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (trackByBase) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = baseUnit,
                            onValueChange = { baseUnit = it },
                            label = { Text("Base Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = baseStock,
                            onValueChange = { baseStock = it },
                            label = { Text(s.openingStock) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCat = customCategory.trim().ifBlank { category }
                    if (customCategory.isNotBlank()) onAddCategory(finalCat)
                    onSave(
                        (existing ?: ProductEntity(name = "", categoryName = finalCat)).copy(
                            name = name.trim(),
                            categoryName = finalCat,
                            trackByBaseUnit = trackByBase,
                            baseUnit = baseUnit.ifBlank { "KG" },
                            basePhysicalStock = baseStock.toDoubleOrNull() ?: existing?.basePhysicalStock ?: 0.0
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_product_button")
            ) {
                Text(s.save)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

/**
 * Step 2: Add / Edit Selling Option (KG, Bag, Packet, Box, Piece, Gram, Litre, ML, Custom + Independent Price + Min Stock + Conversion)
 */
@Composable
private fun AddEditSellingOptionDialog(
    product: ProductEntity,
    existing: SellingOptionEntity?,
    onDismiss: () -> Unit,
    onSave: (SellingOptionEntity) -> Unit
) {
    val s = LocalStrings.current
    var varietyName by remember { mutableStateOf(existing?.varietyName ?: "") }
    var selectedUnit by remember { mutableStateOf(SellingUnit.fromString(existing?.sellingUnit ?: "KG")) }
    var customUnitText by remember { mutableStateOf(if (selectedUnit == SellingUnit.CUSTOM) existing?.sellingUnit ?: "" else "") }
    var contentsQty by remember { mutableStateOf(if (existing != null) Formatters.formatQty(existing.packageContentsQty) else "1") }
    var contentsUnit by remember { mutableStateOf(existing?.packageContentsUnit ?: product.baseUnit) }
    var sellingPrice by remember { mutableStateOf(if (existing != null) Formatters.formatQty(existing.sellingPrice) else "") }
    var purchasePrice by remember { mutableStateOf(if ((existing?.purchasePrice ?: 0.0) > 0) Formatters.formatQty(existing!!.purchasePrice) else "") }
    var physicalStock by remember { mutableStateOf(if (existing != null) Formatters.formatQty(existing.physicalStock) else "20") }
    var minStock by remember { mutableStateOf(if (existing != null) Formatters.formatQty(existing.minStock) else "5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.addSellingOption} — ${product.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = varietyName,
                    onValueChange = { varietyName = it },
                    label = { Text("${s.varietyName} * (e.g. 25 KG Bag, Loose KG)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("option_variety_input")
                )
                Text(s.sellingUnit, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SellingUnit.entries.forEach { u ->
                        FilterChip(
                            selected = selectedUnit == u,
                            onClick = {
                                selectedUnit = u
                                if (varietyName.isBlank()) {
                                    varietyName = when (u) {
                                        SellingUnit.KG -> "Loose KG"
                                        SellingUnit.BAG -> "25 KG Bag"
                                        SellingUnit.PACKET -> "100g Packet"
                                        SellingUnit.BOX -> "Master Box"
                                        SellingUnit.PIECE -> "Single Piece"
                                        else -> u.label
                                    }
                                }
                                if (u == SellingUnit.BAG && contentsQty == "1") { contentsQty = "25"; contentsUnit = "KG" }
                                if (u == SellingUnit.PACKET && contentsQty == "1") { contentsQty = "100"; contentsUnit = "g" }
                            },
                            label = { Text(u.label) }
                        )
                    }
                }
                if (selectedUnit == SellingUnit.CUSTOM) {
                    OutlinedTextField(
                        value = customUnitText,
                        onValueChange = { customUnitText = it },
                        label = { Text("Custom Unit Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("${s.sellingPrice} *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("option_price_input")
                    )
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text(s.purchasePrice) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contentsQty,
                        onValueChange = { contentsQty = it },
                        label = { Text("Pack Weight/Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = contentsUnit,
                        onValueChange = { contentsUnit = it },
                        label = { Text("Unit (KG/g/Pc)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!product.trackByBaseUnit) {
                        OutlinedTextField(
                            value = physicalStock,
                            onValueChange = { physicalStock = it },
                            label = { Text(s.openingStock) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text(s.minStock) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val unitLabel = if (selectedUnit == SellingUnit.CUSTOM) customUnitText.trim().ifBlank { "Unit" } else selectedUnit.label
                    val cQty = contentsQty.toDoubleOrNull() ?: 1.0
                    val conv = when {
                        contentsUnit.equals("g", true) && product.baseUnit.equals("KG", true) -> cQty / 1000.0
                        contentsUnit.equals("ml", true) && product.baseUnit.equals("Litre", true) -> cQty / 1000.0
                        else -> cQty
                    }
                    onSave(
                        (existing ?: SellingOptionEntity(productId = product.id, varietyName = "", sellingUnit = unitLabel, sellingPrice = 0.0)).copy(
                            productId = product.id,
                            varietyName = varietyName.trim(),
                            sellingUnit = unitLabel,
                            packageContentsQty = cQty,
                            packageContentsUnit = contentsUnit.trim(),
                            conversionFactorToBase = conv.coerceAtLeast(0.001),
                            sellingPrice = sellingPrice.toDoubleOrNull() ?: 0.0,
                            purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
                            physicalStock = physicalStock.toDoubleOrNull() ?: existing?.physicalStock ?: 0.0,
                            minStock = minStock.toDoubleOrNull() ?: 5.0
                        )
                    )
                },
                enabled = varietyName.isNotBlank() && (sellingPrice.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_option_button")
            ) {
                Text(s.save)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
