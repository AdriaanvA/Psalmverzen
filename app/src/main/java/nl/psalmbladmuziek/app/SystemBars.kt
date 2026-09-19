package nl.psalmbladmuziek.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge zonder verouderde window.statusBarColor/navigationBarColor: de statusbalk blijft
 * transparant zodat de toolbarkleur uit de layout er doorheen zichtbaar is.
 */
fun ComponentActivity.configureReadableSystemBars() {
    val navigationBarColor = ContextCompat.getColor(this, R.color.app_bg)
    val lightNavBars = ColorUtils.calculateLuminance(navigationBarColor) > 0.5
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = if (lightNavBars) {
            SystemBarStyle.light(navigationBarColor, navigationBarColor)
        } else {
            SystemBarStyle.dark(navigationBarColor)
        }
    )
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

/** Eenvoudige informatiedialoog (titel + tekst + Sluiten); één bron voor de 'over'-popups. */
fun Context.showInfoDialog(title: CharSequence, message: CharSequence) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Sluiten", null)
        .show()
}
