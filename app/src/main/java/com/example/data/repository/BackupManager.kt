package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.db.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupManager(private val context: Context, private val db: AppDatabase) {

    suspend fun exportToJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        db.businessDao().getBusiness()?.let { b ->
            root.put("business", JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("logoUri", b.logoUri)
                put("address", b.address)
                put("mobile", b.mobile)
                put("gstin", b.gstin)
                put("defaultGstPercent", b.defaultGstPercent)
                put("invoicePrefix", b.invoicePrefix)
                put("nextInvoiceNumber", b.nextInvoiceNumber)
                put("nextOrderNumber", b.nextOrderNumber)
                put("nextReceiptNumber", b.nextReceiptNumber)
                put("nextCreditNoteNumber", b.nextCreditNoteNumber)
                put("defaultLanguage", b.defaultLanguage)
                put("defaultInvoiceLanguage", b.defaultInvoiceLanguage)
                put("allowNegativeStock", b.allowNegativeStock)
                put("onboardingCompleted", b.onboardingCompleted)
            })
        }

        root.put("employees", JSONArray().apply {
            db.employeeDao().getAll().forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("mobile", e.mobile)
                    put("role", e.role); put("pinHash", e.pinHash); put("isActive", e.isActive)
                    put("canAddCustomer", e.canAddCustomer); put("canEditPrices", e.canEditPrices)
                    put("canCreateBill", e.canCreateBill); put("canCreateOrder", e.canCreateOrder)
                    put("canRecordPayment", e.canRecordPayment); put("canManageStock", e.canManageStock)
                    put("canEditInvoiceDate", e.canEditInvoiceDate); put("canCancelInvoice", e.canCancelInvoice)
                    put("canViewReports", e.canViewReports); put("preferredLanguage", e.preferredLanguage)
                    put("createdAt", e.createdAt)
                })
            }
        })

        root.put("customers", JSONArray().apply {
            db.customerDao().getAll().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("shopName", c.shopName)
                    put("mobile", c.mobile); put("address", c.address)
                    put("assignedEmployeeId", c.assignedEmployeeId ?: JSONObject.NULL)
                    put("isActive", c.isActive); put("createdAt", c.createdAt)
                })
            }
        })

        root.put("categories", JSONArray().apply {
            db.productDao().getAllCategories().forEach { cat ->
                put(JSONObject().apply {
                    put("id", cat.id); put("name", cat.name); put("isDefault", cat.isDefault)
                })
            }
        })

        root.put("products", JSONArray().apply {
            db.productDao().getAllProducts().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("categoryName", p.categoryName)
                    put("baseUnit", p.baseUnit); put("trackByBaseUnit", p.trackByBaseUnit)
                    put("basePhysicalStock", p.basePhysicalStock); put("baseReservedStock", p.baseReservedStock)
                    put("isActive", p.isActive); put("createdAt", p.createdAt)
                })
            }
        })

        root.put("sellingOptions", JSONArray().apply {
            db.productDao().getAllSellingOptions().forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id); put("productId", o.productId); put("varietyName", o.varietyName)
                    put("sellingUnit", o.sellingUnit); put("packageContentsQty", o.packageContentsQty)
                    put("packageContentsUnit", o.packageContentsUnit); put("conversionFactorToBase", o.conversionFactorToBase)
                    put("sellingPrice", o.sellingPrice); put("purchasePrice", o.purchasePrice)
                    put("physicalStock", o.physicalStock); put("reservedStock", o.reservedStock)
                    put("minStock", o.minStock); put("batchNumber", o.batchNumber)
                    put("expiryDate", o.expiryDate); put("isActive", o.isActive)
                })
            }
        })

        root.put("invoices", JSONArray().apply {
            db.invoiceDao().getAll().forEach { inv ->
                put(JSONObject().apply {
                    put("id", inv.id); put("invoiceNumber", inv.invoiceNumber); put("invoiceDate", inv.invoiceDate)
                    put("customerId", inv.customerId); put("customerNameSnapshot", inv.customerNameSnapshot)
                    put("shopNameSnapshot", inv.shopNameSnapshot); put("customerMobileSnapshot", inv.customerMobileSnapshot)
                    put("customerAddressSnapshot", inv.customerAddressSnapshot); put("subtotal", inv.subtotal)
                    put("discountAmount", inv.discountAmount); put("gstEnabled", inv.gstEnabled)
                    put("gstPercent", inv.gstPercent); put("gstAmount", inv.gstAmount)
                    put("additionalCharges", inv.additionalCharges); put("grandTotal", inv.grandTotal)
                    put("paymentStatus", inv.paymentStatus); put("amountPaid", inv.amountPaid)
                    put("remainingBalance", inv.remainingBalance); put("totalKg", inv.totalKg)
                    put("totalBags", inv.totalBags); put("totalPackets", inv.totalPackets)
                    put("totalBoxes", inv.totalBoxes); put("totalPieces", inv.totalPieces)
                    put("sourceOrderId", inv.sourceOrderId ?: JSONObject.NULL)
                    put("status", inv.status); put("createdByEmployeeId", inv.createdByEmployeeId)
                    put("createdByName", inv.createdByName); put("notes", inv.notes)
                    put("syncStatus", inv.syncStatus); put("createdAt", inv.createdAt)
                })
            }
        })

        root.put("invoiceItems", JSONArray().apply {
            db.invoiceDao().getAllItems().forEach { it ->
                put(JSONObject().apply {
                    put("id", it.id); put("invoiceId", it.invoiceId); put("productId", it.productId)
                    put("sellingOptionId", it.sellingOptionId); put("productNameSnapshot", it.productNameSnapshot)
                    put("varietyNameSnapshot", it.varietyNameSnapshot); put("sellingUnitSnapshot", it.sellingUnitSnapshot)
                    put("quantity", it.quantity); put("rate", it.rate); put("lineAmount", it.lineAmount)
                    put("purchaseCostEstimate", it.purchaseCostEstimate); put("baseQuantityDeducted", it.baseQuantityDeducted)
                })
            }
        })

        root.put("orders", JSONArray().apply {
            db.orderDao().getAll().forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id); put("orderNumber", o.orderNumber); put("orderDate", o.orderDate)
                    put("customerId", o.customerId); put("customerNameSnapshot", o.customerNameSnapshot)
                    put("shopNameSnapshot", o.shopNameSnapshot); put("customerMobileSnapshot", o.customerMobileSnapshot)
                    put("status", o.status); put("totalEstimatedAmount", o.totalEstimatedAmount)
                    put("notes", o.notes); put("salesExecutiveId", o.salesExecutiveId)
                    put("salesExecutiveName", o.salesExecutiveName)
                    put("convertedInvoiceId", o.convertedInvoiceId ?: JSONObject.NULL)
                    put("syncStatus", o.syncStatus); put("createdAt", o.createdAt)
                })
            }
        })

        root.put("orderItems", JSONArray().apply {
            db.orderDao().getAllItems().forEach { it ->
                put(JSONObject().apply {
                    put("id", it.id); put("orderId", it.orderId); put("productId", it.productId)
                    put("sellingOptionId", it.sellingOptionId); put("productNameSnapshot", it.productNameSnapshot)
                    put("varietyNameSnapshot", it.varietyNameSnapshot); put("sellingUnitSnapshot", it.sellingUnitSnapshot)
                    put("quantity", it.quantity); put("rate", it.rate); put("lineAmount", it.lineAmount)
                })
            }
        })

        root.put("payments", JSONArray().apply {
            db.paymentLedgerDao().getAllPayments().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("receiptNumber", p.receiptNumber); put("paymentDate", p.paymentDate)
                    put("customerId", p.customerId); put("customerNameSnapshot", p.customerNameSnapshot)
                    put("shopNameSnapshot", p.shopNameSnapshot); put("amountReceived", p.amountReceived)
                    put("allocatedAmount", p.allocatedAmount); put("unallocatedAdvance", p.unallocatedAdvance)
                    put("paymentMethod", p.paymentMethod); put("referenceNumber", p.referenceNumber)
                    put("relatedInvoiceId", p.relatedInvoiceId ?: JSONObject.NULL)
                    put("relatedInvoiceNumber", p.relatedInvoiceNumber); put("notes", p.notes)
                    put("recordedByEmployeeId", p.recordedByEmployeeId); put("recordedByName", p.recordedByName)
                    put("syncStatus", p.syncStatus); put("createdAt", p.createdAt)
                })
            }
        })

        root.put("ledger", JSONArray().apply {
            db.paymentLedgerDao().getAllLedger().forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("customerId", l.customerId); put("entryDate", l.entryDate)
                    put("entryType", l.entryType); put("referenceNumber", l.referenceNumber)
                    put("referenceId", l.referenceId); put("description", l.description)
                    put("debitAmount", l.debitAmount); put("creditAmount", l.creditAmount)
                    put("runningBalance", l.runningBalance); put("createdAt", l.createdAt)
                })
            }
        })

        root.put("suppliers", JSONArray().apply {
            db.supplierPurchaseDao().getAllSuppliers().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("name", s.name); put("businessName", s.businessName)
                    put("mobile", s.mobile); put("address", s.address); put("gstin", s.gstin)
                    put("isActive", s.isActive); put("createdAt", s.createdAt)
                })
            }
        })

        root.put("purchases", JSONArray().apply {
            db.supplierPurchaseDao().getAllPurchases().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("purchaseInvoiceNumber", p.purchaseInvoiceNumber)
                    put("purchaseDate", p.purchaseDate); put("supplierId", p.supplierId)
                    put("supplierNameSnapshot", p.supplierNameSnapshot); put("supplierBusinessSnapshot", p.supplierBusinessSnapshot)
                    put("totalAmount", p.totalAmount); put("amountPaid", p.amountPaid)
                    put("remainingPayable", p.remainingPayable); put("notes", p.notes)
                    put("createdBy", p.createdBy); put("createdAt", p.createdAt)
                })
            }
        })

        root.put("printTemplates", JSONArray().apply {
            db.settingsMetaDao().getAllPrintTemplates().forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("isDefault", t.isDefault)
                    put("invoiceTitle", t.invoiceTitle); put("headerText", t.headerText)
                    put("footerText", t.footerText); put("termsText", t.termsText)
                    put("fontSizeSp", t.fontSizeSp.toDouble()); put("fontFamily", t.fontFamily)
                    put("marginMm", t.marginMm); put("rowSpacingDp", t.rowSpacingDp)
                    put("showBorders", t.showBorders); put("showLogo", t.showLogo)
                    put("showCustomerAddress", t.showCustomerAddress); put("showCustomerMobile", t.showCustomerMobile)
                    put("showPaymentInfo", t.showPaymentInfo); put("showGstDetails", t.showGstDetails)
                    put("showUnitSummary", t.showUnitSummary); put("billsPerPage", t.billsPerPage)
                    put("showCuttingGuides", t.showCuttingGuides)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun saveAutoLocalBackup(): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "vyapardesk_auto_backup.json")
        file.writeText(exportToJson(), Charsets.UTF_8)
        return file
    }

    suspend fun restoreFromJson(jsonText: String): Result<Unit> = runCatching {
        val root = JSONObject(jsonText)
        db.withTransaction {
            db.returnDao().clearReturnItems()
            db.returnDao().clearReturns()
            db.supplierPurchaseDao().clearPurchaseItems()
            db.supplierPurchaseDao().clearPurchases()
            db.supplierPurchaseDao().clearSuppliers()
            db.paymentLedgerDao().clearAllocations()
            db.paymentLedgerDao().clearPayments()
            db.paymentLedgerDao().clearLedger()
            db.invoiceDao().clearItems()
            db.invoiceDao().clearInvoices()
            db.orderDao().clearItems()
            db.orderDao().clearOrders()
            db.productDao().clearStockMovements()
            db.productDao().clearOptions()
            db.productDao().clearProducts()
            db.productDao().clearCategories()
            db.customerDao().clearAll()
            db.employeeDao().clearAll()
            db.settingsMetaDao().clearPrintTemplates()

            root.optJSONObject("business")?.let { b ->
                db.businessDao().upsert(
                    BusinessEntity(
                        id = 1L,
                        name = b.optString("name", "Wholesale Business"),
                        logoUri = b.optString("logoUri", ""),
                        address = b.optString("address", ""),
                        mobile = b.optString("mobile", ""),
                        gstin = b.optString("gstin", ""),
                        defaultGstPercent = b.optDouble("defaultGstPercent", 5.0),
                        invoicePrefix = b.optString("invoicePrefix", "INV"),
                        nextInvoiceNumber = b.optInt("nextInvoiceNumber", 1001),
                        nextOrderNumber = b.optInt("nextOrderNumber", 501),
                        nextReceiptNumber = b.optInt("nextReceiptNumber", 201),
                        nextCreditNoteNumber = b.optInt("nextCreditNoteNumber", 101),
                        defaultLanguage = b.optString("defaultLanguage", "en"),
                        defaultInvoiceLanguage = b.optString("defaultInvoiceLanguage", "en"),
                        allowNegativeStock = b.optBoolean("allowNegativeStock", false),
                        onboardingCompleted = true
                    )
                )
            }

            root.optJSONArray("employees")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.employeeDao().insert(
                        EmployeeEntity(
                            id = o.getLong("id"), name = o.getString("name"), mobile = o.optString("mobile"),
                            role = o.getString("role"), pinHash = o.optString("pinHash"), isActive = o.optBoolean("isActive", true),
                            canAddCustomer = o.optBoolean("canAddCustomer", true), canEditPrices = o.optBoolean("canEditPrices", false),
                            canCreateBill = o.optBoolean("canCreateBill", true), canCreateOrder = o.optBoolean("canCreateOrder", true),
                            canRecordPayment = o.optBoolean("canRecordPayment", true), canManageStock = o.optBoolean("canManageStock", false),
                            canEditInvoiceDate = o.optBoolean("canEditInvoiceDate", false), canCancelInvoice = o.optBoolean("canCancelInvoice", false),
                            canViewReports = o.optBoolean("canViewReports", false), preferredLanguage = o.optString("preferredLanguage", "en"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("customers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.customerDao().insert(
                        CustomerEntity(
                            id = o.getLong("id"), name = o.getString("name"), shopName = o.getString("shopName"),
                            mobile = o.getString("mobile"), address = o.optString("address"),
                            assignedEmployeeId = if (o.isNull("assignedEmployeeId")) null else o.getLong("assignedEmployeeId"),
                            isActive = o.optBoolean("isActive", true), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("categories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.productDao().insertCategory(CategoryEntity(o.getLong("id"), o.getString("name"), o.optBoolean("isDefault")))
                }
            }

            root.optJSONArray("products")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.productDao().insertProduct(
                        ProductEntity(
                            id = o.getLong("id"), name = o.getString("name"), categoryName = o.getString("categoryName"),
                            baseUnit = o.optString("baseUnit", "KG"), trackByBaseUnit = o.optBoolean("trackByBaseUnit", false),
                            basePhysicalStock = o.optDouble("basePhysicalStock", 0.0), baseReservedStock = o.optDouble("baseReservedStock", 0.0),
                            isActive = o.optBoolean("isActive", true), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("sellingOptions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.productDao().insertOption(
                        SellingOptionEntity(
                            id = o.getLong("id"), productId = o.getLong("productId"), varietyName = o.getString("varietyName"),
                            sellingUnit = o.getString("sellingUnit"), packageContentsQty = o.optDouble("packageContentsQty", 1.0),
                            packageContentsUnit = o.optString("packageContentsUnit"), conversionFactorToBase = o.optDouble("conversionFactorToBase", 1.0),
                            sellingPrice = o.getDouble("sellingPrice"), purchasePrice = o.optDouble("purchasePrice", 0.0),
                            physicalStock = o.optDouble("physicalStock", 0.0), reservedStock = o.optDouble("reservedStock", 0.0),
                            minStock = o.optDouble("minStock", 5.0), batchNumber = o.optString("batchNumber"),
                            expiryDate = o.optString("expiryDate"), isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }

            root.optJSONArray("invoices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.invoiceDao().insert(
                        InvoiceEntity(
                            id = o.getLong("id"), invoiceNumber = o.getString("invoiceNumber"), invoiceDate = o.getLong("invoiceDate"),
                            customerId = o.getLong("customerId"), customerNameSnapshot = o.getString("customerNameSnapshot"),
                            shopNameSnapshot = o.getString("shopNameSnapshot"), customerMobileSnapshot = o.optString("customerMobileSnapshot"),
                            customerAddressSnapshot = o.optString("customerAddressSnapshot"), subtotal = o.getDouble("subtotal"),
                            discountAmount = o.optDouble("discountAmount", 0.0), gstEnabled = o.optBoolean("gstEnabled", false),
                            gstPercent = o.optDouble("gstPercent", 0.0), gstAmount = o.optDouble("gstAmount", 0.0),
                            additionalCharges = o.optDouble("additionalCharges", 0.0), grandTotal = o.getDouble("grandTotal"),
                            paymentStatus = o.getString("paymentStatus"), amountPaid = o.optDouble("amountPaid", 0.0),
                            remainingBalance = o.optDouble("remainingBalance", 0.0), totalKg = o.optDouble("totalKg", 0.0),
                            totalBags = o.optDouble("totalBags", 0.0), totalPackets = o.optDouble("totalPackets", 0.0),
                            totalBoxes = o.optDouble("totalBoxes", 0.0), totalPieces = o.optDouble("totalPieces", 0.0),
                            sourceOrderId = if (o.isNull("sourceOrderId")) null else o.getLong("sourceOrderId"),
                            status = o.optString("status", "FINALIZED"), createdByEmployeeId = o.optLong("createdByEmployeeId", 1L),
                            createdByName = o.optString("createdByName"), notes = o.optString("notes"),
                            syncStatus = o.optString("syncStatus", "SYNCED"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("invoiceItems")?.let { arr ->
                val list = mutableListOf<InvoiceItemEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        InvoiceItemEntity(
                            id = o.getLong("id"), invoiceId = o.getLong("invoiceId"), productId = o.getLong("productId"),
                            sellingOptionId = o.getLong("sellingOptionId"), productNameSnapshot = o.getString("productNameSnapshot"),
                            varietyNameSnapshot = o.getString("varietyNameSnapshot"), sellingUnitSnapshot = o.getString("sellingUnitSnapshot"),
                            quantity = o.getDouble("quantity"), rate = o.getDouble("rate"), lineAmount = o.getDouble("lineAmount"),
                            purchaseCostEstimate = o.optDouble("purchaseCostEstimate", 0.0), baseQuantityDeducted = o.optDouble("baseQuantityDeducted", 0.0)
                        )
                    )
                }
                db.invoiceDao().insertItems(list)
            }

            root.optJSONArray("orders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.orderDao().insert(
                        OrderEntity(
                            id = o.getLong("id"), orderNumber = o.getString("orderNumber"), orderDate = o.getLong("orderDate"),
                            customerId = o.getLong("customerId"), customerNameSnapshot = o.getString("customerNameSnapshot"),
                            shopNameSnapshot = o.getString("shopNameSnapshot"), customerMobileSnapshot = o.optString("customerMobileSnapshot"),
                            status = o.optString("status", "PENDING"), totalEstimatedAmount = o.optDouble("totalEstimatedAmount", 0.0),
                            notes = o.optString("notes"), salesExecutiveId = o.optLong("salesExecutiveId", 1L),
                            salesExecutiveName = o.optString("salesExecutiveName"),
                            convertedInvoiceId = if (o.isNull("convertedInvoiceId")) null else o.getLong("convertedInvoiceId"),
                            syncStatus = o.optString("syncStatus", "SYNCED"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("orderItems")?.let { arr ->
                val list = mutableListOf<OrderItemEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        OrderItemEntity(
                            id = o.getLong("id"), orderId = o.getLong("orderId"), productId = o.getLong("productId"),
                            sellingOptionId = o.getLong("sellingOptionId"), productNameSnapshot = o.getString("productNameSnapshot"),
                            varietyNameSnapshot = o.getString("varietyNameSnapshot"), sellingUnitSnapshot = o.getString("sellingUnitSnapshot"),
                            quantity = o.getDouble("quantity"), rate = o.getDouble("rate"), lineAmount = o.getDouble("lineAmount")
                        )
                    )
                }
                db.orderDao().insertItems(list)
            }

            root.optJSONArray("payments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.paymentLedgerDao().insertPayment(
                        PaymentEntity(
                            id = o.getLong("id"), receiptNumber = o.getString("receiptNumber"), paymentDate = o.getLong("paymentDate"),
                            customerId = o.getLong("customerId"), customerNameSnapshot = o.getString("customerNameSnapshot"),
                            shopNameSnapshot = o.getString("shopNameSnapshot"), amountReceived = o.getDouble("amountReceived"),
                            allocatedAmount = o.optDouble("allocatedAmount", 0.0), unallocatedAdvance = o.optDouble("unallocatedAdvance", 0.0),
                            paymentMethod = o.optString("paymentMethod", "CASH"), referenceNumber = o.optString("referenceNumber"),
                            relatedInvoiceId = if (o.isNull("relatedInvoiceId")) null else o.getLong("relatedInvoiceId"),
                            relatedInvoiceNumber = o.optString("relatedInvoiceNumber"), notes = o.optString("notes"),
                            recordedByEmployeeId = o.optLong("recordedByEmployeeId", 1L), recordedByName = o.optString("recordedByName"),
                            syncStatus = o.optString("syncStatus", "SYNCED"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("ledger")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.paymentLedgerDao().insertLedgerEntry(
                        LedgerEntryEntity(
                            id = o.getLong("id"), customerId = o.getLong("customerId"), entryDate = o.getLong("entryDate"),
                            entryType = o.getString("entryType"), referenceNumber = o.getString("referenceNumber"),
                            referenceId = o.getLong("referenceId"), description = o.getString("description"),
                            debitAmount = o.optDouble("debitAmount", 0.0), creditAmount = o.optDouble("creditAmount", 0.0),
                            runningBalance = o.optDouble("runningBalance", 0.0), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("suppliers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.supplierPurchaseDao().insertSupplier(
                        SupplierEntity(
                            id = o.getLong("id"), name = o.getString("name"), businessName = o.getString("businessName"),
                            mobile = o.optString("mobile"), address = o.optString("address"), gstin = o.optString("gstin"),
                            isActive = o.optBoolean("isActive", true), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("purchases")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.supplierPurchaseDao().insertPurchase(
                        PurchaseEntity(
                            id = o.getLong("id"), purchaseInvoiceNumber = o.getString("purchaseInvoiceNumber"),
                            purchaseDate = o.getLong("purchaseDate"), supplierId = o.getLong("supplierId"),
                            supplierNameSnapshot = o.getString("supplierNameSnapshot"), supplierBusinessSnapshot = o.optString("supplierBusinessSnapshot"),
                            totalAmount = o.getDouble("totalAmount"), amountPaid = o.optDouble("amountPaid", 0.0),
                            remainingPayable = o.optDouble("remainingPayable", 0.0), notes = o.optString("notes"),
                            createdBy = o.optString("createdBy"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            root.optJSONArray("printTemplates")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.settingsMetaDao().insertPrintTemplate(
                        PrintTemplateEntity(
                            id = o.getLong("id"), name = o.getString("name"), isDefault = o.optBoolean("isDefault", false),
                            invoiceTitle = o.optString("invoiceTitle", "WHOLESALE BILL"), headerText = o.optString("headerText"),
                            footerText = o.optString("footerText"), termsText = o.optString("termsText"),
                            fontSizeSp = o.optDouble("fontSizeSp", 10.0).toFloat(), fontFamily = o.optString("fontFamily", "Noto Sans Telugu"),
                            marginMm = o.optInt("marginMm", 10), rowSpacingDp = o.optInt("rowSpacingDp", 4),
                            showBorders = o.optBoolean("showBorders", true), showLogo = o.optBoolean("showLogo", true),
                            showCustomerAddress = o.optBoolean("showCustomerAddress", true), showCustomerMobile = o.optBoolean("showCustomerMobile", true),
                            showPaymentInfo = o.optBoolean("showPaymentInfo", true), showGstDetails = o.optBoolean("showGstDetails", true),
                            showUnitSummary = o.optBoolean("showUnitSummary", true), billsPerPage = o.optInt("billsPerPage", 1),
                            showCuttingGuides = o.optBoolean("showCuttingGuides", true)
                        )
                    )
                }
            }
        }
    }
}
