package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomerEntity
import com.example.data.db.InvoiceEntity
import com.example.data.model.DateFilter
import com.example.i18n.LocalStrings
import com.example.util.Formatters

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    tag: String = "search_field"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = placeholder) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DateFilterBar(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    modifier: Modifier = Modifier,
    includeAllTime: Boolean = true
) {
    val s = LocalStrings.current
    val filters = buildList {
        add(DateFilter.TODAY)
        add(DateFilter.YESTERDAY)
        add(DateFilter.THIS_WEEK)
        add(DateFilter.THIS_MONTH)
        add(DateFilter.LAST_MONTH)
        if (includeAllTime) add(DateFilter.ALL_TIME)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(s.dateFilterLabel(f), fontWeight = if (selected == f) FontWeight.Bold else FontWeight.Medium) },
                modifier = Modifier.heightIn(min = 42.dp)
            )
        }
    }
}

@Composable
fun QuickActionTile(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = ""
) {
    Surface(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(contentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = message,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PrintInvoiceDialog(
    invoice: InvoiceEntity,
    onDismiss: () -> Unit,
    onPrint: (copiesPerPage: Int, shareOnly: Boolean) -> Unit
) {
    val s = LocalStrings.current
    var selectedCopies by remember { mutableIntStateOf(4) } // Showcase 4-up A4 easily selectable

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${s.print} — ${invoice.invoiceNumber}", fontWeight = FontWeight.Bold)
                Text(
                    "${invoice.shopNameSnapshot} • ${Formatters.formatRupee(invoice.grandTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    s.noCopyLabelsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                listOf(
                    1 to s.printFullA4,
                    2 to s.printTwoCopies,
                    4 to s.printFourCopies
                ).forEach { (copies, label) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCopies = copies }
                            .testTag("print_copies_$copies"),
                        color = if (selectedCopies == copies) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = if (selectedCopies == copies) 2.dp else 1.dp,
                            color = if (selectedCopies == copies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedCopies == copies, onClick = { selectedCopies = copies })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onPrint(selectedCopies, true); onDismiss() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("share_invoice_pdf_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = s.sharePdf, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.sharePdf)
                }
                Button(
                    onClick = { onPrint(selectedCopies, false); onDismiss() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("print_invoice_direct_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = s.print, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.print)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        }
    )
}

/**
 * Simple Add/Edit Customer Dialog strictly containing ONLY the 4 required fields:
 * 1. Customer Name (Required)
 * 2. Shop Name (Required)
 * 3. Mobile Number (Required)
 * 4. Address (Optional)
 */
@Composable
fun AddEditCustomerDialog(
    existing: CustomerEntity? = null,
    existingCustomers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, shopName: String, mobile: String, address: String) -> Unit
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var shopName by remember { mutableStateOf(existing?.shopName ?: "") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }

    val potentialDuplicate = remember(name, shopName, mobile, existingCustomers) {
        val m = mobile.trim()
        val n = name.trim().lowercase()
        val sh = shopName.trim().lowercase()
        existingCustomers.firstOrNull { c ->
            c.id != (existing?.id ?: -1L) && (
                (m.length >= 6 && c.mobile.trim() == m) ||
                (n.isNotEmpty() && sh.isNotEmpty() && c.name.trim().lowercase() == n && c.shopName.trim().lowercase() == sh)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.addCustomer else "${s.edit} ${s.navCustomers}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("${s.customerName} *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("${s.shopName} *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_shop_input")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("${s.mobileNumber} *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_mobile_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(s.address) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("customer_address_input")
                )
                if (potentialDuplicate != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${s.duplicateCustomerWarn}\n(${potentialDuplicate.shopName} - ${potentialDuplicate.mobile})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && shopName.isNotBlank() && mobile.isNotBlank()) {
                        onSave(name, shopName, mobile, address)
                    }
                },
                enabled = name.isNotBlank() && shopName.isNotBlank() && mobile.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_customer_button")
            ) {
                Text(s.saveCustomer)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}
