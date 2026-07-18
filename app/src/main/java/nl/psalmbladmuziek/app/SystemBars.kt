package nl.psalmbladmuziek.app

import android.app.Activity
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

fun Activity.configureReadableSystemBars(
    navigationBarColor: Int = ContextCompat.getColor(this, R.color.app_bg)
) {
    val toolbarColor = ContextCompat.getColor(this, R.color.psalm_toolbar)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = toolbarColor
    window.navigationBarColor = navigationBarColor
    val lightNavBars = ColorUtils.calculateLuminance(navigationBarColor) > 0.5
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = lightNavBars
    }
    if (Build.VERSION.SDK_INT >= 29) {
        window.isStatusBarContrastEnforced = true
        window.isNavigationBarContrastEnforced = false
    }
}