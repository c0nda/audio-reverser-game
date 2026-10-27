package com.vorontsov.audioreverser.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TealPrimaryLight = Color(0xFF00696E)
private val TealPrimaryDark = Color(0xFF4FD8DF)
private val SecondaryLight = Color(0xFF4A6365)
private val SecondaryDark = Color(0xFFB1CBCB)

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    secondary = SecondaryLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    secondary = SecondaryDark,
)

@Composable
fun AudioReverserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
