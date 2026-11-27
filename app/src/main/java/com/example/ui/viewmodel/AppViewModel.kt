package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.prefs.AppPreferences
import com.example.data.prefs.UserPreferences
import com.example.data.repository.BackupManager
import com.example.data.repository.DraftLineItem
import com.example.data.repository.DraftReturnItem
import com.example.data.repository.WholesaleRepository
import com.example.printing.PdfPrintEngine
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OrderConversionRequest(
    val order: OrderEntity,
    val items: List<OrderItemEntity>
)

data class AppUiState(
    val isReady: Boolean = false,
    val business: BusinessEntity = BusinessEntity(),
    val prefs: UserPreferences = UserPreferences(),
    val employees: List<EmployeeEntity> = emptyList(),
    val activeEmployee: EmployeeEntity = EmployeeEntity(
        id = 1L, name = "Owner", role = EmployeeRole.OWNER.name,
        canAddCustomer = true, canEditPrices = true, canCreateBill = true,
        canCreateOrder = true, canRecordPayment = true, canManageStock = true,
        canEditInvoiceDate = true, canCancelInvoice = true, canViewReports = true
    ),
    val customers: List<CustomerEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val sellingOptions: List<SellingOptionEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val invoiceItems: List<InvoiceItemEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val orderItems: List<OrderItemEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val allocations: List<PaymentAllocationEntity> = emptyList(),
    val ledger: List<LedgerEntryEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val purchaseItems: List<PurchaseItemEntity> = emptyList(),
    val returns: List<ReturnEntity> = emptyList(),
    val returnItems: List<ReturnItemEntity> = emptyList(),
    val printTemplates: List<PrintTemplateEntity> = emptyList(),
    val activityLogs: List<ActivityLogEntity> = emptyList(),
    val syncQueue: List<SyncQueueEntity> = emptyList(),
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val pendingOrderConversion: OrderConversionRequest? = null,
    val snackbarMessage: String? = null
) {
    val defaultTemplate: PrintTemplateEntity
        get() = printTemplates.firstOrNull { it.isDefault } ?: printTemplates.firstOrNull() ?: PrintTemplateEntity()

    fun customerBalance(customerId: Long): Double {
        return ledger.lastOrNull { it.customerId == customerId }?.runningBalance ?: 0.0
    }

    fun optionsForProduct(productId: Long): List<SellingOptionEntity> =
        sellingOptions.filter { it.productId == productId }

    fun activeOptionsForProduct(productId: Long): List<SellingOptionEntity> =
        sellingOptions.filter { it.productId == productId && it.isActive }

    fun isLowStock(product: ProductEntity, option: SellingOptionEntity): Boolean {
        return if (product.trackByBaseUnit) {
            val availableBase = product.basePhysicalStock - product.baseReservedStock
            val minBase = option.minStock * option.conversionFactorToBase
            availableBase <= minBase
        } else {
            (option.physicalStock - option.reservedStock) <= option.minStock
        }
    }

    fun availableStockForOption(product: ProductEntity, option: SellingOptionEntity): Double {
        return if (product.trackByBaseUnit) {
            val availBase = (product.basePhysicalStock - product.baseReservedStock).coerceAtLeast(0.0)
            if (option.conversionFactorToBase > 0) Formatters.roundMoney(availBase / option.conversionFactorToBase) else 0.0
        } else {
            Formatters.roundMoney(option.physicalStock - option.reservedStock)
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    val repo = WholesaleRepository(db)
    val prefs = AppPreferences(application)
    val backupManager = BackupManager(application, db)
    val printEngine = PdfPrintEngine(application)

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    private val _screenHistory = mutableListOf<AppScreen>()
    private val _pendingOrderConversion = MutableStateFlow<OrderConversionRequest?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _isReady = MutableStateFlow(false)

    // Combine core flows cleanly in grouped chunks
    private val coreFlow = combine(
        repo.businessFlow,
        prefs.preferencesFlow,
        repo.employeesFlow,
        repo.customersFlow,
        repo.categoriesFlow
    ) { biz, userPrefs, emps, custs, cats ->
        CoreBundle(biz ?: BusinessEntity(), userPrefs, emps, custs, cats)
    }

    private val catalogFlow = combine(
        repo.productsFlow,
        repo.sellingOptionsFlow,
        repo.stockMovementsFlow,
        repo.suppliersFlow,
        repo.purchasesFlow
    ) { prods, opts, moves, sups, purs ->
        CatalogBundle(prods, opts, moves, sups, purs)
    }

    private val transactionFlow = combine(
        repo.invoicesFlow,
        repo.invoiceItemsFlow,
        repo.ordersFlow,
        repo.orderItemsFlow,
        repo.paymentsFlow
    ) { invs, invItems, ords, ordItems, pays ->
        TransactionBundle(invs, invItems, ords, ordItems, pays)
    }

    private val ledgerMetaFlow = combine(
        repo.allocationsFlow,
        repo.ledgerFlow,
        repo.purchaseItemsFlow,
        repo.returnsFlow,
        repo.returnItemsFlow
    ) { allocs, ledg, purItems, rets, retItems ->
        LedgerMetaBundle(allocs, ledg, purItems, rets, retItems)
    }

    private val settingsUiFlow = combine(
        repo.printTemplatesFlow,
        repo.activityLogsFlow,
        repo.syncQueueFlow,
        _currentScreen,
        combine(_pendingOrderConversion, _snackbar, _isReady) { conv, msg, ready -> Triple(conv, msg, ready) }
    ) { templates, logs, sync, screen, uiMeta ->
        SettingsUiBundle(templates, logs, sync, screen, uiMeta.first, uiMeta.second, uiMeta.third)
    }

    val uiState: StateFlow<AppUiState> = combine(
        coreFlow,
        catalogFlow,
        transactionFlow,
        ledgerMetaFlow,
        settingsUiFlow
    ) { core, cat, tx, ledg, meta ->
        val activeEmp = core.employees.firstOrNull { it.id == core.prefs.activeEmployeeId && it.isActive }
            ?: core.employees.firstOrNull { it.isActive }
            ?: AppUiState().activeEmployee
        AppUiState(
            isReady = meta.isReady,
            business = core.business,
            prefs = core.prefs,
            employees = core.employees,
            activeEmployee = activeEmp,
            customers = core.customers,
            categories = core.categories,
            products = cat.products,
            sellingOptions = cat.options,
            stockMovements = cat.movements,
            invoices = tx.invoices,
            invoiceItems = tx.invoiceItems,
            orders = tx.orders,
            orderItems = tx.orderItems,
            payments = tx.payments,
            allocations = ledg.allocations,
            ledger = ledg.ledger,
            suppliers = cat.suppliers,
            purchases = cat.purchases,
            purchaseItems = ledg.purchaseItems,
            returns = ledg.returns,
            returnItems = ledg.returnItems,
            printTemplates = meta.templates,
            activityLogs = meta.logs,
            syncQueue = meta.sync,
            currentScreen = meta.screen,
            pendingOrderConversion = meta.conversion,
            snackbarMessage = meta.snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    init {
        viewModelScope.launch {
            repo.ensureInitialized()
            _isReady.value = true
        }
    }

    fun showMessage(msg: String) {
        _snackbar.value = msg
    }

    fun clearMessage() {
        _snackbar.value = null
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            _screenHistory.add(_currentScreen.value)
            if (_screenHistory.size > 20) _screenHistory.removeAt(0)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_screenHistory.isNotEmpty()) {
            _currentScreen.value = _screenHistory.removeAt(_screenHistory.lastIndex)
            return true
        }
        if (_currentScreen.value != AppScreen.DASHBOARD) {
            _currentScreen.value = AppScreen.DASHBOARD
            return true
        }
        return false
    }

    fun startOrderConversion(order: OrderEntity, items: List<OrderItemEntity>) {
        _pendingOrderConversion.value = OrderConversionRequest(order, items)
        navigateTo(AppScreen.NEW_BILL)
    }

    fun clearOrderConversion() {
        _pendingOrderConversion.value = null
    }

    fun completeOnboarding(businessName: String, mobile: String, address: String, language: AppLanguage, loadSample: Boolean) {
        viewModelScope.launch {
            prefs.setLanguage(language)
            repo.completeOnboarding(businessName, mobile, address, language, loadSample)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefs.setLanguage(language)
            val emp = uiState.value.activeEmployee
            if (emp.id > 0) {
                repo.saveEmployee(emp.copy(preferredLanguage = language.code), emp)
            }
        }
    }

    fun setFontSize(option: FontSizeOption) {
        viewModelScope.launch { prefs.setFontSize(option) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }

    fun switchEmployee(employee: EmployeeEntity, enteredPin: String): Boolean {
        if (employee.pinHash.isNotBlank() && Formatters.hashPin(enteredPin) != employee.pinHash) {
            showMessage("Incorrect PIN for ${employee.name}")
            return false
        }
        viewModelScope.launch {
            prefs.setActiveEmployeeId(employee.id)
            prefs.setLanguage(AppLanguage.fromCode(employee.preferredLanguage))
            showMessage("Switched to ${employee.name}")
        }
        return true
    }

    fun saveBusiness(business: BusinessEntity) {
        viewModelScope.launch {
            repo.updateBusiness(business)
            showMessage("Business settings saved")
        }
    }

    fun saveCustomer(name: String, shopName: String, mobile: String, address: String, existingId: Long = 0L, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val actor = uiState.value.activeEmployee
            val existing = if (existingId > 0L) uiState.value.customers.firstOrNull { it.id == existingId } else null
            val id = repo.saveCustomer(
                CustomerEntity(
                    id = existingId,
                    name = name.trim(),
                    shopName = shopName.trim(),
                    mobile = mobile.trim(),
                    address = address.trim(),
                    assignedEmployeeId = existing?.assignedEmployeeId ?: if (actor.role == EmployeeRole.SALES_EXECUTIVE.name) actor.id else null,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                ),
                actor
            )
            autoBackupIfEnabled()
            showMessage("Customer saved: $shopName")
            onSaved(id)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch { repo.addCategory(name) }
    }

    fun saveProduct(product: ProductEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveProduct(product, uiState.value.activeEmployee)
            showMessage("Product saved: ${product.name}")
            onSaved(id)
        }
    }

    fun saveSellingOption(option: SellingOptionEntity, productName: String) {
        viewModelScope.launch {
            repo.saveSellingOption(option, productName, uiState.value.activeEmployee)
            showMessage("Selling option saved: ${option.varietyName}")
        }
    }

    fun createInvoice(
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
    ) {
        viewModelScope.launch {
            val res = repo.createInvoice(
                customer = customer, items = items, invoiceDate = invoiceDate,
                discountAmount = discount, additionalCharges = additionalCharges,
                gstEnabled = gstEnabled, gstPercent = gstPercent,
                paymentStatusChoice = paymentStatus, amountPaidInput = amountPaid,
                notes = notes, sourceOrderId = sourceOrderId, actor = uiState.value.activeEmployee
            )
            res.onSuccess { inv ->
                clearOrderConversion()
                autoBackupIfEnabled()
                onSuccess(inv)
            }.onFailure { err ->
                showMessage(err.message ?: "Failed to save bill")
            }
        }
    }

    fun cancelInvoice(invoiceId: Long, reason: String) {
        viewModelScope.launch {
            repo.cancelInvoice(invoiceId, reason, uiState.value.activeEmployee)
                .onSuccess { showMessage("Bill cancelled & stock restored") }
                .onFailure { showMessage(it.message ?: "Could not cancel bill") }
        }
    }

    fun saveOrder(
        existingOrderId: Long?,
        customer: CustomerEntity,
        items: List<DraftLineItem>,
        status: OrderStatus,
        notes: String,
        onSuccess: (OrderEntity) -> Unit
    ) {
        viewModelScope.launch {
            repo.saveOrder(existingOrderId, customer, items, status, notes, uiState.value.activeEmployee)
                .onSuccess {
                    autoBackupIfEnabled()
                    showMessage("Order saved: ${it.orderNumber}")
                    onSuccess(it)
                }
                .onFailure { showMessage(it.message ?: "Failed to save order") }
        }
    }

    fun updateOrderStatus(orderId: Long, status: OrderStatus) {
        viewModelScope.launch {
            repo.updateOrderStatus(orderId, status, uiState.value.activeEmployee)
            showMessage("Order status updated")
        }
    }

    fun duplicateOrder(orderId: Long) {
        viewModelScope.launch {
            repo.duplicateOrder(orderId, uiState.value.activeEmployee)
                .onSuccess { showMessage("Duplicated as ${it.orderNumber}") }
                .onFailure { showMessage(it.message ?: "Could not duplicate order") }
        }
    }

    fun recordPayment(
        customer: CustomerEntity,
        paymentDate: Long,
        amountReceived: Double,
        method: PaymentMethod,
        referenceNumber: String,
        selectedInvoiceIds: List<Long>,
        notes: String,
        onSuccess: (PaymentEntity) -> Unit
    ) {
        viewModelScope.launch {
            repo.recordPayment(customer, paymentDate, amountReceived, method, referenceNumber, selectedInvoiceIds, notes, uiState.value.activeEmployee)
                .onSuccess {
                    autoBackupIfEnabled()
                    showMessage("Payment recorded: ${it.receiptNumber}")
                    onSuccess(it)
                }
                .onFailure { showMessage(it.message ?: "Failed to record payment") }
        }
    }

    fun adjustStock(
        productId: Long,
        optionId: Long?,
        delta: Double,
        type: StockMovementType,
        reason: String,
        batch: String,
        expiry: String
    ) {
        viewModelScope.launch {
            repo.adjustStock(productId, optionId, delta, type, reason, batch, expiry, uiState.value.activeEmployee)
                .onSuccess { showMessage("Stock updated") }
                .onFailure { showMessage(it.message ?: "Stock update failed") }
        }
    }

    fun saveSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            repo.saveSupplier(supplier, uiState.value.activeEmployee)
            showMessage("Supplier saved: ${supplier.businessName}")
        }
    }

    fun recordPurchase(
        supplier: SupplierEntity,
        invoiceNo: String,
        date: Long,
        items: List<DraftLineItem>,
        amountPaid: Double,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repo.recordPurchase(supplier, invoiceNo, date, items, amountPaid, notes, uiState.value.activeEmployee)
                .onSuccess {
                    autoBackupIfEnabled()
                    showMessage("Purchase saved & stock updated")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Failed to record purchase") }
        }
    }

    fun recordReturn(
        invoice: InvoiceEntity,
        items: List<DraftReturnItem>,
        settlement: ReturnSettlementType,
        reason: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repo.recordReturn(invoice, items, settlement, reason, uiState.value.activeEmployee)
                .onSuccess {
                    showMessage("Return recorded: ${it.creditNoteNumber}")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Failed to record return") }
        }
    }

    fun saveEmployee(employee: EmployeeEntity) {
        viewModelScope.launch {
            repo.saveEmployee(employee, uiState.value.activeEmployee)
            showMessage("Employee saved: ${employee.name}")
        }
    }

    fun assignCustomerToEmployee(customerId: Long, employeeId: Long?) {
        viewModelScope.launch {
            repo.assignCustomerToEmployee(customerId, employeeId, uiState.value.activeEmployee)
            showMessage("Customer assignment updated")
        }
    }

    fun savePrintTemplate(template: PrintTemplateEntity, setAsDefault: Boolean) {
        viewModelScope.launch {
            repo.savePrintTemplate(template, setAsDefault)
            showMessage("Print template saved")
        }
    }

    fun deletePrintTemplate(id: Long) {
        viewModelScope.launch {
            repo.deletePrintTemplate(id)
            showMessage("Print template removed")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            repo.seedSampleWholesaleData()
            showMessage("Sample wholesale data added")
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            repo.simulateSyncNow()
            showMessage("All pending local records marked synced")
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val json = backupManager.exportToJson()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
            }
            prefs.setLastBackupTimestamp(System.currentTimeMillis())
            showMessage("Database backup exported successfully")
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (json.isNullOrBlank()) {
                showMessage("Backup file is empty")
                return@launch
            }
            backupManager.restoreFromJson(json)
                .onSuccess { showMessage("Database restored successfully!") }
                .onFailure { showMessage("Restore failed: ${it.message}") }
        }
    }

    fun printInvoice(invoice: InvoiceEntity, copiesPerPage: Int, shareOnly: Boolean = false) {
        viewModelScope.launch {
            val state = uiState.value
            val items = state.invoiceItems.filter { it.invoiceId == invoice.id }
            val pdf = withContext(Dispatchers.IO) {
                printEngine.generateInvoicePdf(state.business, invoice, items, state.defaultTemplate, copiesPerPage)
            }
            if (shareOnly) {
                printEngine.shareFile(pdf, "application/pdf")
            } else {
                printEngine.printPdfDirect(pdf, "Bill_${invoice.invoiceNumber}")
            }
        }
    }

    fun printCustomerHistory(customer: CustomerEntity, filter: DateFilter, shareOnly: Boolean = false) {
        viewModelScope.launch {
            val state = uiState.value
            val (start, end) = Formatters.dateRangeFor(filter)
            val invs = state.invoices.filter { it.customerId == customer.id && it.invoiceDate in start..end }
            val ords = state.orders.filter { it.customerId == customer.id && it.orderDate in start..end }
            val pays = state.payments.filter { it.customerId == customer.id && it.paymentDate in start..end }
            val ledg = state.ledger.filter { it.customerId == customer.id && it.entryDate in start..end }
            val pdf = withContext(Dispatchers.IO) {
                printEngine.generateCustomerHistoryPdf(state.business, customer, invs, ords, pays, ledg, filter.name)
            }
            if (shareOnly) {
                printEngine.shareFile(pdf, "application/pdf")
            } else {
                printEngine.printPdfDirect(pdf, "History_${customer.shopName}")
            }
        }
    }

    fun printPaymentReceipt(payment: PaymentEntity, shareOnly: Boolean = false) {
        viewModelScope.launch {
            val state = uiState.value
            val bal = state.customerBalance(payment.customerId)
            val pdf = withContext(Dispatchers.IO) {
                printEngine.generatePaymentReceiptPdf(state.business, payment, bal)
            }
            if (shareOnly) {
                printEngine.shareFile(pdf, "application/pdf")
            } else {
                printEngine.printPdfDirect(pdf, "Receipt_${payment.receiptNumber}")
            }
        }
    }

    fun printOrderSlip(order: OrderEntity, shareOnly: Boolean = false) {
        viewModelScope.launch {
            val state = uiState.value
            val items = state.orderItems.filter { it.orderId == order.id }
            val pdf = withContext(Dispatchers.IO) {
                printEngine.generateOrderSlipPdf(state.business, order, items)
            }
            if (shareOnly) {
                printEngine.shareFile(pdf, "application/pdf")
            } else {
                printEngine.printPdfDirect(pdf, "Order_${order.orderNumber}")
            }
        }
    }

    fun printOrShareReport(title: String, lines: List<Pair<String, String>>, shareOnly: Boolean) {
        viewModelScope.launch {
            val pdf = withContext(Dispatchers.IO) {
                printEngine.generateReportPdf(uiState.value.business, title, lines)
            }
            if (shareOnly) printEngine.shareFile(pdf, "application/pdf") else printEngine.printPdfDirect(pdf, title)
        }
    }

    fun exportReportCsv(fileName: String, headers: List<String>, rows: List<List<String>>) {
        viewModelScope.launch {
            val csv = withContext(Dispatchers.IO) {
                printEngine.exportCsvFile(fileName, headers, rows)
            }
            printEngine.shareFile(csv, "text/csv")
        }
    }

    private fun autoBackupIfEnabled() {
        if (uiState.value.prefs.autoBackupEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    backupManager.saveAutoLocalBackup()
                    prefs.setLastBackupTimestamp(System.currentTimeMillis())
                }
            }
        }
    }

    private data class CoreBundle(
        val business: BusinessEntity,
        val prefs: UserPreferences,
        val employees: List<EmployeeEntity>,
        val customers: List<CustomerEntity>,
        val categories: List<CategoryEntity>
    )

    private data class CatalogBundle(
        val products: List<ProductEntity>,
        val options: List<SellingOptionEntity>,
        val movements: List<StockMovementEntity>,
        val suppliers: List<SupplierEntity>,
        val purchases: List<PurchaseEntity>
    )

    private data class TransactionBundle(
        val invoices: List<InvoiceEntity>,
        val invoiceItems: List<InvoiceItemEntity>,
        val orders: List<OrderEntity>,
        val orderItems: List<OrderItemEntity>,
        val payments: List<PaymentEntity>
    )

    private data class LedgerMetaBundle(
        val allocations: List<PaymentAllocationEntity>,
        val ledger: List<LedgerEntryEntity>,
        val purchaseItems: List<PurchaseItemEntity>,
        val returns: List<ReturnEntity>,
        val returnItems: List<ReturnItemEntity>
    )

    private data class SettingsUiBundle(
        val templates: List<PrintTemplateEntity>,
        val logs: List<ActivityLogEntity>,
        val sync: List<SyncQueueEntity>,
        val screen: AppScreen,
        val conversion: OrderConversionRequest?,
        val snackbar: String?,
        val isReady: Boolean
    )
}
