package com.example.data.model

enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    TELUGU("te", "తెలుగు"),
    HINDI("hi", "हिन्दी");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class FontSizeOption(val scale: Float, val key: String) {
    NORMAL(1.0f, "normal"),
    LARGE(1.15f, "large"),
    EXTRA_LARGE(1.30f, "extra_large");

    companion object {
        fun fromKey(key: String): FontSizeOption = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

enum class EmployeeRole {
    OWNER,
    MANAGER,
    BILLING_STAFF,
    SALES_EXECUTIVE,
    INVENTORY_STAFF
}

enum class SellingUnit(val label: String, val shortLabel: String) {
    KG("KG", "KG"),
    BAG("Bag", "Bag"),
    PACKET("Packet", "Pkt"),
    BOX("Box", "Box"),
    PIECE("Piece", "Pc"),
    GRAM("Gram", "g"),
    LITRE("Litre", "L"),
    MILLILITRE("Millilitre", "ml"),
    CUSTOM("Custom", "Unit");

    companion object {
        fun fromString(value: String): SellingUnit =
            entries.firstOrNull { it.name.equals(value, true) || it.label.equals(value, true) } ?: CUSTOM
    }
}

enum class PaymentStatus {
    PREPAID,
    POSTPAID,
    PARTIAL
}

enum class PaymentMethod {
    CASH,
    UPI,
    BANK_TRANSFER,
    CHEQUE,
    OTHER
}

enum class OrderStatus {
    DRAFT,
    PENDING,
    CONFIRMED,
    PROCESSING,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}

enum class InvoiceStatus {
    FINALIZED,
    CANCELLED,
    CORRECTED
}

enum class StockMovementType {
    OPENING,
    PURCHASE,
    SALE,
    RETURN_SELLABLE,
    RETURN_DAMAGED,
    ADJUSTMENT,
    CANCELLATION
}

enum class ReturnSettlementType {
    CUSTOMER_CREDIT,
    CASH_REFUND
}

enum class ReturnCondition {
    SELLABLE,
    DAMAGED
}

enum class DateFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    ALL_TIME,
    CUSTOM
}

enum class SyncStatus {
    SYNCED,
    PENDING,
    FAILED
}

enum class AppScreen {
    DASHBOARD,
    CUSTOMERS,
    PRODUCTS,
    NEW_BILL,
    ORDERS,
    PAYMENTS,
    INVENTORY,
    SUPPLIERS,
    PURCHASES,
    EMPLOYEES,
    RETURNS,
    REPORTS,
    SETTINGS
}
