package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.*
import com.example.data.model.*
import com.example.util.Formatters
import kotlinx.coroutines.flow.Flow

data class DraftLineItem(
    val productId: Long,
    val sellingOptionId: Long,
    val productName: String,
    val varietyName: String,
    val sellingUnit: String,
    val quantity: Double,
    val rate: Double
) {
    val lineAmount: Double get() = Formatters.multiplyMoney(quantity, rate)
}

data class DraftReturnItem(
    val productId: Long,
    val sellingOptionId: Long,
    val productName: String,
    val varietyName: String,
    val sellingUnit: String,
    val returnedQty: Double,
    val rate: Double,
    val condition: ReturnCondition
) {
    val amount: Double get() = Formatters.multiplyMoney(returnedQty, rate)
}

class WholesaleRepository(private val db: AppDatabase) {
    val businessFlow: Flow<BusinessEntity?> = db.businessDao().observeBusiness()
    val employeesFlow: Flow<List<EmployeeEntity>> = db.employeeDao().observeAll()
    val customersFlow: Flow<List<CustomerEntity>> = db.customerDao().observeActive()
    val categoriesFlow: Flow<List<CategoryEntity>> = db.productDao().observeCategories()
    val productsFlow: Flow<List<ProductEntity>> = db.productDao().observeActiveProducts()
    val sellingOptionsFlow: Flow<List<SellingOptionEntity>> = db.productDao().observeAllSellingOptions()
    val stockMovementsFlow: Flow<List<StockMovementEntity>> = db.productDao().observeStockMovements()
    val invoicesFlow: Flow<List<InvoiceEntity>> = db.invoiceDao().observeAll()
    val invoiceItemsFlow: Flow<List<InvoiceItemEntity>> = db.invoiceDao().observeAllItems()
    val ordersFlow: Flow<List<OrderEntity>> = db.orderDao().observeAll()
    val orderItemsFlow: Flow<List<OrderItemEntity>> = db.orderDao().observeAllItems()
    val paymentsFlow: Flow<List<PaymentEntity>> = db.paymentLedgerDao().observeAllPayments()
    val allocationsFlow: Flow<List<PaymentAllocationEntity>> = db.paymentLedgerDao().observeAllAllocations()
    val ledgerFlow: Flow<List<LedgerEntryEntity>> = db.paymentLedgerDao().observeAllLedger()
    val suppliersFlow: Flow<List<SupplierEntity>> = db.supplierPurchaseDao().observeActiveSuppliers()
    val purchasesFlow: Flow<List<PurchaseEntity>> = db.supplierPurchaseDao().observeAllPurchases()
    val purchaseItemsFlow: Flow<List<PurchaseItemEntity>> = db.supplierPurchaseDao().observeAllPurchaseItems()
    val returnsFlow: Flow<List<ReturnEntity>> = db.returnDao().observeAllReturns()
    val returnItemsFlow: Flow<List<ReturnItemEntity>> = db.returnDao().observeAllReturnItems()
    val printTemplatesFlow: Flow<List<PrintTemplateEntity>> = db.settingsMetaDao().observePrintTemplates()
    val activityLogsFlow: Flow<List<ActivityLogEntity>> = db.settingsMetaDao().observeActivityLogs()
    val syncQueueFlow: Flow<List<SyncQueueEntity>> = db.settingsMetaDao().observeSyncQueue()

    suspend fun ensureInitialized() {
        db.withTransaction {
            if (db.businessDao().getBusiness() == null) {
                db.businessDao().upsert(BusinessEntity())
            }
            if (db.employeeDao().getAll().isEmpty()) {
                db.employeeDao().insert(
                    EmployeeEntity(
                        id = 1L,
                        name = "Owner",
                        role = EmployeeRole.OWNER.name,
                        canAddCustomer = true,
                        canEditPrices = true,
                        canCreateBill = true,
                        canCreateOrder = true,
                        canRecordPayment = true,
                        canManageStock = true,
                        canEditInvoiceDate = true,
                        canCancelInvoice = true,
                        canViewReports = true
                    )
                )
            }
            val defaults = listOf("Flour", "Spices", "Rice", "Pulses", "Grains", "Oil & Ghee", "Other")
            if (db.productDao().getAllCategories().isEmpty()) {
                defaults.forEach { db.productDao().insertCategory(CategoryEntity(name = it, isDefault = true)) }
            }
            if (db.settingsMetaDao().getAllPrintTemplates().isEmpty()) {
                db.settingsMetaDao().insertPrintTemplate(PrintTemplateEntity(name = "Standard Wholesale A4", isDefault = true, billsPerPage = 1))
                db.settingsMetaDao().insertPrintTemplate(PrintTemplateEntity(name = "4-in-1 A4 Quad Bill", isDefault = false, billsPerPage = 4, fontSizeSp = 8.5f, marginMm = 6))
                db.settingsMetaDao().insertPrintTemplate(PrintTemplateEntity(name = "2-in-1 A4 Half Bill", isDefault = false, billsPerPage = 2, fontSizeSp = 9.5f, marginMm = 8))
            }
        }
    }

