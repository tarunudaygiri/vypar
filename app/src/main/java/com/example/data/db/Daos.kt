package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE id = 1 LIMIT 1")
    fun observeBusiness(): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE id = 1 LIMIT 1")
    suspend fun getBusiness(): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(business: BusinessEntity)
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY id ASC")
    fun observeAll(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees ORDER BY id ASC")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(employee: EmployeeEntity): Long

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Query("DELETE FROM employees")
    suspend fun clearAll()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE isActive = 1 AND (mobile = :mobile OR (LOWER(name) = LOWER(:name) AND LOWER(shopName) = LOWER(:shopName)))")
    suspend fun findPotentialDuplicates(name: String, shopName: String, mobile: String): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun clearAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_categories ORDER BY name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM product_categories")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM selling_options ORDER BY productId ASC, id ASC")
    fun observeAllSellingOptions(): Flow<List<SellingOptionEntity>>

    @Query("SELECT * FROM selling_options ORDER BY id ASC")
    suspend fun getAllSellingOptions(): List<SellingOptionEntity>

    @Query("SELECT * FROM selling_options WHERE productId = :productId ORDER BY id ASC")
    suspend fun getOptionsForProduct(productId: Long): List<SellingOptionEntity>

    @Query("SELECT * FROM selling_options WHERE id = :id LIMIT 1")
    suspend fun getOptionById(id: Long): SellingOptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOption(option: SellingOptionEntity): Long

    @Update
    suspend fun updateOption(option: SellingOptionEntity)

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 500")
    fun observeStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY id ASC")
    suspend fun getAllStockMovements(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Query("DELETE FROM stock_movements")
    suspend fun clearStockMovements()

    @Query("DELETE FROM selling_options")
    suspend fun clearOptions()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM product_categories")
    suspend fun clearCategories()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY invoiceDate DESC, id DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY id ASC")
    suspend fun getAll(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE sourceOrderId = :orderId AND status != 'CANCELLED' LIMIT 1")
    suspend fun findBySourceOrder(orderId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND status != 'CANCELLED' AND remainingBalance > 0.01 ORDER BY invoiceDate ASC, id ASC")
    suspend fun getUnpaidInvoicesForCustomer(customerId: Long): List<InvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: InvoiceEntity): Long

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoice_items ORDER BY id ASC")
    fun observeAllItems(): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items ORDER BY id ASC")
    suspend fun getAllItems(): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    suspend fun getItemsForInvoice(invoiceId: Long): List<InvoiceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Query("DELETE FROM invoice_items")
    suspend fun clearItems()

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY orderDate DESC, id DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY id ASC")
    suspend fun getAll(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderEntity): Long

    @Update
    suspend fun update(order: OrderEntity)

    @Query("SELECT * FROM order_items ORDER BY id ASC")
    fun observeAllItems(): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items ORDER BY id ASC")
    suspend fun getAllItems(): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getItemsForOrder(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItemsForOrder(orderId: Long)

    @Query("DELETE FROM order_items")
    suspend fun clearItems()

    @Query("DELETE FROM orders")
    suspend fun clearOrders()
}

@Dao
interface PaymentLedgerDao {
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, id DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY id ASC")
    suspend fun getAllPayments(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payment_allocations ORDER BY id ASC")
    fun observeAllAllocations(): Flow<List<PaymentAllocationEntity>>

    @Query("SELECT * FROM payment_allocations ORDER BY id ASC")
    suspend fun getAllAllocations(): List<PaymentAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<PaymentAllocationEntity>)

    @Query("SELECT * FROM customer_ledger_entries ORDER BY entryDate ASC, id ASC")
    fun observeAllLedger(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM customer_ledger_entries ORDER BY id ASC")
    suspend fun getAllLedger(): List<LedgerEntryEntity>

    @Query("SELECT * FROM customer_ledger_entries WHERE customerId = :customerId ORDER BY entryDate DESC, id DESC LIMIT 1")
    suspend fun getLatestLedgerForCustomer(customerId: Long): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long

    @Query("DELETE FROM payment_allocations")
    suspend fun clearAllocations()

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Query("DELETE FROM customer_ledger_entries")
    suspend fun clearLedger()
}

@Dao
interface SupplierPurchaseDao {
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers ORDER BY id ASC")
    suspend fun getAllSuppliers(): List<SupplierEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC, id DESC")
    fun observeAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY id ASC")
    suspend fun getAllPurchases(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchase_items ORDER BY id ASC")
    fun observeAllPurchaseItems(): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items ORDER BY id ASC")
    suspend fun getAllPurchaseItems(): List<PurchaseItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("DELETE FROM purchase_items")
    suspend fun clearPurchaseItems()

    @Query("DELETE FROM purchases")
    suspend fun clearPurchases()

    @Query("DELETE FROM suppliers")
    suspend fun clearSuppliers()
}

@Dao
interface ReturnDao {
    @Query("SELECT * FROM returns ORDER BY returnDate DESC, id DESC")
    fun observeAllReturns(): Flow<List<ReturnEntity>>

    @Query("SELECT * FROM returns ORDER BY id ASC")
    suspend fun getAllReturns(): List<ReturnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(record: ReturnEntity): Long

    @Query("SELECT * FROM return_items ORDER BY id ASC")
    fun observeAllReturnItems(): Flow<List<ReturnItemEntity>>

    @Query("SELECT * FROM return_items ORDER BY id ASC")
    suspend fun getAllReturnItems(): List<ReturnItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnItems(items: List<ReturnItemEntity>)

    @Query("DELETE FROM return_items")
    suspend fun clearReturnItems()

    @Query("DELETE FROM returns")
    suspend fun clearReturns()
}

@Dao
interface SettingsMetaDao {
    @Query("SELECT * FROM print_templates ORDER BY isDefault DESC, id ASC")
    fun observePrintTemplates(): Flow<List<PrintTemplateEntity>>

    @Query("SELECT * FROM print_templates ORDER BY id ASC")
    suspend fun getAllPrintTemplates(): List<PrintTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrintTemplate(template: PrintTemplateEntity): Long

    @Update
    suspend fun updatePrintTemplate(template: PrintTemplateEntity)

    @Query("UPDATE print_templates SET isDefault = 0")
    suspend fun clearDefaultTemplate()

    @Query("DELETE FROM print_templates WHERE id = :id")
    suspend fun deletePrintTemplate(id: Long)

    @Query("DELETE FROM print_templates")
    suspend fun clearPrintTemplates()

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeActivityLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY id ASC")
    suspend fun getAllActivityLogs(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs")
    suspend fun clearActivityLogs()

    @Query("SELECT * FROM sync_operations ORDER BY updatedAt DESC LIMIT 100")
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: SyncQueueEntity): Long

    @Query("UPDATE sync_operations SET status = :status, updatedAt = :time WHERE status != 'SYNCED'")
    suspend fun markAllPendingAs(status: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_operations")
    suspend fun clearSyncQueue()
}
