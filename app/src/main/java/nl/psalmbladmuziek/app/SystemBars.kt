package nl.psalmbladmuziek.app

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Suppress("DEPRECATION")
fun Activity.configureReadableSystemBars(
    navigationBarColor: Int = ContextCompat.getColor(this, R.color.app_bg)
) {
    val toolbarColor = ContextCompat.getColor(this, R.color.psalm_toolbar)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    applyLegacyBarColors(toolbarColor, navigationBarColor)
    val lightNavBars = ColorUtils.calculateLuminance(navigationBarColor) > 0.5
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = lightNavBars
    }
    applyBarContrastPolicy()
}

@Suppress("DEPRECATION")
private fun Activity.applyLegacyBarColors(statusBarColor: Int, navigationBarColor: Int) {
    window.statusBarColor = statusBarColor
    window.navigationBarColor = navigationBarColor
}

@Suppress("DEPRECATION")
private fun Activity.applyBarContrastPolicy() {
    if (Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = true
        window.isNavigationBarContrastEnforced = false
    }
}

/** Laat de statusbalk-achtergrond even hoog worden als de systeem-statusbalk (edge-to-edge). */
fun Activity.bindStatusBarBackground(root: View, statusBarBackground: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        statusBarBackground.layoutParams = statusBarBackground.layoutParams.apply {
            height = statusBars.top
        }
        insets
    }
}

fun Context.dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()