    suspend fun completeOnboarding(
        businessName: String,
        mobile: String,
        address: String,
        language: AppLanguage,
        loadSampleData: Boolean
    ) {
        ensureInitialized()
        val current = db.businessDao().getBusiness() ?: BusinessEntity()
        db.businessDao().upsert(
            current.copy(
                name = businessName.ifBlank { current.name },
                mobile = mobile.ifBlank { current.mobile },
                address = address.ifBlank { current.address },
                defaultLanguage = language.code,
                defaultInvoiceLanguage = language.code,
                onboardingCompleted = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (loadSampleData && db.productDao().getAllProducts().isEmpty()) {
            seedSampleWholesaleData()
        }
    }

    suspend fun seedSampleWholesaleData() {
        ensureInitialized()
        db.withTransaction {
            val now = System.currentTimeMillis()
            val day = 86_400_000L

            // Add realistic employees if only Owner exists
            val existingEmps = db.employeeDao().getAll()
            val salesExecId = if (existingEmps.size < 2) {
                val execId = db.employeeDao().insert(
                    EmployeeEntity(
                        name = "Suresh Reddy",
                        mobile = "9848055112",
                        role = EmployeeRole.SALES_EXECUTIVE.name,
                        canAddCustomer = true,
                        canEditPrices = false,
                        canCreateBill = false,
                        canCreateOrder = true,
                        canRecordPayment = true,
                        canManageStock = false,
                        canEditInvoiceDate = false,
                        canCancelInvoice = false,
                        canViewReports = false,
                        preferredLanguage = "te"
                    )
                )
                db.employeeDao().insert(
                    EmployeeEntity(
                        name = "Anitha Billing",
                        mobile = "9848055223",
                        role = EmployeeRole.BILLING_STAFF.name,
                        canAddCustomer = true,
                        canEditPrices = false,
                        canCreateBill = true,
                        canCreateOrder = true,
                        canRecordPayment = true,
                        canManageStock = false,
                        canEditInvoiceDate = false,
                        canCancelInvoice = false,
                        canViewReports = false,
                        preferredLanguage = "en"
                    )
                )
                execId
            } else existingEmps.last().id

            // Customers (Mixed English and Telugu!)
            val c1 = db.customerDao().insert(
                CustomerEntity(name = "Ravi", shopName = "Ravi Traders", mobile = "9848112233", address = "Subhash Road, Anantapur", assignedEmployeeId = salesExecId)
            )
            val c2 = db.customerDao().insert(
                CustomerEntity(name = "వెంకటేష్", shopName = "శ్రీ లక్ష్మీ కిరాణా & జనరల్ స్టోర్స్", mobile = "9440223344", address = "పాత ఊరు, అనంతపురం", assignedEmployeeId = salesExecId)
            )
            val c3 = db.customerDao().insert(
                CustomerEntity(name = "Mahesh Gupta", shopName = "Balaji Super Mart", mobile = "9989334455", address = "Clock Tower, Kurnool", assignedEmployeeId = salesExecId)
            )
            db.customerDao().insert(
                CustomerEntity(name = "రమేష్ నాయుడు", shopName = "సాయి ట్రేడర్స్", mobile = "9000445566", address = "ధర్మవరం రోడ్, అనంతపురం", assignedEmployeeId = salesExecId)
            )

            // Products with flexible selling options (Ragi Flour uses base KG conversion!)
            val pRagi = db.productDao().insertProduct(
                ProductEntity(
                    name = "Ragi Flour / రాగి పిండి",
                    categoryName = "Flour",
                    baseUnit = "KG",
                    trackByBaseUnit = true,
                    basePhysicalStock = 240.0
                )
            )
            val optRagiKg = db.productDao().insertOption(
                SellingOptionEntity(productId = pRagi, varietyName = "Loose KG", sellingUnit = "KG", packageContentsQty = 1.0, packageContentsUnit = "KG", conversionFactorToBase = 1.0, sellingPrice = 110.0, purchasePrice = 85.0, physicalStock = 240.0, minStock = 20.0)
            )
            val optRagiBag = db.productDao().insertOption(
                SellingOptionEntity(productId = pRagi, varietyName = "25 KG Bag", sellingUnit = "Bag", packageContentsQty = 25.0, packageContentsUnit = "KG", conversionFactorToBase = 25.0, sellingPrice = 2400.0, purchasePrice = 1950.0, physicalStock = 9.0, minStock = 5.0)
            )
            val optRagiPkt = db.productDao().insertOption(
                SellingOptionEntity(productId = pRagi, varietyName = "100g Packet", sellingUnit = "Packet", packageContentsQty = 100.0, packageContentsUnit = "g", conversionFactorToBase = 0.1, sellingPrice = 15.0, purchasePrice = 11.0, physicalStock = 120.0, minStock = 50.0)
            )
            db.productDao().insertOption(
                SellingOptionEntity(productId = pRagi, varietyName = "Single Piece", sellingUnit = "Piece", packageContentsQty = 1.0, packageContentsUnit = "Piece", conversionFactorToBase = 0.05, sellingPrice = 10.0, purchasePrice = 7.0, physicalStock = 60.0, minStock = 20.0)
            )

            val pWheat = db.productDao().insertProduct(
                ProductEntity(name = "Wheat Flour / గోధుమ పిండి", categoryName = "Flour", baseUnit = "KG", trackByBaseUnit = false)
            )
            val optWheatBox = db.productDao().insertOption(
                SellingOptionEntity(productId = pWheat, varietyName = "10 KG Master Box", sellingUnit = "Box", packageContentsQty = 10.0, packageContentsUnit = "KG", conversionFactorToBase = 10.0, sellingPrice = 500.0, purchasePrice = 410.0, physicalStock = 18.0, minStock = 6.0)
            )
            db.productDao().insertOption(
                SellingOptionEntity(productId = pWheat, varietyName = "50 KG Sack", sellingUnit = "Bag", packageContentsQty = 50.0, packageContentsUnit = "KG", conversionFactorToBase = 50.0, sellingPrice = 2250.0, purchasePrice = 1900.0, physicalStock = 4.0, minStock = 5.0) // Low stock!
            )

            val pChilli = db.productDao().insertProduct(
                ProductEntity(name = "Guntur Chilli Powder / కారం పొడి", categoryName = "Spices", baseUnit = "KG", trackByBaseUnit = false)
            )
            val optChilliPkt = db.productDao().insertOption(
                SellingOptionEntity(productId = pChilli, varietyName = "200g Retail Packet", sellingUnit = "Packet", packageContentsQty = 200.0, packageContentsUnit = "g", conversionFactorToBase = 0.2, sellingPrice = 68.0, purchasePrice = 52.0, physicalStock = 35.0, minStock = 40.0) // Low stock!
            )
            db.productDao().insertOption(
                SellingOptionEntity(productId = pChilli, varietyName = "5 KG Bulk Pack", sellingUnit = "Bag", packageContentsQty = 5.0, packageContentsUnit = "KG", conversionFactorToBase = 5.0, sellingPrice = 1450.0, purchasePrice = 1150.0, physicalStock = 15.0, minStock = 5.0)
            )

            val pSona = db.productDao().insertProduct(
                ProductEntity(name = "Sona Masoori Rice / సోనా మసూరి బియ్యం", categoryName = "Rice", baseUnit = "KG", trackByBaseUnit = false)
            )
            db.productDao().insertOption(
                SellingOptionEntity(productId = pSona, varietyName = "25 KG Premium Bag", sellingUnit = "Bag", packageContentsQty = 25.0, packageContentsUnit = "KG", conversionFactorToBase = 25.0, sellingPrice = 1380.0, purchasePrice = 1210.0, physicalStock = 42.0, minStock = 10.0)
            )

            // Supplier & sample purchase
            val sup1 = db.supplierPurchaseDao().insertSupplier(
                SupplierEntity(name = "K. Narayana", businessName = "Rayalaseema Agro Mills", mobile = "9849011223", address = "Industrial Estate, Hindupur", gstin = "37AABCR1234F1Z5")
            )
            db.supplierPurchaseDao().insertPurchase(
                PurchaseEntity(
                    purchaseInvoiceNumber = "PUR-881",
                    purchaseDate = now - 3 * day,
                    supplierId = sup1,
                    supplierNameSnapshot = "K. Narayana",
                    supplierBusinessSnapshot = "Rayalaseema Agro Mills",
                    totalAmount = 19500.0,
                    amountPaid = 12000.0,
                    remainingPayable = 7500.0,
                    notes = "10 Bags Ragi Flour stock",
                    createdBy = "Owner"
                )
            )

            // Sample Invoices + Ledger + Payments
            val inv1Id = db.invoiceDao().insert(
                InvoiceEntity(
                    invoiceNumber = "INV-1001",
                    invoiceDate = now - 2 * day,
                    customerId = c1,
                    customerNameSnapshot = "Ravi",
                    shopNameSnapshot = "Ravi Traders",
                    customerMobileSnapshot = "9848112233",
                    customerAddressSnapshot = "Subhash Road, Anantapur",
                    subtotal = 7000.0,
                    grandTotal = 7000.0,
                    paymentStatus = PaymentStatus.PARTIAL.name,
                    amountPaid = 4500.0,
                    remainingBalance = 2500.0,
                    totalKg = 5.0,
                    totalBags = 2.0,
                    totalPackets = 10.0,
                    totalBoxes = 3.0,
                    createdByEmployeeId = 1L,
                    createdByName = "Owner"
                )
            )
            db.invoiceDao().insertItems(
                listOf(
                    InvoiceItemEntity(invoiceId = inv1Id, productId = pRagi, sellingOptionId = optRagiKg, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "Loose KG", sellingUnitSnapshot = "KG", quantity = 5.0, rate = 110.0, lineAmount = 550.0, purchaseCostEstimate = 425.0, baseQuantityDeducted = 5.0),
                    InvoiceItemEntity(invoiceId = inv1Id, productId = pRagi, sellingOptionId = optRagiBag, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "25 KG Bag", sellingUnitSnapshot = "Bag", quantity = 2.0, rate = 2400.0, lineAmount = 4800.0, purchaseCostEstimate = 3900.0, baseQuantityDeducted = 50.0),
                    InvoiceItemEntity(invoiceId = inv1Id, productId = pRagi, sellingOptionId = optRagiPkt, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "100g Packet", sellingUnitSnapshot = "Packet", quantity = 10.0, rate = 15.0, lineAmount = 150.0, purchaseCostEstimate = 110.0, baseQuantityDeducted = 1.0),
                    InvoiceItemEntity(invoiceId = inv1Id, productId = pWheat, sellingOptionId = optWheatBox, productNameSnapshot = "Wheat Flour / గోధుమ పిండి", varietyNameSnapshot = "10 KG Master Box", sellingUnitSnapshot = "Box", quantity = 3.0, rate = 500.0, lineAmount = 1500.0, purchaseCostEstimate = 1230.0)
                )
            )
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(customerId = c1, entryDate = now - 2 * day, entryType = "INVOICE", referenceNumber = "INV-1001", referenceId = inv1Id, description = "Wholesale Bill INV-1001", debitAmount = 7000.0, creditAmount = 0.0, runningBalance = 7000.0)
            )
            val pay1Id = db.paymentLedgerDao().insertPayment(
                PaymentEntity(receiptNumber = "RCP-201", paymentDate = now - day, customerId = c1, customerNameSnapshot = "Ravi", shopNameSnapshot = "Ravi Traders", amountReceived = 4500.0, allocatedAmount = 4500.0, paymentMethod = PaymentMethod.UPI.name, referenceNumber = "UPI-MANUAL-4500", relatedInvoiceId = inv1Id, relatedInvoiceNumber = "INV-1001", recordedByEmployeeId = 1L, recordedByName = "Owner")
            )
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(customerId = c1, entryDate = now - day, entryType = "PAYMENT", referenceNumber = "RCP-201", referenceId = pay1Id, description = "Payment Received (UPI)", debitAmount = 0.0, creditAmount = 4500.0, runningBalance = 2500.0)
            )

            // Telugu customer bill today
            val inv2Id = db.invoiceDao().insert(
                InvoiceEntity(
                    invoiceNumber = "INV-1002",
                    invoiceDate = now,
                    customerId = c2,
                    customerNameSnapshot = "వెంకటేష్",
                    shopNameSnapshot = "శ్రీ లక్ష్మీ కిరాణా & జనరల్ స్టోర్స్",
                    customerMobileSnapshot = "9440223344",
                    customerAddressSnapshot = "పాత ఊరు, అనంతపురం",
                    subtotal = 3760.0,
                    grandTotal = 3760.0,
                    paymentStatus = PaymentStatus.PREPAID.name,
                    amountPaid = 3760.0,
                    remainingBalance = 0.0,
                    totalBags = 1.0,
                    totalPackets = 20.0,
                    createdByEmployeeId = 1L,
                    createdByName = "Owner"
                )
            )
            db.invoiceDao().insertItems(
                listOf(
                    InvoiceItemEntity(invoiceId = inv2Id, productId = pRagi, sellingOptionId = optRagiBag, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "25 KG Bag", sellingUnitSnapshot = "Bag", quantity = 1.0, rate = 2400.0, lineAmount = 2400.0, purchaseCostEstimate = 1950.0, baseQuantityDeducted = 25.0),
                    InvoiceItemEntity(invoiceId = inv2Id, productId = pChilli, sellingOptionId = optChilliPkt, productNameSnapshot = "Guntur Chilli Powder / కారం పొడి", varietyNameSnapshot = "200g Retail Packet", sellingUnitSnapshot = "Packet", quantity = 20.0, rate = 68.0, lineAmount = 1360.0, purchaseCostEstimate = 1040.0)
                )
            )
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(customerId = c2, entryDate = now, entryType = "INVOICE", referenceNumber = "INV-1002", referenceId = inv2Id, description = "Wholesale Bill INV-1002", debitAmount = 3760.0, creditAmount = 0.0, runningBalance = 3760.0)
            )
            val pay2Id = db.paymentLedgerDao().insertPayment(
                PaymentEntity(receiptNumber = "RCP-202", paymentDate = now, customerId = c2, customerNameSnapshot = "వెంకటేష్", shopNameSnapshot = "శ్రీ లక్ష్మీ కిరాణా & జనరల్ స్టోర్స్", amountReceived = 3760.0, allocatedAmount = 3760.0, paymentMethod = PaymentMethod.CASH.name, relatedInvoiceId = inv2Id, relatedInvoiceNumber = "INV-1002", recordedByEmployeeId = 1L, recordedByName = "Owner")
            )
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(customerId = c2, entryDate = now, entryType = "PAYMENT", referenceNumber = "RCP-202", referenceId = pay2Id, description = "Payment Received (Cash)", debitAmount = 0.0, creditAmount = 3760.0, runningBalance = 0.0)
            )

