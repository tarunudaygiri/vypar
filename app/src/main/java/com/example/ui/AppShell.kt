package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.AppScreen
import com.example.i18n.LocalStrings
import com.example.ui.components.AddEditCustomerDialog
import com.example.ui.screens.*
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private data class NavEntry(val screen: AppScreen, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val s = LocalStrings.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickAddCustomer by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    BackHandler(enabled = drawerState.isOpen || state.currentScreen != AppScreen.DASHBOARD) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            viewModel.navigateBack()
        }
    }

    // Complete 13-item Navigation list — STRICTLY NO Expenses module anywhere!
    val allNavItems = listOf(
        NavEntry(AppScreen.DASHBOARD, s.navDashboard, Icons.Default.Dashboard),
        NavEntry(AppScreen.NEW_BILL, s.navNewBill, Icons.Default.ReceiptLong),
        NavEntry(AppScreen.ORDERS, s.navOrders, Icons.Default.Assignment),
        NavEntry(AppScreen.CUSTOMERS, s.navCustomers, Icons.Default.People),
        NavEntry(AppScreen.PRODUCTS, s.navProducts, Icons.Default.Inventory2),
        NavEntry(AppScreen.PAYMENTS, s.navPayments, Icons.Default.Payments),
        NavEntry(AppScreen.INVENTORY, s.navInventory, Icons.Default.Warehouse),
        NavEntry(AppScreen.SUPPLIERS, s.navSuppliers, Icons.Default.LocalShipping),
        NavEntry(AppScreen.PURCHASES, s.navPurchases, Icons.Default.ShoppingCart),
        NavEntry(AppScreen.RETURNS, s.navReturns, Icons.Default.AssignmentReturn),
        NavEntry(AppScreen.EMPLOYEES, s.navEmployees, Icons.Default.Badge),
        NavEntry(AppScreen.REPORTS, s.navReports, Icons.Default.Assessment),
        NavEntry(AppScreen.SETTINGS, s.navSettings, Icons.Default.Settings)
    )

    val bottomBarItems = listOf(
        NavEntry(AppScreen.DASHBOARD, s.navDashboard, Icons.Default.Dashboard),
        NavEntry(AppScreen.NEW_BILL, s.navNewBill, Icons.Default.ReceiptLong),
        NavEntry(AppScreen.CUSTOMERS, s.navCustomers, Icons.Default.People),
        NavEntry(AppScreen.PRODUCTS, s.navProducts, Icons.Default.Inventory2),
        NavEntry(AppScreen.ORDERS, s.navOrders, Icons.Default.Assignment)
    )

    val currentTitle = allNavItems.firstOrNull { it.screen == state.currentScreen }?.label ?: s.appName

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(296.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    // Business & Active User Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(state.business.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${s.activeUser}: ${state.activeEmployee.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                s.offlineReady,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    allNavItems.forEach { entry ->
                        NavigationDrawerItem(
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label, fontWeight = if (state.currentScreen == entry.screen) FontWeight.Bold else FontWeight.Medium) },
                            selected = state.currentScreen == entry.screen,
                            onClick = {
                                viewModel.navigateTo(entry.screen)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer_${entry.screen.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 720.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.statusBarsPadding().navigationBarsPadding()
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = s.moreMenu)
                        }
                        Spacer(Modifier.height(8.dp))
                        bottomBarItems.forEach { entry ->
                            NavigationRailItem(
                                selected = state.currentScreen == entry.screen,
                                onClick = { viewModel.navigateTo(entry.screen) },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_btn")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = s.moreMenu)
                                }
                            },
                            title = {
                                Column {
                                    Text(currentTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${state.business.name} • ${state.activeEmployee.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                // 1-tap Language Switch Chip (EN / తె / हि)
                                Surface(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable {
                                            val next = when (state.prefs.language) {
                                                AppLanguage.ENGLISH -> AppLanguage.TELUGU
                                                AppLanguage.TELUGU -> AppLanguage.HINDI
                                                AppLanguage.HINDI -> AppLanguage.ENGLISH
                                            }
                                            viewModel.setLanguage(next)
                                        }
                                        .testTag("topbar_lang_cycle"),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = state.prefs.language.nativeName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                bottomBarItems.forEach { entry ->
                                    NavigationBarItem(
                                        selected = state.currentScreen == entry.screen,
                                        onClick = { viewModel.navigateTo(entry.screen) },
                                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                                        label = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.testTag("bottom_${entry.screen.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (state.currentScreen) {
                            AppScreen.DASHBOARD -> DashboardScreen(
                                state = state,
                                onNavigate = { viewModel.navigateTo(it) },
                                onQuickAddCustomer = { showQuickAddCustomer = true },
                                onPrintInvoice = { inv, copies, share -> viewModel.printInvoice(inv, copies, share) }
                            )
                            AppScreen.CUSTOMERS -> CustomersScreen(
                                state = state,
                                onSaveCustomer = { n, sh, m, a, id -> viewModel.saveCustomer(n, sh, m, a, id) },
                                onPrintInvoice = { inv, copies, share -> viewModel.printInvoice(inv, copies, share) },
                                onPrintReceipt = { pay, share -> viewModel.printPaymentReceipt(pay, share) },
                                onPrintHistory = { cust, filter, share -> viewModel.printCustomerHistory(cust, filter, share) }
                            )
                            AppScreen.PRODUCTS -> ProductsScreen(
                                state = state,
                                onSaveProduct = { prod, onSaved -> viewModel.saveProduct(prod, onSaved) },
                                onSaveSellingOption = { opt, prodName -> viewModel.saveSellingOption(opt, prodName) },
                                onAddCategory = { viewModel.addCategory(it) }
                            )
                            AppScreen.NEW_BILL -> NewBillScreen(
                                state = state,
                                onQuickSaveCustomer = { n, sh, m, a, cb -> viewModel.saveCustomer(n, sh, m, a, 0L, cb) },
                                onCreateInvoice = { c, items, date, disc, extra, gst, gstPct, st, paid, notes, ordId, ok ->
                                    viewModel.createInvoice(c, items, date, disc, extra, gst, gstPct, st, paid, notes, ordId, ok)
                                },
                                onCancelInvoice = { id, reason -> viewModel.cancelInvoice(id, reason) },
                                onPrintInvoice = { inv, copies, share -> viewModel.printInvoice(inv, copies, share) },
                                onClearOrderConversion = { viewModel.clearOrderConversion() }
                            )
                            AppScreen.ORDERS -> OrdersScreen(
                                state = state,
                                onQuickSaveCustomer = { n, sh, m, a, cb -> viewModel.saveCustomer(n, sh, m, a, 0L, cb) },
                                onSaveOrder = { id, c, items, st, notes, ok -> viewModel.saveOrder(id, c, items, st, notes, ok) },
                                onUpdateStatus = { id, st -> viewModel.updateOrderStatus(id, st) },
                                onDuplicateOrder = { viewModel.duplicateOrder(it) },
                                onConvertToBill = { ord, items -> viewModel.startOrderConversion(ord, items) },
                                onPrintOrderSlip = { ord, share -> viewModel.printOrderSlip(ord, share) }
                            )
                            AppScreen.PAYMENTS -> PaymentsScreen(
                                state = state,
                                onRecordPayment = { c, date, amt, method, ref, invIds, notes, ok ->
                                    viewModel.recordPayment(c, date, amt, method, ref, invIds, notes, ok)
                                },
                                onPrintReceipt = { pay, share -> viewModel.printPaymentReceipt(pay, share) }
                            )
                            AppScreen.INVENTORY -> InventoryScreen(
                                state = state,
                                onAdjustStock = { pId, oId, d, t, r, b, e -> viewModel.adjustStock(pId, oId, d, t, r, b, e) }
                            )
                            AppScreen.SUPPLIERS -> SuppliersPurchasesScreen(
                                state = state,
                                initialTab = 0,
                                onSaveSupplier = { viewModel.saveSupplier(it) },
                                onRecordPurchase = { sup, inv, date, items, paid, notes, ok ->
                                    viewModel.recordPurchase(sup, inv, date, items, paid, notes, ok)
                                }
                            )
                            AppScreen.PURCHASES -> SuppliersPurchasesScreen(
                                state = state,
                                initialTab = 1,
                                onSaveSupplier = { viewModel.saveSupplier(it) },
                                onRecordPurchase = { sup, inv, date, items, paid, notes, ok ->
                                    viewModel.recordPurchase(sup, inv, date, items, paid, notes, ok)
                                }
                            )
                            AppScreen.EMPLOYEES -> EmployeesScreen(
                                state = state,
                                onSaveEmployee = { viewModel.saveEmployee(it) },
                                onSwitchEmployee = { emp, pin -> viewModel.switchEmployee(emp, pin) },
                                onAssignCustomer = { cId, eId -> viewModel.assignCustomerToEmployee(cId, eId) }
                            )
                            AppScreen.RETURNS -> ReturnsScreen(
                                state = state,
                                onRecordReturn = { inv, items, settle, reason, ok ->
                                    viewModel.recordReturn(inv, items, settle, reason, ok)
                                }
                            )
                            AppScreen.REPORTS -> ReportsScreen(
                                state = state,
                                onPrintReport = { title, lines, share -> viewModel.printOrShareReport(title, lines, share) },
                                onExportCsv = { name, headers, rows -> viewModel.exportReportCsv(name, headers, rows) }
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                state = state,
                                onLanguageChange = { viewModel.setLanguage(it) },
                                onFontSizeChange = { viewModel.setFontSize(it) },
                                onDarkThemeChange = { viewModel.setDarkTheme(it) },
                                onAutoBackupChange = { enabled ->
                                    scope.launch { viewModel.prefs.setAutoBackup(enabled) }
                                },
                                onSaveBusiness = { viewModel.saveBusiness(it) },
                                onSavePrintTemplate = { tpl, def -> viewModel.savePrintTemplate(tpl, def) },
                                onDeletePrintTemplate = { viewModel.deletePrintTemplate(it) },
                                onExportBackup = { viewModel.exportBackupToUri(it) },
                                onRestoreBackup = { viewModel.restoreBackupFromUri(it) },
                                onSyncNow = { viewModel.triggerManualSync() },
                                onLoadDemoData = { viewModel.loadDemoData() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickAddCustomer) {
        AddEditCustomerDialog(
            existing = null,
            existingCustomers = state.customers,
            onDismiss = { showQuickAddCustomer = false },
            onSave = { n, sh, m, a ->
                viewModel.saveCustomer(n, sh, m, a)
                showQuickAddCustomer = false
            }
        )
    }
}
