package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.BusinessEntity
import com.example.data.db.PrintTemplateEntity
import com.example.data.model.AppLanguage
import com.example.data.model.FontSizeOption
import com.example.data.model.SyncStatus
import com.example.i18n.LocalStrings
import com.example.ui.components.StatusPill
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AppUiState
import com.example.util.Formatters

@Composable
fun SettingsScreen(
    state: AppUiState,
    onLanguageChange: (AppLanguage) -> Unit,
    onFontSizeChange: (FontSizeOption) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onSaveBusiness: (BusinessEntity) -> Unit,
    onSavePrintTemplate: (PrintTemplateEntity, Boolean) -> Unit,
    onDeletePrintTemplate: (Long) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    onSyncNow: () -> Unit,
    onLoadDemoData: () -> Unit
) {
    val s = LocalStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = General & Business, 1 = Print Settings, 2 = Backup & Sync

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onSaveBusiness(state.business.copy(logoUri = uri.toString()))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportBackup(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(s.businessProfile, fontWeight = FontWeight.Bold) })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(s.printSettings, fontWeight = FontWeight.Bold) })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text(s.backupAndRestore, fontWeight = FontWeight.Bold) })
        }

        when (activeTab) {
            0 -> GeneralSettingsTab(
                state = state,
                onPickLogo = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onLanguageChange = onLanguageChange,
                onFontSizeChange = onFontSizeChange,
                onDarkThemeChange = onDarkThemeChange,
                onSaveBusiness = onSaveBusiness,
                onLoadDemoData = onLoadDemoData
            )
            1 -> PrintTemplateSettingsTab(
                state = state,
                onSaveTemplate = onSavePrintTemplate,
                onDeleteTemplate = onDeletePrintTemplate
            )
            2 -> BackupSyncTab(
                state = state,
                onAutoBackupChange = onAutoBackupChange,
                onExport = { exportLauncher.launch("VyaparDesk_Backup_${System.currentTimeMillis() % 100000}.json") },
                onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                onSyncNow = onSyncNow
            )
        }
    }
}