            // Sample Pending Order from Sales Executive
            val ord1Id = db.orderDao().insert(
                OrderEntity(
                    orderNumber = "ORD-501",
                    orderDate = now,
                    customerId = c3,
                    customerNameSnapshot = "Mahesh Gupta",
                    shopNameSnapshot = "Balaji Super Mart",
                    customerMobileSnapshot = "9989334455",
                    status = OrderStatus.PENDING.name,
                    totalEstimatedAmount = 6100.0,
                    notes = "Morning delivery requested before 11 AM",
                    salesExecutiveId = salesExecId,
                    salesExecutiveName = "Suresh Reddy"
                )
            )
            db.orderDao().insertItems(
                listOf(
                    OrderItemEntity(orderId = ord1Id, productId = pRagi, sellingOptionId = optRagiBag, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "25 KG Bag", sellingUnitSnapshot = "Bag", quantity = 2.0, rate = 2400.0, lineAmount = 4800.0),
                    OrderItemEntity(orderId = ord1Id, productId = pRagi, sellingOptionId = optRagiPkt, productNameSnapshot = "Ragi Flour / రాగి పిండి", varietyNameSnapshot = "100g Packet", sellingUnitSnapshot = "Packet", quantity = 20.0, rate = 15.0, lineAmount = 300.0),
                    OrderItemEntity(orderId = ord1Id, productId = pWheat, sellingOptionId = optWheatBox, productNameSnapshot = "Wheat Flour / గోధుమ పిండి", varietyNameSnapshot = "10 KG Master Box", sellingUnitSnapshot = "Box", quantity = 2.0, rate = 500.0, lineAmount = 1000.0)
                )
            )

