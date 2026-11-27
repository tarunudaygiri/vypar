package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.EmployeeEntity
import com.example.data.model.EmployeeRole
import com.example.data.model.InvoiceStatus
import com.example.i18n.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun EmployeesScreen(
    state: AppUiState,
    onSaveEmployee: (EmployeeEntity) -> Unit,
    onSwitchEmployee: (EmployeeEntity, String) -> Boolean,
    onAssignCustomer: (Long, Long?) -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Employees, 1 = Activity Log
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEmp by remember { mutableStateOf<EmployeeEntity?>(null) }
    var switchTarget by remember { mutableStateOf<EmployeeEntity?>(null) }
    var assignTarget by remember { mutableStateOf<EmployeeEntity?>(null) }

    val isOwner = state.activeEmployee.role == EmployeeRole.OWNER.name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (isOwner && activeTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Badge, contentDescription = s.addEmployee) },
                    text = { Text(s.addEmployee) },
                    modifier = Modifier.testTag("add_employee_fab")
                )
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("${s.navEmployees} (${state.employees.size})", fontWeight = FontWeight.Bold) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(s.activityLog, fontWeight = FontWeight.Bold) })
            }

            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.employees, key = { it.id }) { emp ->
                        val roleEnum = runCatching { EmployeeRole.valueOf(emp.role) }.getOrDefault(EmployeeRole.SALES_EXECUTIVE)
                        val isActive = emp.id == state.activeEmployee.id

                        // Separate metrics: Orders Collected vs Invoiced Sales vs Payments Collected!
                        val ordersCount = state.orders.count { it.salesExecutiveId == emp.id }
                        val invoicedSum = state.invoices.filter { it.createdByEmployeeId == emp.id && it.status != InvoiceStatus.CANCELLED.name }.sumOf { it.grandTotal }
                        val paymentsSum = state.payments.filter { it.recordedByEmployeeId == emp.id }.sumOf { it.amountReceived }
                        val assignedCusts = state.customers.count { it.assignedEmployeeId == emp.id }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(emp.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (isActive) StatusPill(s.activeUser, CreditGreen.copy(alpha = 0.14f), CreditGreen)
                                        }
                                        Text("${s.roleText(roleEnum)}${if (emp.mobile.isNotBlank()) " • ${emp.mobile}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!isActive) {
                                        FilledTonalButton(onClick = {
                                            if (emp.pinHash.isBlank()) {
                                                onSwitchEmployee(emp, "")
                                            } else {
                                                switchTarget = emp
                                            }
                                        }) { Text(s.switchUser) }
                                    }
                                }

                                // Distinct performance metrics row
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    EmpMetric(s.ordersCollected, "$ordersCount")
                                    EmpMetric(s.invoicedSales, Formatters.formatRupee(invoicedSum))
                                    EmpMetric(s.paymentsCollected, Formatters.formatRupee(paymentsSum))
                                }

                                if (isOwner) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (roleEnum == EmployeeRole.SALES_EXECUTIVE) {
                                            TextButton(onClick = { assignTarget = emp }) {
                                                Text("${s.assignCustomers} ($assignedCusts)")
                                            }
                                        }
                                        TextButton(onClick = { editingEmp = emp }) { Text("${s.edit} / ${s.permissions}") }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.activityLogs.isEmpty()) {
                        item { EmptyStateCard(Icons.Default.History, s.noRecordsYet) }
                    } else {
                        items(state.activityLogs, key = { it.id }) { log ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(log.employeeName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(log.action, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                    Text(Formatters.formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingEmp != null) {
        AddEditEmployeeDialog(
            existing = editingEmp,
            onDismiss = { showAddDialog = false; editingEmp = null },
            onSave = { emp ->
                onSaveEmployee(emp)
                showAddDialog = false; editingEmp = null
            }
        )
    }

    switchTarget?.let { target ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text("${s.switchUser}: ${target.name}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Enter PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { if (onSwitchEmployee(target, pin)) switchTarget = null }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Text(s.cancel) } }
        )
    }

    assignTarget?.let { exec ->
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text("${s.assignCustomers} — ${exec.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.customers.forEach { cust ->
                        val assigned = cust.assignedEmployeeId == exec.id
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onAssignCustomer(cust.id, if (assigned) null else exec.id)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = assigned, onCheckedChange = { onAssignCustomer(cust.id, if (it) exec.id else null) })
                            Column {
                                Text(cust.shopName, fontWeight = FontWeight.SemiBold)
                                Text(cust.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { assignTarget = null }) { Text(s.close) } }
        )
    }
}

@Composable
private fun AddEditEmployeeDialog(
    existing: EmployeeEntity?,
    onDismiss: () -> Unit,
    onSave: (EmployeeEntity) -> Unit
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var role by remember { mutableStateOf(existing?.let { runCatching { EmployeeRole.valueOf(it.role) }.getOrDefault(EmployeeRole.SALES_EXECUTIVE) } ?: EmployeeRole.SALES_EXECUTIVE) }
    var pin by remember { mutableStateOf("") }
    var canAddCust by remember { mutableStateOf(existing?.canAddCustomer ?: true) }
    var canEditPrice by remember { mutableStateOf(existing?.canEditPrices ?: false) }
    var canBill by remember { mutableStateOf(existing?.canCreateBill ?: true) }
    var canOrder by remember { mutableStateOf(existing?.canCreateOrder ?: true) }
    var canPay by remember { mutableStateOf(existing?.canRecordPayment ?: true) }
    var canStock by remember { mutableStateOf(existing?.canManageStock ?: false) }
    var canReports by remember { mutableStateOf(existing?.canViewReports ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.addEmployee else "${s.edit} ${existing.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Employee Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text(s.mobileNumber) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text(s.pinOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text(s.roleLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmployeeRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = {
                                role = r
                                when (r) {
                                    EmployeeRole.OWNER -> { canAddCust = true; canEditPrice = true; canBill = true; canOrder = true; canPay = true; canStock = true; canReports = true }
                                    EmployeeRole.MANAGER -> { canAddCust = true; canEditPrice = true; canBill = true; canOrder = true; canPay = true; canStock = true; canReports = true }
                                    EmployeeRole.BILLING_STAFF -> { canAddCust = true; canEditPrice = false; canBill = true; canOrder = true; canPay = true; canStock = false; canReports = false }
                                    EmployeeRole.SALES_EXECUTIVE -> { canAddCust = true; canEditPrice = false; canBill = false; canOrder = true; canPay = true; canStock = false; canReports = false }
                                    EmployeeRole.INVENTORY_STAFF -> { canAddCust = false; canEditPrice = false; canBill = false; canOrder = false; canPay = false; canStock = true; canReports = false }
                                }
                            },
                            label = { Text(s.roleText(r)) }
                        )
                    }
                }

                Text(s.permissions, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                PermToggle("Add Customers", canAddCust) { canAddCust = it }
                PermToggle("Create Bills", canBill) { canBill = it }
                PermToggle("Create Orders", canOrder) { canOrder = it }
                PermToggle("Record Payments", canPay) { canPay = it }
                PermToggle("Manage Stock & Purchases", canStock) { canStock = it }
                PermToggle("Edit Selling Prices", canEditPrice) { canEditPrice = it }
                PermToggle("View Business Reports", canReports) { canReports = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newHash = if (pin.isNotBlank()) Formatters.hashPin(pin) else existing?.pinHash ?: ""
                    onSave(
                        (existing ?: EmployeeEntity(name = "", role = role.name)).copy(
                            name = name.trim(),
                            mobile = mobile.trim(),
                            role = role.name,
                            pinHash = newHash,
                            canAddCustomer = canAddCust,
                            canEditPrices = canEditPrice,
                            canCreateBill = canBill,
                            canCreateOrder = canOrder,
                            canRecordPayment = canPay,
                            canManageStock = canStock,
                            canEditInvoiceDate = role == EmployeeRole.OWNER || role == EmployeeRole.MANAGER,
                            canCancelInvoice = role == EmployeeRole.OWNER,
                            canViewReports = canReports
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun PermToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EmpMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
