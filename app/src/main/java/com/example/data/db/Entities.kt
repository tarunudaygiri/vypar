package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String = "Sri Venkateswara Wholesale Traders",
    val logoUri: String = "",
    val address: String = "Main Market Road, Anantapur, Andhra Pradesh",
    val mobile: String = "9848012345",
    val gstin: String = "",
    val defaultGstPercent: Double = 5.0,
    val invoicePrefix: String = "INV",
    val nextInvoiceNumber: Int = 1001,
    val nextOrderNumber: Int = 501,
    val nextReceiptNumber: Int = 201,
    val nextCreditNoteNumber: Int = 101,
    val defaultLanguage: String = "en",
    val defaultInvoiceLanguage: String = "en",
    val allowNegativeStock: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String = "",
    val role: String, // EmployeeRole.name
    val pinHash: String = "",
    val isActive: Boolean = true,
    val canAddCustomer: Boolean = true,
    val canEditPrices: Boolean = false,
    val canCreateBill: Boolean = true,
    val canCreateOrder: Boolean = true,
    val canRecordPayment: Boolean = true,
    val canManageStock: Boolean = false,
    val canEditInvoiceDate: Boolean = false,
    val canCancelInvoice: Boolean = false,
    val canViewReports: Boolean = false,
    val preferredLanguage: String = "en",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers",
    indices = [Index("name"), Index("shopName"), Index("mobile")]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shopName: String,
    val mobile: String,
    val address: String = "",
    val assignedEmployeeId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "product_categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "products",
    indices = [Index("name"), Index("categoryName")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryName: String,
    val baseUnit: String = "KG", // Base measurement when trackByBaseUnit is true
    val trackByBaseUnit: Boolean = false,
    val basePhysicalStock: Double = 0.0,
    val baseReservedStock: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "selling_options",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class SellingOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val varietyName: String, // e.g., "Loose KG", "25 KG Bag", "100g Packet"
    val sellingUnit: String, // SellingUnit label or custom unit text
    val packageContentsQty: Double = 1.0, // e.g., 25.0 or 100.0
    val packageContentsUnit: String = "", // e.g., "KG", "g", "Pieces"
    val conversionFactorToBase: Double = 1.0, // e.g., 25.0 KG per Bag, 0.1 KG per 100g Packet
    val sellingPrice: Double,
    val purchasePrice: Double = 0.0,
    val physicalStock: Double = 0.0, // Used when product.trackByBaseUnit == false
    val reservedStock: Double = 0.0,
    val minStock: Double = 5.0,
    val batchNumber: String = "",
    val expiryDate: String = "",
    val isActive: Boolean = true
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index("sellingOptionId"), Index("timestamp")]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val sellingOptionId: Long?,
    val productName: String,
    val optionName: String,
    val movementType: String, // StockMovementType.name
    val quantityDelta: Double,
    val unit: String,
    val reason: String,
    val referenceNumber: String = "",
    val performedBy: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["invoiceNumber"], unique = true), Index("customerId"), Index("invoiceDate")]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val invoiceDate: Long,
    val customerId: Long,
    val customerNameSnapshot: String,
    val shopNameSnapshot: String,
    val customerMobileSnapshot: String,
    val customerAddressSnapshot: String,
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val gstEnabled: Boolean = false,
    val gstPercent: Double = 0.0,
    val gstAmount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val grandTotal: Double,
    val paymentStatus: String, // PREPAID, POSTPAID, PARTIAL
    val amountPaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val totalKg: Double = 0.0,
    val totalBags: Double = 0.0,
    val totalPackets: Double = 0.0,
    val totalBoxes: Double = 0.0,
    val totalPieces: Double = 0.0,
    val sourceOrderId: Long? = null,
    val status: String = "FINALIZED", // FINALIZED, CANCELLED, CORRECTED
    val createdByEmployeeId: Long = 1L,
    val createdByName: String = "",
    val notes: String = "",
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId"), Index("productId")]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val sellingOptionId: Long,
    val productNameSnapshot: String,
    val varietyNameSnapshot: String,
    val sellingUnitSnapshot: String,
    val quantity: Double,
    val rate: Double,
    val lineAmount: Double,
    val purchaseCostEstimate: Double = 0.0,
    val baseQuantityDeducted: Double = 0.0
)

@Entity(
    tableName = "orders",
    indices = [Index(value = ["orderNumber"], unique = true), Index("customerId"), Index("orderDate")]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val orderDate: Long,
    val customerId: Long,
    val customerNameSnapshot: String,
    val shopNameSnapshot: String,
    val customerMobileSnapshot: String = "",
    val status: String = "PENDING", // OrderStatus.name
    val totalEstimatedAmount: Double = 0.0,
    val notes: String = "",
    val salesExecutiveId: Long = 1L,
    val salesExecutiveName: String = "",
    val convertedInvoiceId: Long? = null,
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val sellingOptionId: Long,
    val productNameSnapshot: String,
    val varietyNameSnapshot: String,
    val sellingUnitSnapshot: String,
    val quantity: Double,
    val rate: Double,
    val lineAmount: Double
)