            val b = db.businessDao().getBusiness() ?: BusinessEntity()
            db.businessDao().upsert(b.copy(nextInvoiceNumber = 1003, nextOrderNumber = 502, nextReceiptNumber = 203))
            db.settingsMetaDao().insertActivityLog(
                ActivityLogEntity(employeeId = 1L, employeeName = "Owner", action = "SAMPLE_DATA_LOADED", entityType = "SYSTEM", entityId = 0L, details = "Loaded sample wholesale products, customers, bills & orders")
            )
        }
    }

    // --- Business & Settings ---
    suspend fun updateBusiness(business: BusinessEntity) {
        db.businessDao().upsert(business.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun savePrintTemplate(template: PrintTemplateEntity, setAsDefault: Boolean): Long {
        return db.withTransaction {
            if (setAsDefault) db.settingsMetaDao().clearDefaultTemplate()
            val id = db.settingsMetaDao().insertPrintTemplate(template.copy(isDefault = setAsDefault || template.isDefault))
            id
        }
    }

    suspend fun deletePrintTemplate(id: Long) {
        db.settingsMetaDao().deletePrintTemplate(id)
    }

    suspend fun simulateSyncNow() {
        db.settingsMetaDao().markAllPendingAs(SyncStatus.SYNCED.name)
    }

    // --- Customers ---
    suspend fun findPotentialDuplicates(name: String, shopName: String, mobile: String): List<CustomerEntity> =
        db.customerDao().findPotentialDuplicates(name.trim(), shopName.trim(), mobile.trim())

    suspend fun saveCustomer(customer: CustomerEntity, actor: EmployeeEntity): Long {
        return db.withTransaction {
            val id = db.customerDao().insert(customer)
            logActivity(actor, if (customer.id == 0L) "ADD_CUSTOMER" else "EDIT_CUSTOMER", "CUSTOMER", id, "${customer.name} (${customer.shopName})")
            queueSync("CUSTOMER", id, "UPSERT", customer.shopName)
            id
        }
    }

    // --- Categories & Products ---
    suspend fun addCategory(name: String) {
        if (name.isNotBlank()) db.productDao().insertCategory(CategoryEntity(name = name.trim()))
    }

    suspend fun saveProduct(product: ProductEntity, actor: EmployeeEntity): Long {
        return db.withTransaction {
            val isNew = product.id == 0L
            val id = db.productDao().insertProduct(product)
            if (isNew && product.trackByBaseUnit && product.basePhysicalStock > 0) {
                db.productDao().insertStockMovement(
                    StockMovementEntity(
                        productId = id, sellingOptionId = null, productName = product.name, optionName = "Base (${product.baseUnit})",
                        movementType = StockMovementType.OPENING.name, quantityDelta = product.basePhysicalStock, unit = product.baseUnit,
                        reason = "Opening stock", performedBy = actor.name
                    )
                )
            }
            logActivity(actor, if (isNew) "ADD_PRODUCT" else "EDIT_PRODUCT", "PRODUCT", id, product.name)
            queueSync("PRODUCT", id, "UPSERT", product.name)
            id
        }
    }

    suspend fun saveSellingOption(option: SellingOptionEntity, productName: String, actor: EmployeeEntity): Long {
        return db.withTransaction {
            val isNew = option.id == 0L
            val id = db.productDao().insertOption(option)
            if (isNew && option.physicalStock > 0) {
                db.productDao().insertStockMovement(
                    StockMovementEntity(
                        productId = option.productId, sellingOptionId = id, productName = productName, optionName = option.varietyName,
                        movementType = StockMovementType.OPENING.name, quantityDelta = option.physicalStock, unit = option.sellingUnit,
                        reason = "Opening stock", performedBy = actor.name
                    )
                )
            }
            logActivity(actor, if (isNew) "ADD_SELLING_OPTION" else "EDIT_SELLING_OPTION", "SELLING_OPTION", id, "$productName - ${option.varietyName} (${Formatters.formatRupee(option.sellingPrice)})")
            id
        }
    }

    suspend fun adjustStock(
        productId: Long,
        sellingOptionId: Long?,
        delta: Double,
        movementType: StockMovementType,
        reason: String,
        batchNumber: String = "",
        expiryDate: String = "",
        actor: EmployeeEntity
    ): Result<Unit> = runCatching {
        db.withTransaction {
            val product = db.productDao().getProductById(productId) ?: error("Product not found")
            if (product.trackByBaseUnit || sellingOptionId == null) {
                val updated = Formatters.roundMoney(product.basePhysicalStock + delta)
                db.productDao().updateProduct(product.copy(basePhysicalStock = updated))
                db.productDao().insertStockMovement(
                    StockMovementEntity(
                        productId = product.id, sellingOptionId = null, productName = product.name, optionName = "Base (${product.baseUnit})",
                        movementType = movementType.name, quantityDelta = delta, unit = product.baseUnit,
                        reason = reason.ifBlank { movementType.name }, performedBy = actor.name
                    )
                )
            } else {
                val option = db.productDao().getOptionById(sellingOptionId) ?: error("Selling option not found")
                val updated = Formatters.roundMoney(option.physicalStock + delta)
                db.productDao().updateOption(
                    option.copy(
                        physicalStock = updated,
                        batchNumber = batchNumber.ifBlank { option.batchNumber },
                        expiryDate = expiryDate.ifBlank { option.expiryDate }
                    )
                )
                db.productDao().insertStockMovement(
                    StockMovementEntity(
                        productId = product.id, sellingOptionId = option.id, productName = product.name, optionName = option.varietyName,
                        movementType = movementType.name, quantityDelta = delta, unit = option.sellingUnit,
                        reason = reason.ifBlank { movementType.name }, performedBy = actor.name
                    )
                )
            }
            logActivity(actor, "STOCK_ADJUSTMENT", "INVENTORY", productId, "${product.name}: $delta ($reason)")
        }
    }

    // --- Billing / Invoices ---
    suspend fun createInvoice(
        customer: CustomerEntity,
        items: List<DraftLineItem>,
        invoiceDate: Long,
        discountAmount: Double,
        additionalCharges: Double,
        gstEnabled: Boolean,
        gstPercent: Double,
        paymentStatusChoice: PaymentStatus,
        amountPaidInput: Double,
        notes: String,
        sourceOrderId: Long? = null,
        actor: EmployeeEntity
    ): Result<InvoiceEntity> = runCatching {
        require(items.isNotEmpty()) { "Add at least one product to create a bill." }
        db.withTransaction {
            // Prevent duplicate invoicing of the same order
            if (sourceOrderId != null) {
                val existing = db.invoiceDao().findBySourceOrder(sourceOrderId)
                require(existing == null) { "This order is already converted to ${existing?.invoiceNumber}." }
            }

            val business = db.businessDao().getBusiness() ?: BusinessEntity()
            val invNum = "${business.invoicePrefix}-${business.nextInvoiceNumber}"

            // Calculate totals cleanly with BigDecimal
            val subtotal = Formatters.roundMoney(items.sumOf { it.lineAmount })
            val disc = Formatters.roundMoney(discountAmount.coerceAtLeast(0.0))
            val taxable = Formatters.roundMoney((subtotal - disc).coerceAtLeast(0.0))
            val gstAmt = if (gstEnabled && gstPercent > 0) Formatters.roundMoney(taxable * gstPercent / 100.0) else 0.0
            val extra = Formatters.roundMoney(additionalCharges.coerceAtLeast(0.0))
            val grandTotal = Formatters.roundMoney(taxable + gstAmt + extra)

            val paid = Formatters.roundMoney(amountPaidInput.coerceIn(0.0, grandTotal))
            val remaining = Formatters.roundMoney((grandTotal - paid).coerceAtLeast(0.0))
            val computedStatus = when {
                remaining <= 0.01 -> PaymentStatus.PREPAID
                paid > 0.01 -> PaymentStatus.PARTIAL
                else -> if (paymentStatusChoice == PaymentStatus.PREPAID && paid >= grandTotal) PaymentStatus.PREPAID else PaymentStatus.POSTPAID
            }

            // Unit summaries
            var totalKg = 0.0
            var totalBags = 0.0
            var totalPackets = 0.0
            var totalBoxes = 0.0
            var totalPieces = 0.0
            items.forEach { item ->
                when (SellingUnit.fromString(item.sellingUnit)) {
                    SellingUnit.KG -> totalKg += item.quantity
                    SellingUnit.BAG -> totalBags += item.quantity
                    SellingUnit.PACKET -> totalPackets += item.quantity
                    SellingUnit.BOX -> totalBoxes += item.quantity
                    SellingUnit.PIECE -> totalPieces += item.quantity
                    else -> Unit
                }
            }

            // Validate and deduct stock (supports both base-unit conversion and package stock!)
            val preparedItems = mutableListOf<InvoiceItemEntity>()
            for (line in items) {
                val product = db.productDao().getProductById(line.productId) ?: error("Product not found: ${line.productName}")
                val option = db.productDao().getOptionById(line.sellingOptionId) ?: error("Selling option not found: ${line.varietyName}")
                var baseDeducted = 0.0

                if (product.trackByBaseUnit) {
                    baseDeducted = Formatters.roundMoney(line.quantity * option.conversionFactorToBase)
                    val available = Formatters.roundMoney(product.basePhysicalStock - product.baseReservedStock)
                    if (!business.allowNegativeStock && available < baseDeducted) {
                        error("Insufficient stock for ${product.name}. Available: ${Formatters.formatQty(available)} ${product.baseUnit}, Required: ${Formatters.formatQty(baseDeducted)} ${product.baseUnit}")
                    }
                    db.productDao().updateProduct(product.copy(basePhysicalStock = Formatters.roundMoney(product.basePhysicalStock - baseDeducted)))
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = option.id, productName = product.name, optionName = option.varietyName,
                            movementType = StockMovementType.SALE.name, quantityDelta = -baseDeducted, unit = product.baseUnit,
                            reason = "Sale ${invNum} (${Formatters.formatQty(line.quantity)} ${option.sellingUnit})",
                            referenceNumber = invNum, performedBy = actor.name
                        )
                    )
                } else {
                    val available = Formatters.roundMoney(option.physicalStock - option.reservedStock)
                    if (!business.allowNegativeStock && available < line.quantity) {
                        error("Insufficient stock for ${product.name} (${option.varietyName}). Available: ${Formatters.formatQty(available)} ${option.sellingUnit}")
                    }
                    db.productDao().updateOption(option.copy(physicalStock = Formatters.roundMoney(option.physicalStock - line.quantity)))
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = option.id, productName = product.name, optionName = option.varietyName,
                            movementType = StockMovementType.SALE.name, quantityDelta = -line.quantity, unit = option.sellingUnit,
                            reason = "Sale $invNum", referenceNumber = invNum, performedBy = actor.name
                        )
                    )
                }

                preparedItems.add(
                    InvoiceItemEntity(
                        invoiceId = 0L,
                        productId = product.id,
                        sellingOptionId = option.id,
                        productNameSnapshot = product.name,
                        varietyNameSnapshot = option.varietyName,
                        sellingUnitSnapshot = line.sellingUnit,
                        quantity = line.quantity,
                        rate = line.rate,
                        lineAmount = line.lineAmount,
                        purchaseCostEstimate = Formatters.multiplyMoney(line.quantity, option.purchasePrice),
                        baseQuantityDeducted = baseDeducted
                    )
                )
            }

            val invoice = InvoiceEntity(
                invoiceNumber = invNum,
                invoiceDate = invoiceDate,
                customerId = customer.id,
                customerNameSnapshot = customer.name,
                shopNameSnapshot = customer.shopName,
                customerMobileSnapshot = customer.mobile,
                customerAddressSnapshot = customer.address,
                subtotal = subtotal,
                discountAmount = disc,
                gstEnabled = gstEnabled,
                gstPercent = if (gstEnabled) gstPercent else 0.0,
                gstAmount = gstAmt,
                additionalCharges = extra,
                grandTotal = grandTotal,
                paymentStatus = computedStatus.name,
                amountPaid = paid,
                remainingBalance = remaining,
                totalKg = Formatters.roundMoney(totalKg),
                totalBags = Formatters.roundMoney(totalBags),
                totalPackets = Formatters.roundMoney(totalPackets),
                totalBoxes = Formatters.roundMoney(totalBoxes),
                totalPieces = Formatters.roundMoney(totalPieces),
                sourceOrderId = sourceOrderId,
                status = InvoiceStatus.FINALIZED.name,
                createdByEmployeeId = actor.id,
                createdByName = actor.name,
                notes = notes.trim(),
                syncStatus = SyncStatus.PENDING.name
            )
            val invoiceId = db.invoiceDao().insert(invoice)
            db.invoiceDao().insertItems(preparedItems.map { it.copy(invoiceId = invoiceId) })

            // Customer ledger debit for invoice
            val prevBalance = db.paymentLedgerDao().getLatestLedgerForCustomer(customer.id)?.runningBalance ?: 0.0
            val balanceAfterInv = Formatters.roundMoney(prevBalance + grandTotal)
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(
                    customerId = customer.id, entryDate = invoiceDate, entryType = "INVOICE",
                    referenceNumber = invNum, referenceId = invoiceId,
                    description = "Bill $invNum", debitAmount = grandTotal, creditAmount = 0.0,
                    runningBalance = balanceAfterInv
                )
            )

            var nextReceipt = business.nextReceiptNumber
            // If initial payment was received at billing, record Payment + Allocation + Ledger Credit
            if (paid > 0.01) {
                val rcpNum = "RCP-$nextReceipt"
                nextReceipt++
                val payId = db.paymentLedgerDao().insertPayment(
                    PaymentEntity(
                        receiptNumber = rcpNum, paymentDate = invoiceDate, customerId = customer.id,
                        customerNameSnapshot = customer.name, shopNameSnapshot = customer.shopName,
                        amountReceived = paid, allocatedAmount = paid, unallocatedAdvance = 0.0,
                        paymentMethod = PaymentMethod.CASH.name, relatedInvoiceId = invoiceId,
                        relatedInvoiceNumber = invNum, notes = "Received with $invNum",
                        recordedByEmployeeId = actor.id, recordedByName = actor.name,
                        syncStatus = SyncStatus.PENDING.name
                    )
                )
                db.paymentLedgerDao().insertAllocations(
                    listOf(PaymentAllocationEntity(paymentId = payId, invoiceId = invoiceId, invoiceNumber = invNum, allocatedAmount = paid))
                )
                val balanceAfterPay = Formatters.roundMoney(balanceAfterInv - paid)
                db.paymentLedgerDao().insertLedgerEntry(
                    LedgerEntryEntity(
                        customerId = customer.id, entryDate = invoiceDate, entryType = "PAYMENT",
                        referenceNumber = rcpNum, referenceId = payId,
                        description = "Payment against $invNum", debitAmount = 0.0, creditAmount = paid,
                        runningBalance = balanceAfterPay
                    )
                )
            }

            // Link source order if converted
            if (sourceOrderId != null) {
                db.orderDao().getById(sourceOrderId)?.let { ord ->
                    db.orderDao().update(ord.copy(convertedInvoiceId = invoiceId, status = OrderStatus.DELIVERED.name))
                }
            }

            db.businessDao().upsert(
                business.copy(nextInvoiceNumber = business.nextInvoiceNumber + 1, nextReceiptNumber = nextReceipt)
            )
            logActivity(actor, "CREATE_INVOICE", "INVOICE", invoiceId, "$invNum for ${customer.shopName} (${Formatters.formatRupee(grandTotal)})")
            queueSync("INVOICE", invoiceId, "CREATE", "$invNum (${Formatters.formatRupee(grandTotal)})")

            invoice.copy(id = invoiceId)
        }
    }

    suspend fun cancelInvoice(invoiceId: Long, reason: String, actor: EmployeeEntity): Result<Unit> = runCatching {
        db.withTransaction {
            val inv = db.invoiceDao().getById(invoiceId) ?: error("Invoice not found")
            require(inv.status != InvoiceStatus.CANCELLED.name) { "Invoice is already cancelled." }
            val items = db.invoiceDao().getItemsForInvoice(invoiceId)

            // Restore stock cleanly
            for (item in items) {
                val product = db.productDao().getProductById(item.productId) ?: continue
                if (product.trackByBaseUnit && item.baseQuantityDeducted > 0) {
                    db.productDao().updateProduct(product.copy(basePhysicalStock = Formatters.roundMoney(product.basePhysicalStock + item.baseQuantityDeducted)))
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = item.sellingOptionId, productName = product.name, optionName = item.varietyNameSnapshot,
                            movementType = StockMovementType.CANCELLATION.name, quantityDelta = item.baseQuantityDeducted, unit = product.baseUnit,
                            reason = "Cancelled ${inv.invoiceNumber}: $reason", referenceNumber = inv.invoiceNumber, performedBy = actor.name
                        )
                    )
                } else {
                    val opt = db.productDao().getOptionById(item.sellingOptionId)
                    if (opt != null) {
                        db.productDao().updateOption(opt.copy(physicalStock = Formatters.roundMoney(opt.physicalStock + item.quantity)))
                        db.productDao().insertStockMovement(
                            StockMovementEntity(
                                productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                                movementType = StockMovementType.CANCELLATION.name, quantityDelta = item.quantity, unit = opt.sellingUnit,
                                reason = "Cancelled ${inv.invoiceNumber}: $reason", referenceNumber = inv.invoiceNumber, performedBy = actor.name
                            )
                        )
                    }
                }
            }

            db.invoiceDao().update(inv.copy(status = InvoiceStatus.CANCELLED.name, remainingBalance = 0.0, notes = "${inv.notes} [CANCELLED: $reason]".trim()))

            val prevBalance = db.paymentLedgerDao().getLatestLedgerForCustomer(inv.customerId)?.runningBalance ?: 0.0
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(
                    customerId = inv.customerId, entryDate = System.currentTimeMillis(), entryType = "CANCELLATION",
                    referenceNumber = inv.invoiceNumber, referenceId = inv.id,
                    description = "Bill Cancelled (${inv.invoiceNumber}): $reason",
                    debitAmount = 0.0, creditAmount = inv.grandTotal,
                    runningBalance = Formatters.roundMoney(prevBalance - inv.grandTotal)
                )
            )
            logActivity(actor, "CANCEL_INVOICE", "INVOICE", inv.id, "Cancelled ${inv.invoiceNumber}: $reason")
        }
    }

    // --- Orders ---
    suspend fun saveOrder(
        existingOrderId: Long? = null,
        customer: CustomerEntity,
        items: List<DraftLineItem>,
        status: OrderStatus,
        notes: String,
        actor: EmployeeEntity
    ): Result<OrderEntity> = runCatching {
        require(items.isNotEmpty()) { "Add at least one product to save the order." }
        db.withTransaction {
            val total = Formatters.roundMoney(items.sumOf { it.lineAmount })
            val savedOrder = if (existingOrderId != null) {
                val existing = db.orderDao().getById(existingOrderId) ?: error("Order not found")
                val updated = existing.copy(
                    customerId = customer.id,
                    customerNameSnapshot = customer.name,
                    shopNameSnapshot = customer.shopName,
                    customerMobileSnapshot = customer.mobile,
                    status = status.name,
                    totalEstimatedAmount = total,
                    notes = notes.trim(),
                    syncStatus = SyncStatus.PENDING.name
                )
                db.orderDao().update(updated)
                db.orderDao().deleteItemsForOrder(existingOrderId)
                db.orderDao().insertItems(items.map {
                    OrderItemEntity(
                        orderId = existingOrderId, productId = it.productId, sellingOptionId = it.sellingOptionId,
                        productNameSnapshot = it.productName, varietyNameSnapshot = it.varietyName,
                        sellingUnitSnapshot = it.sellingUnit, quantity = it.quantity, rate = it.rate, lineAmount = it.lineAmount
                    )
                })
                updated
            } else {
                val business = db.businessDao().getBusiness() ?: BusinessEntity()
                val orderNum = "ORD-${business.nextOrderNumber}"
                val newOrder = OrderEntity(
                    orderNumber = orderNum, orderDate = System.currentTimeMillis(),
                    customerId = customer.id, customerNameSnapshot = customer.name, shopNameSnapshot = customer.shopName,
                    customerMobileSnapshot = customer.mobile, status = status.name, totalEstimatedAmount = total,
                    notes = notes.trim(), salesExecutiveId = actor.id, salesExecutiveName = actor.name,
                    syncStatus = SyncStatus.PENDING.name
                )
                val orderId = db.orderDao().insert(newOrder)
                db.orderDao().insertItems(items.map {
                    OrderItemEntity(
                        orderId = orderId, productId = it.productId, sellingOptionId = it.sellingOptionId,
                        productNameSnapshot = it.productName, varietyNameSnapshot = it.varietyName,
                        sellingUnitSnapshot = it.sellingUnit, quantity = it.quantity, rate = it.rate, lineAmount = it.lineAmount
                    )
                })
                db.businessDao().upsert(business.copy(nextOrderNumber = business.nextOrderNumber + 1))
                logActivity(actor, "CREATE_ORDER", "ORDER", orderId, "$orderNum for ${customer.shopName}")
                queueSync("ORDER", orderId, "CREATE", "$orderNum (${customer.shopName})")
                newOrder.copy(id = orderId)
            }
            savedOrder
        }
    }

    suspend fun updateOrderStatus(orderId: Long, status: OrderStatus, actor: EmployeeEntity) {
        db.withTransaction {
            val ord = db.orderDao().getById(orderId) ?: return@withTransaction
            db.orderDao().update(ord.copy(status = status.name))
            logActivity(actor, "ORDER_STATUS", "ORDER", orderId, "${ord.orderNumber} -> ${status.name}")
        }
    }

    suspend fun duplicateOrder(orderId: Long, actor: EmployeeEntity): Result<OrderEntity> = runCatching {
        val orig = db.orderDao().getById(orderId) ?: error("Order not found")
        val origItems = db.orderDao().getItemsForOrder(orderId)
        val customer = db.customerDao().getById(orig.customerId) ?: CustomerEntity(
            id = orig.customerId, name = orig.customerNameSnapshot, shopName = orig.shopNameSnapshot, mobile = orig.customerMobileSnapshot
        )
        val drafts = origItems.map {
            DraftLineItem(it.productId, it.sellingOptionId, it.productNameSnapshot, it.varietyNameSnapshot, it.sellingUnitSnapshot, it.quantity, it.rate)
        }
        saveOrder(null, customer, drafts, OrderStatus.PENDING, orig.notes, actor).getOrThrow()
    }

    // --- Payments & Multi-Invoice Allocation ---
    suspend fun recordPayment(
        customer: CustomerEntity,
        paymentDate: Long,
        amountReceived: Double,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        selectedInvoiceIds: List<Long>,
        notes: String,
        actor: EmployeeEntity
    ): Result<PaymentEntity> = runCatching {
        require(amountReceived > 0.0) { "Enter a valid payment amount." }
        db.withTransaction {
            val business = db.businessDao().getBusiness() ?: BusinessEntity()
            val rcpNum = "RCP-${business.nextReceiptNumber}"

            val unpaidInvoices = db.invoiceDao().getUnpaidInvoicesForCustomer(customer.id)
            val targetInvoices = if (selectedInvoiceIds.isNotEmpty()) {
                unpaidInvoices.filter { it.id in selectedInvoiceIds }
            } else {
                unpaidInvoices
            }

            var remainingToAllocate = Formatters.roundMoney(amountReceived)
            val allocations = mutableListOf<PaymentAllocationEntity>()
            var totalAllocated = 0.0
            var firstInvoiceId: Long? = null
            var firstInvoiceNum = ""

            for (inv in targetInvoices) {
                if (remainingToAllocate <= 0.01) break
                val alloc = Formatters.roundMoney(minOf(remainingToAllocate, inv.remainingBalance))
                if (alloc <= 0.0) continue
                if (firstInvoiceId == null) {
                    firstInvoiceId = inv.id
                    firstInvoiceNum = inv.invoiceNumber
                }
                val newPaid = Formatters.roundMoney(inv.amountPaid + alloc)
                val newBal = Formatters.roundMoney((inv.grandTotal - newPaid).coerceAtLeast(0.0))
                val newStatus = if (newBal <= 0.01) PaymentStatus.PREPAID.name else PaymentStatus.PARTIAL.name
                db.invoiceDao().update(inv.copy(amountPaid = newPaid, remainingBalance = newBal, paymentStatus = newStatus))
                allocations.add(PaymentAllocationEntity(paymentId = 0L, invoiceId = inv.id, invoiceNumber = inv.invoiceNumber, allocatedAmount = alloc))
                totalAllocated = Formatters.roundMoney(totalAllocated + alloc)
                remainingToAllocate = Formatters.roundMoney(remainingToAllocate - alloc)
            }

            val payment = PaymentEntity(
                receiptNumber = rcpNum,
                paymentDate = paymentDate,
                customerId = customer.id,
                customerNameSnapshot = customer.name,
                shopNameSnapshot = customer.shopName,
                amountReceived = Formatters.roundMoney(amountReceived),
                allocatedAmount = totalAllocated,
                unallocatedAdvance = remainingToAllocate,
                paymentMethod = paymentMethod.name,
                referenceNumber = referenceNumber.trim(),
                relatedInvoiceId = firstInvoiceId,
                relatedInvoiceNumber = if (allocations.size > 1) "${allocations.size} bills" else firstInvoiceNum,
                notes = notes.trim(),
                recordedByEmployeeId = actor.id,
                recordedByName = actor.name,
                syncStatus = SyncStatus.PENDING.name
            )
            val paymentId = db.paymentLedgerDao().insertPayment(payment)
            if (allocations.isNotEmpty()) {
                db.paymentLedgerDao().insertAllocations(allocations.map { it.copy(paymentId = paymentId) })
            }

            val prevBalance = db.paymentLedgerDao().getLatestLedgerForCustomer(customer.id)?.runningBalance ?: 0.0
            val newRunning = Formatters.roundMoney(prevBalance - amountReceived)
            val desc = buildString {
                append("Payment $rcpNum (${paymentMethod.name})")
                if (remainingToAllocate > 0.01) append(" [Advance: ${Formatters.formatRupee(remainingToAllocate)}]")
            }
            db.paymentLedgerDao().insertLedgerEntry(
                LedgerEntryEntity(
                    customerId = customer.id, entryDate = paymentDate, entryType = "PAYMENT",
                    referenceNumber = rcpNum, referenceId = paymentId, description = desc,
                    debitAmount = 0.0, creditAmount = Formatters.roundMoney(amountReceived),
                    runningBalance = newRunning
                )
            )

            db.businessDao().upsert(business.copy(nextReceiptNumber = business.nextReceiptNumber + 1))
            logActivity(actor, "RECORD_PAYMENT", "PAYMENT", paymentId, "$rcpNum from ${customer.shopName}: ${Formatters.formatRupee(amountReceived)} (${paymentMethod.name})")
            queueSync("PAYMENT", paymentId, "CREATE", "$rcpNum ${Formatters.formatRupee(amountReceived)}")
            payment.copy(id = paymentId)
        }
    }

    // --- Suppliers & Purchases ---
    suspend fun saveSupplier(supplier: SupplierEntity, actor: EmployeeEntity): Long {
        return db.withTransaction {
            val id = db.supplierPurchaseDao().insertSupplier(supplier)
            logActivity(actor, "SAVE_SUPPLIER", "SUPPLIER", id, supplier.businessName)
            id
        }
    }

    suspend fun recordPurchase(
        supplier: SupplierEntity,
        purchaseInvoiceNo: String,
        purchaseDate: Long,
        items: List<DraftLineItem>,
        amountPaid: Double,
        notes: String,
        actor: EmployeeEntity
    ): Result<PurchaseEntity> = runCatching {
        require(items.isNotEmpty()) { "Add at least one item to record a purchase." }
        db.withTransaction {
            val total = Formatters.roundMoney(items.sumOf { it.lineAmount })
            val paid = Formatters.roundMoney(amountPaid.coerceIn(0.0, total))
            val payable = Formatters.roundMoney((total - paid).coerceAtLeast(0.0))

            val purchase = PurchaseEntity(
                purchaseInvoiceNumber = purchaseInvoiceNo.ifBlank { "PUR-${System.currentTimeMillis() % 10000}" },
                purchaseDate = purchaseDate,
                supplierId = supplier.id,
                supplierNameSnapshot = supplier.name,
                supplierBusinessSnapshot = supplier.businessName,
                totalAmount = total,
                amountPaid = paid,
                remainingPayable = payable,
                notes = notes.trim(),
                createdBy = actor.name
            )
            val purchaseId = db.supplierPurchaseDao().insertPurchase(purchase)
            db.supplierPurchaseDao().insertPurchaseItems(
                items.map {
                    PurchaseItemEntity(
                        purchaseId = purchaseId, productId = it.productId, sellingOptionId = it.sellingOptionId,
                        productNameSnapshot = it.productName, varietyNameSnapshot = it.varietyName,
                        sellingUnitSnapshot = it.sellingUnit, quantity = it.quantity, purchaseRate = it.rate, lineAmount = it.lineAmount
                    )
                }
            )

            // Automatically increase stock!
            for (line in items) {
                val product = db.productDao().getProductById(line.productId) ?: continue
                val opt = db.productDao().getOptionById(line.sellingOptionId) ?: continue
                if (product.trackByBaseUnit) {
                    val baseAdd = Formatters.roundMoney(line.quantity * opt.conversionFactorToBase)
                    db.productDao().updateProduct(product.copy(basePhysicalStock = Formatters.roundMoney(product.basePhysicalStock + baseAdd)))
                    db.productDao().updateOption(opt.copy(purchasePrice = line.rate))
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                            movementType = StockMovementType.PURCHASE.name, quantityDelta = baseAdd, unit = product.baseUnit,
                            reason = "Purchase ${purchase.purchaseInvoiceNumber} from ${supplier.businessName}",
                            referenceNumber = purchase.purchaseInvoiceNumber, performedBy = actor.name
                        )
                    )
                } else {
                    db.productDao().updateOption(opt.copy(physicalStock = Formatters.roundMoney(opt.physicalStock + line.quantity), purchasePrice = line.rate))
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                            movementType = StockMovementType.PURCHASE.name, quantityDelta = line.quantity, unit = opt.sellingUnit,
                            reason = "Purchase ${purchase.purchaseInvoiceNumber} from ${supplier.businessName}",
                            referenceNumber = purchase.purchaseInvoiceNumber, performedBy = actor.name
                        )
                    )
                }
            }
            logActivity(actor, "RECORD_PURCHASE", "PURCHASE", purchaseId, "${purchase.purchaseInvoiceNumber} from ${supplier.businessName} (${Formatters.formatRupee(total)})")
            purchase.copy(id = purchaseId)
        }
    }

    // --- Returns & Credit Notes ---
    suspend fun recordReturn(
        invoice: InvoiceEntity,
        items: List<DraftReturnItem>,
        settlementType: ReturnSettlementType,
        reason: String,
        actor: EmployeeEntity
    ): Result<ReturnEntity> = runCatching {
        val validItems = items.filter { it.returnedQty > 0 }
        require(validItems.isNotEmpty()) { "Enter return quantity for at least one item." }
        db.withTransaction {
            val business = db.businessDao().getBusiness() ?: BusinessEntity()
            val cnNum = "CN-${business.nextCreditNoteNumber}"
            val totalReturn = Formatters.roundMoney(validItems.sumOf { it.amount })

            val record = ReturnEntity(
                creditNoteNumber = cnNum,
                returnDate = System.currentTimeMillis(),
                originalInvoiceId = invoice.id,
                originalInvoiceNumber = invoice.invoiceNumber,
                customerId = invoice.customerId,
                customerNameSnapshot = invoice.customerNameSnapshot,
                shopNameSnapshot = invoice.shopNameSnapshot,
                totalReturnAmount = totalReturn,
                settlementType = settlementType.name,
                reason = reason.ifBlank { "Customer Return" },
                createdBy = actor.name
            )
            val returnId = db.returnDao().insertReturn(record)
            db.returnDao().insertReturnItems(
                validItems.map {
                    ReturnItemEntity(
                        returnId = returnId, productId = it.productId, sellingOptionId = it.sellingOptionId,
                        productNameSnapshot = it.productName, varietyNameSnapshot = it.varietyName,
                        sellingUnitSnapshot = it.sellingUnit, returnedQty = it.returnedQty,
                        rate = it.rate, amount = it.amount, condition = it.condition.name
                    )
                }
            )

            // Only restock SELLABLE items; DAMAGED items are logged without increasing sellable stock!
            for (item in validItems) {
                val product = db.productDao().getProductById(item.productId) ?: continue
                val opt = db.productDao().getOptionById(item.sellingOptionId) ?: continue
                if (item.condition == ReturnCondition.SELLABLE) {
                    if (product.trackByBaseUnit) {
                        val baseQty = Formatters.roundMoney(item.returnedQty * opt.conversionFactorToBase)
                        db.productDao().updateProduct(product.copy(basePhysicalStock = Formatters.roundMoney(product.basePhysicalStock + baseQty)))
                        db.productDao().insertStockMovement(
                            StockMovementEntity(
                                productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                                movementType = StockMovementType.RETURN_SELLABLE.name, quantityDelta = baseQty, unit = product.baseUnit,
                                reason = "Sellable Return $cnNum (${invoice.invoiceNumber})", referenceNumber = cnNum, performedBy = actor.name
                            )
                        )
                    } else {
                        db.productDao().updateOption(opt.copy(physicalStock = Formatters.roundMoney(opt.physicalStock + item.returnedQty)))
                        db.productDao().insertStockMovement(
                            StockMovementEntity(
                                productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                                movementType = StockMovementType.RETURN_SELLABLE.name, quantityDelta = item.returnedQty, unit = opt.sellingUnit,
                                reason = "Sellable Return $cnNum (${invoice.invoiceNumber})", referenceNumber = cnNum, performedBy = actor.name
                            )
                        )
                    }
                } else {
                    db.productDao().insertStockMovement(
                        StockMovementEntity(
                            productId = product.id, sellingOptionId = opt.id, productName = product.name, optionName = opt.varietyName,
                            movementType = StockMovementType.RETURN_DAMAGED.name, quantityDelta = 0.0, unit = opt.sellingUnit,
                            reason = "Damaged Return (${Formatters.formatQty(item.returnedQty)} ${opt.sellingUnit}) - Not restocked: $reason",
                            referenceNumber = cnNum, performedBy = actor.name
                        )
                    )
                }
            }

            if (settlementType == ReturnSettlementType.CUSTOMER_CREDIT) {
                val prevBalance = db.paymentLedgerDao().getLatestLedgerForCustomer(invoice.customerId)?.runningBalance ?: 0.0
                db.paymentLedgerDao().insertLedgerEntry(
                    LedgerEntryEntity(
                        customerId = invoice.customerId, entryDate = System.currentTimeMillis(), entryType = "CREDIT_NOTE",
                        referenceNumber = cnNum, referenceId = returnId,
                        description = "Credit Note $cnNum against ${invoice.invoiceNumber}",
                        debitAmount = 0.0, creditAmount = totalReturn,
                        runningBalance = Formatters.roundMoney(prevBalance - totalReturn)
                    )
                )
            }

            db.businessDao().upsert(business.copy(nextCreditNoteNumber = business.nextCreditNoteNumber + 1))
            logActivity(actor, "RECORD_RETURN", "RETURN", returnId, "$cnNum for ${invoice.invoiceNumber} (${Formatters.formatRupee(totalReturn)})")
            record.copy(id = returnId)
        }
    }

    // --- Employees ---
    suspend fun saveEmployee(employee: EmployeeEntity, actor: EmployeeEntity): Long {
        return db.withTransaction {
            val id = db.employeeDao().insert(employee)
            logActivity(actor, "SAVE_EMPLOYEE", "EMPLOYEE", id, "${employee.name} (${employee.role})")
            id
        }
    }

    suspend fun assignCustomerToEmployee(customerId: Long, employeeId: Long?, actor: EmployeeEntity) {
        db.withTransaction {
            val c = db.customerDao().getById(customerId) ?: return@withTransaction
            db.customerDao().update(c.copy(assignedEmployeeId = employeeId))
            logActivity(actor, "ASSIGN_CUSTOMER", "CUSTOMER", customerId, "${c.shopName} assigned to employee #$employeeId")
        }
    }

    private suspend fun logActivity(actor: EmployeeEntity, action: String, entityType: String, entityId: Long, details: String) {
        db.settingsMetaDao().insertActivityLog(
            ActivityLogEntity(
                employeeId = actor.id,
                employeeName = actor.name,
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }

    private suspend fun queueSync(entityType: String, entityId: Long, op: String, summary: String) {
        db.settingsMetaDao().insertSyncItem(
            SyncQueueEntity(entityType = entityType, entityId = entityId, operation = op, summary = summary, status = SyncStatus.PENDING.name)
        )
    }
}
