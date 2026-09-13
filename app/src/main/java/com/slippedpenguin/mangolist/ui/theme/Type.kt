package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.slippedpenguin.mangolist.R

/*
 * v1.8 typography — the identity layer of the redesign.
 *
 * - Display/headline slots: **Bebas Neue** (condensed poster caps, the
 *   "Big Manga Energy" voice — was faked with ExtraBold Roboto since v1).
 * - Everything else: **Inter** (modern UI grotesque, tightened tracking).
 *
 * Both are downloadable Google Fonts (res/font/*.xml): zero APK bloat,
 * fetched by Play services on first run. The value/constructor fallback
 * chain keeps every style readable while fonts stream in.
 *
 * Bebas is single-weight (400) — ExtraBold on it is a no-op, so display
 * styles use weight 400 and let the face carry the punch.
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val bebasFont = GoogleFont("Bebas Neue")
private val interFont = GoogleFont("Inter")

/**
 * Bebas for display slots. FontFamily(Font(googleFont, ...)) already
 * degrades gracefully to the platform default while the font streams in
 * (and permanently on devices without Play services), so no extra
 * fallback wiring is needed.
 */
private fun bebas(): FontFamily = FontFamily(
    Font(googleFont = bebasFont, fontProvider = provider),
)

private fun inter(weight: FontWeight): FontFamily = FontFamily(
    Font(googleFont = interFont, fontProvider = provider, weight = weight),
)

/*
 * Static (non-composable) mapping used by MaterialTheme. The GoogleFont
 * constructors are plain factories — safe outside composition. Styles not
 * listed inherit Material defaults.
 *
 * Note on letter-spacing: Bebas is naturally condensed; we add a hair of
 * tracking (+0.5sp) so screen titles breathe. Inter gets the standard
 * negative display tracking (-0.5sp on large sizes) for a tighter block.
 */
@Composable
fun mangoTypography(): Typography = Typography(
    // ---- Display: the Bebas voice ------------------------------------------
    displaySmall = TextStyle(
        fontFamily = bebas(),
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = bebas(),
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        lineHeight = 54.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = bebas(),
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = bebas(),
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
    ),

    // ---- Titles: Inter, tight ----------------------------------------------
    titleLarge = TextStyle(
        fontFamily = inter(FontWeight.SemiBold),
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = inter(FontWeight.SemiBold),
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = inter(FontWeight.SemiBold),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ---- Body ---------------------------------------------------------------
    bodyLarge = TextStyle(
        fontFamily = inter(FontWeight.Normal),
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = inter(FontWeight.Normal),
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = inter(FontWeight.Normal),
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),

    // ---- Labels -------------------------------------------------------------
    labelLarge = TextStyle(
        fontFamily = inter(FontWeight.Bold),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    // Tier badge / kicker style — wide-tracked caps (Inter ExtraBold).
    labelMedium = TextStyle(
        fontFamily = inter(FontWeight.ExtraBold),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = inter(FontWeight.Medium),
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

