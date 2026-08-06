package nl.psalmbladmuziek.app

import android.app.Activity
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

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