@Composable
private fun GeneralSettingsTab(
    state: AppUiState,
    onPickLogo: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onFontSizeChange: (FontSizeOption) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onSaveBusiness: (BusinessEntity) -> Unit,
    onLoadDemoData: () -> Unit
) {
    val s = LocalStrings.current
    var bizName by remember(state.business) { mutableStateOf(state.business.name) }
    var mobile by remember(state.business) { mutableStateOf(state.business.mobile) }
    var address by remember(state.business) { mutableStateOf(state.business.address) }
    var gstin by remember(state.business) { mutableStateOf(state.business.gstin) }
    var gstPct by remember(state.business) { mutableStateOf(Formatters.formatQty(state.business.defaultGstPercent)) }
    var prefix by remember(state.business) { mutableStateOf(state.business.invoicePrefix) }
    var allowNeg by remember(state.business) { mutableStateOf(state.business.allowNegativeStock) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Language & Accessibility Card
        item {
            SettingsCard(s.languageSetting) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.prefs.language == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.nativeName, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_lang_${lang.code}")
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(s.textSizeSetting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FontSizeOption.NORMAL to s.textNormal,
                        FontSizeOption.LARGE to s.textLarge,
                        FontSizeOption.EXTRA_LARGE to s.textExtraLarge
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = state.prefs.fontSize == opt,
                            onClick = { onFontSizeChange(opt) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("settings_font_${opt.key}")
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.darkTheme, fontWeight = FontWeight.SemiBold)
                    Switch(checked = state.prefs.darkTheme, onCheckedChange = onDarkThemeChange)
                }
            }
        }

        // Business Profile Card
        item {
            SettingsCard(s.businessProfile) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.business.logoUri.isNotBlank()) {
                        AsyncImage(
                            model = state.business.logoUri,
                            contentDescription = "Business Logo",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    OutlinedButton(onClick = onPickLogo, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = s.selectLogo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.selectLogo)
                    }
                }

                OutlinedTextField(value = bizName, onValueChange = { bizName = it }, label = { Text(s.businessNameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text(s.mobileLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(s.addressLabel) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = gstin, onValueChange = { gstin = it }, label = { Text(s.gstinOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Bill Prefix") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = gstPct, onValueChange = { gstPct = it }, label = { Text("Default GST %") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.allowNegativeStock, modifier = Modifier.weight(1f))
                    Switch(checked = allowNeg, onCheckedChange = { allowNeg = it })
                }
                Button(
                    onClick = {
                        onSaveBusiness(
                            state.business.copy(
                                name = bizName.trim(),
                                mobile = mobile.trim(),
                                address = address.trim(),
                                gstin = gstin.trim(),
                                invoicePrefix = prefix.trim().ifBlank { "INV" },
                                defaultGstPercent = gstPct.toDoubleOrNull() ?: 5.0,
                                allowNegativeStock = allowNeg
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_business_settings")
                ) { Text(s.save) }
            }
        }

        item {
            OutlinedButton(
                onClick = onLoadDemoData,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Dataset, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.loadSampleData)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrintTemplateSettingsTab(
    state: AppUiState,
    onSaveTemplate: (PrintTemplateEntity, Boolean) -> Unit,
    onDeleteTemplate: (Long) -> Unit
) {
    val s = LocalStrings.current
    var selectedTemplateId by remember(state.printTemplates) { mutableLongStateOf(state.defaultTemplate.id) }
    val current = state.printTemplates.firstOrNull { it.id == selectedTemplateId } ?: state.defaultTemplate

    var name by remember(current) { mutableStateOf(current.name) }
    var title by remember(current) { mutableStateOf(current.invoiceTitle) }
    var header by remember(current) { mutableStateOf(current.headerText) }
    var footer by remember(current) { mutableStateOf(current.footerText) }
    var terms by remember(current) { mutableStateOf(current.termsText) }
    var fontSize by remember(current) { mutableFloatStateOf(current.fontSizeSp) }
    var fontFamily by remember(current) { mutableStateOf(current.fontFamily) }
    var marginMm by remember(current) { mutableIntStateOf(current.marginMm) }
    var billsPerPage by remember(current) { mutableIntStateOf(current.billsPerPage) }
    var showBorders by remember(current) { mutableStateOf(current.showBorders) }
    var showCutting by remember(current) { mutableStateOf(current.showCuttingGuides) }
    var showCustAddr by remember(current) { mutableStateOf(current.showCustomerAddress) }
    var showPayInfo by remember(current) { mutableStateOf(current.showPaymentInfo) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Saved Templates selector
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.printTemplates.forEach { tpl ->
                    FilterChip(
                        selected = tpl.id == selectedTemplateId,
                        onClick = { selectedTemplateId = tpl.id },
                        label = { Text(if (tpl.isDefault) "★ ${tpl.name}" else tpl.name) }
                    )
                }
            }
        }

        // Live Print Preview Card
        item {
            SettingsCard("${s.printPreview} (${billsPerPage}x A4)") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (showBorders) 1.5.dp else 0.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                ) {
                    Column(Modifier.padding((marginMm.coerceIn(6, 18)).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.business.name, fontWeight = FontWeight.Bold, fontSize = (fontSize + 3f).sp, color = MaterialTheme.colorScheme.primary)
                        if (header.isNotBlank()) Text(header, fontSize = (fontSize - 1f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                            Text("INV-1001 | 27 Nov", fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp)
                        }
                        Text("Ravi Traders (రవి) • 9848112233", fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
                        if (showCustAddr) Text("Anantapur / అనంతపురం", fontSize = (fontSize - 1f).sp)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ragi Flour / రాగి పిండి (2 Bag)", fontSize = fontSize.sp)
                            Text("₹4,800", fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                        }
                        if (showPayInfo) {
                            Text("Status: POSTPAID | Paid: ₹2,000 | Bal: ₹2,800", fontSize = (fontSize - 0.5f).sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (terms.isNotBlank()) Text(terms, fontSize = (fontSize - 2f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (footer.isNotBlank()) Text(footer, fontSize = (fontSize - 1.5f).sp, color = MaterialTheme.colorScheme.primary)
                        if (billsPerPage > 1) {
                            Text(
                                "Layout: $billsPerPage identical bills on 1 A4 sheet • ${if (showCutting) "Dashed cutting guides ON" else "No guides"} • No Original/Duplicate labels",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Template Customization Controls
        item {
            SettingsCard(s.printSettings) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(s.invoiceTitleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = header, onValueChange = { header = it }, label = { Text(s.headerTextLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = footer, onValueChange = { footer = it }, label = { Text(s.footerTextLabel) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = terms, onValueChange = { terms = it }, label = { Text(s.termsLabel) }, modifier = Modifier.fillMaxWidth())

                Text(s.billsPerPage, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4).forEach { count ->
                        FilterChip(selected = billsPerPage == count, onClick = { billsPerPage = count }, label = { Text("$count / A4") }, modifier = Modifier.weight(1f))
                    }
                }

                Text("Font Family (Unicode Telugu / Hindi / English)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Noto Sans Telugu", "Noto Sans Devanagari", "Plus Jakarta Sans").forEach { ff ->
                        FilterChip(selected = fontFamily == ff, onClick = { fontFamily = ff }, label = { Text(ff) })
                    }
                }

                Text("Font Size: ${fontSize.toInt()} pt")
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 7f..13f)
                Text("Page Margin: $marginMm mm")
                Slider(value = marginMm.toFloat(), onValueChange = { marginMm = it.toInt() }, valueRange = 4f..20f)

                SettingSwitch(s.showBorders, showBorders) { showBorders = it }
                SettingSwitch(s.showCuttingGuides, showCutting) { showCutting = it }
                SettingSwitch("Show Customer Address", showCustAddr) { showCustAddr = it }
                SettingSwitch("Show Payment Info", showPayInfo) { showPayInfo = it }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveTemplate(
                                current.copy(
                                    name = name.trim().ifBlank { "Custom Template" },
                                    invoiceTitle = title.trim(),
                                    headerText = header.trim(),
                                    footerText = footer.trim(),
                                    termsText = terms.trim(),
                                    fontSizeSp = fontSize,
                                    fontFamily = fontFamily,
                                    marginMm = marginMm,
                                    billsPerPage = billsPerPage,
                                    showBorders = showBorders,
                                    showCuttingGuides = showCutting,
                                    showCustomerAddress = showCustAddr,
                                    showPaymentInfo = showPayInfo
                                ),
                                true
                            )
                        },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("${s.save} (Default)") }

                    OutlinedButton(
                        onClick = {
                            onSaveTemplate(
                                current.copy(
                                    id = 0L,
                                    name = "${name.trim()} Copy",
                                    invoiceTitle = title.trim(),
                                    headerText = header.trim(),
                                    footerText = footer.trim(),
                                    termsText = terms.trim(),
                                    fontSizeSp = fontSize,
                                    fontFamily = fontFamily,
                                    marginMm = marginMm,
                                    billsPerPage = billsPerPage,
                                    showBorders = showBorders,
                                    showCuttingGuides = showCutting,
                                    showCustomerAddress = showCustAddr,
                                    showPaymentInfo = showPayInfo
                                ),
                                false
                            )
                        },
                        modifier = Modifier.height(50.dp)
                    ) { Text("Save New") }
                }

                if (state.printTemplates.size > 1 && !current.isDefault) {
                    TextButton(onClick = { onDeleteTemplate(current.id) }) {
                        Text(s.delete, color = DebitRed)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackupSyncTab(
    state: AppUiState,
    onAutoBackupChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onSyncNow: () -> Unit
) {
    val s = LocalStrings.current
    val pendingCount = state.syncQueue.count { it.status == SyncStatus.PENDING.name }
    val syncedCount = state.syncQueue.count { it.status == SyncStatus.SYNCED.name }
    val failedCount = state.syncQueue.count { it.status == SyncStatus.FAILED.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsCard(s.backupAndRestore) {
                SettingSwitch(s.autoLocalBackup, state.prefs.autoBackupEnabled, onAutoBackupChange)
                if (state.prefs.lastBackupTimestamp > 0) {
                    Text(
                        "Last Local Backup: ${Formatters.formatDateTime(state.prefs.lastBackupTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CreditGreen
                    )
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("export_backup_btn")) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.exportBackup)
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("restore_backup_btn")) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.restoreBackup)
                }
            }
        }

        item {
            SettingsCard(s.syncStatusTitle) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatusPill("Synced: $syncedCount", CreditGreen.copy(alpha = 0.14f), CreditGreen)
                    StatusPill("Pending: $pendingCount", WarningAmber.copy(alpha = 0.16f), WarningAmber)
                    StatusPill("Failed: $failedCount", DebitRed.copy(alpha = 0.14f), DebitRed)
                }
                Text(
                    "All business operations run 100% locally on SQLite without internet. Pending changes queue safely for optional multi-device sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onSyncNow, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mark Local Sync Queue Complete")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
