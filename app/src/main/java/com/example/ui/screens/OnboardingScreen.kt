package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.FontSizeOption
import com.example.i18n.LocalStrings

@Composable
fun OnboardingScreen(
    selectedLanguage: AppLanguage,
    selectedFontSize: FontSizeOption,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFontSizeSelected: (FontSizeOption) -> Unit,
    onComplete: (businessName: String, mobile: String, address: String, loadSample: Boolean) -> Unit
) {
    val s = LocalStrings.current
    var businessName by remember { mutableStateOf("Sri Venkateswara Wholesale Traders") }
    var mobile by remember { mutableStateOf("9848012345") }
    var address by remember { mutableStateOf("Main Market Road, Anantapur") }
    var loadSample by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Warm Emerald Heritage Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = s.appName,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        s.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    s.tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        s.offlineReady,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Language Picker (English, Telugu, Hindi)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Translate, contentDescription = s.chooseLanguage, tint = MaterialTheme.colorScheme.primary)
                    Text(s.chooseLanguage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val active = lang == selectedLanguage
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onLanguageSelected(lang) }
                                .testTag("onboarding_lang_${lang.code}"),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    lang.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (active) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Accessibility Text Size Picker
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.textSizeSetting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FontSizeOption.NORMAL to s.textNormal,
                        FontSizeOption.LARGE to s.textLarge,
                        FontSizeOption.EXTRA_LARGE to s.textExtraLarge
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = selectedFontSize == opt,
                            onClick = { onFontSizeSelected(opt) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        // Business Profile Details
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.businessProfile, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text(s.businessNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_business_name")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text(s.mobileLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(s.addressLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .clickable { loadSample = !loadSample }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = loadSample, onCheckedChange = { loadSample = it })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.loadSampleData, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(s.loadSampleDataDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Button(
            onClick = { onComplete(businessName, mobile, address, loadSample) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("start_business_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(s.startBusiness, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
