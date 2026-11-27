package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val TeluguFontFamily = FontFamily(
    Font(R.font.noto_sans_telugu, FontWeight.Normal),
    Font(R.font.noto_sans_telugu, FontWeight.Medium),
    Font(R.font.noto_sans_telugu, FontWeight.Bold)
)

val HindiFontFamily = FontFamily(
    Font(R.font.noto_sans_devanagari, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari, FontWeight.Bold)
)

fun buildAppTypography(language: AppLanguage, scale: Float): Typography {
    val bodyFamily = when (language) {
        AppLanguage.TELUGU -> TeluguFontFamily
        AppLanguage.HINDI -> HindiFontFamily
        AppLanguage.ENGLISH -> PlusJakartaFontFamily
    }
    val displayFamily = when (language) {
        AppLanguage.TELUGU -> TeluguFontFamily
        AppLanguage.HINDI -> HindiFontFamily
        AppLanguage.ENGLISH -> PlusJakartaFontFamily
    }
    return Typography(
        displaySmall = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = (30f * scale).sp, lineHeight = (38f * scale).sp),
        headlineMedium = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = (24f * scale).sp, lineHeight = (32f * scale).sp),
        headlineSmall = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = (20f * scale).sp, lineHeight = (28f * scale).sp),
        titleLarge = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.SemiBold, fontSize = (18f * scale).sp, lineHeight = (26f * scale).sp),
        titleMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = (16f * scale).sp, lineHeight = (23f * scale).sp),
        titleSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = (14f * scale).sp, lineHeight = (20f * scale).sp),
        bodyLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (16f * scale).sp, lineHeight = (24f * scale).sp),
        bodyMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (14.5f * scale).sp, lineHeight = (21f * scale).sp),
        bodySmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (13f * scale).sp, lineHeight = (19f * scale).sp),
        labelLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = (14.5f * scale).sp, lineHeight = (20f * scale).sp),
        labelMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = (12.5f * scale).sp, lineHeight = (18f * scale).sp),
        labelSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = (11.5f * scale).sp, lineHeight = (16f * scale).sp)
    )
}