@Entity(
    tableName = "payments",
    indices = [Index(value = ["receiptNumber"], unique = true), Index("customerId"), Index("paymentDate")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val paymentDate: Long,
    val customerId: Long,
    val customerNameSnapshot: String,
    val shopNameSnapshot: String,
    val amountReceived: Double,
    val allocatedAmount: Double = 0.0,
    val unallocatedAdvance: Double = 0.0,
    val paymentMethod: String, // CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER
    val referenceNumber: String = "",
    val relatedInvoiceId: Long? = null,
    val relatedInvoiceNumber: String = "",
    val notes: String = "",
    val recordedByEmployeeId: Long = 1L,
    val recordedByName: String = "",
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paymentId"), Index("invoiceId")]
)
data class PaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val invoiceId: Long,
    val invoiceNumber: String,
    val allocatedAmount: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customer_ledger_entries",
    indices = [Index("customerId"), Index("entryDate")]
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val entryDate: Long,
    val entryType: String, // INVOICE, PAYMENT, CREDIT_NOTE, CANCELLATION
    val referenceNumber: String,
    val referenceId: Long,
    val description: String,
    val debitAmount: Double = 0.0,
    val creditAmount: Double = 0.0,
    val runningBalance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers", indices = [Index("name"), Index("businessName")])
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val businessName: String,
    val mobile: String,
    val address: String = "",
    val gstin: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchases", indices = [Index("supplierId"), Index("purchaseDate")])
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseInvoiceNumber: String,
    val purchaseDate: Long,
    val supplierId: Long,
    val supplierNameSnapshot: String,
    val supplierBusinessSnapshot: String = "",
    val totalAmount: Double,
    val amountPaid: Double = 0.0,
    val remainingPayable: Double = 0.0,
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("purchaseId")]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val sellingOptionId: Long,
    val productNameSnapshot: String,
    val varietyNameSnapshot: String,
    val sellingUnitSnapshot: String,
    val quantity: Double,
    val purchaseRate: Double,
    val lineAmount: Double
)

@Entity(tableName = "returns", indices = [Index("originalInvoiceId"), Index("customerId")])
data class ReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditNoteNumber: String,
    val returnDate: Long,
    val originalInvoiceId: Long,
    val originalInvoiceNumber: String,
    val customerId: Long,
    val customerNameSnapshot: String,
    val shopNameSnapshot: String = "",
    val totalReturnAmount: Double,
    val settlementType: String, // CUSTOMER_CREDIT, CASH_REFUND
    val reason: String,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "return_items",
    foreignKeys = [
        ForeignKey(
            entity = ReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("returnId")]
)
data class ReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val productId: Long,
    val sellingOptionId: Long,
    val productNameSnapshot: String,
    val varietyNameSnapshot: String,
    val sellingUnitSnapshot: String,
    val returnedQty: Double,
    val rate: Double,
    val amount: Double,
    val condition: String // SELLABLE, DAMAGED
)

@Entity(tableName = "print_templates")
data class PrintTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Default Wholesale Bill",
    val isDefault: Boolean = true,
    val invoiceTitle: String = "WHOLESALE BILL / INVOICE",
    val headerText: String = "Quality Wholesale Grains, Flours & Spices",
    val footerText: String = "Thank you for your business! Goods once sold will be checked at delivery.",
    val termsText: String = "1. Check weight & count before leaving. 2. Subject to local jurisdiction.",
    val fontSizeSp: Float = 10f,
    val fontFamily: String = "Noto Sans Telugu",
    val marginMm: Int = 10,
    val rowSpacingDp: Int = 4,
    val showBorders: Boolean = true,
    val showLogo: Boolean = true,
    val showCustomerAddress: Boolean = true,
    val showCustomerMobile: Boolean = true,
    val showPaymentInfo: Boolean = true,
    val showGstDetails: Boolean = true,
    val showUnitSummary: Boolean = true,
    val billsPerPage: Int = 1, // 1, 2, or 4
    val showCuttingGuides: Boolean = true
)

@Entity(tableName = "activity_logs", indices = [Index("timestamp")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val employeeName: String,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_operations", indices = [Index("status")])
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val operation: String,
    val summary: String,
    val status: String = "PENDING", // SYNCED, PENDING, FAILED
    val updatedAt: Long = System.currentTimeMillis()
)
