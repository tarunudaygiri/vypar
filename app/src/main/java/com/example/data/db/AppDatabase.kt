package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BusinessEntity::class,
        EmployeeEntity::class,
        CustomerEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        SellingOptionEntity::class,
        StockMovementEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        LedgerEntryEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        ReturnEntity::class,
        ReturnItemEntity::class,
        PrintTemplateEntity::class,
        ActivityLogEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun orderDao(): OrderDao
    abstract fun paymentLedgerDao(): PaymentLedgerDao
    abstract fun supplierPurchaseDao(): SupplierPurchaseDao
    abstract fun returnDao(): ReturnDao
    abstract fun settingsMetaDao(): SettingsMetaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vyapardesk_wholesale.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
