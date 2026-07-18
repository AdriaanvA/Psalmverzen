package nl.psalmbladmuziek.app

import android.app.Application

/** Past bij het starten de opgeslagen thema-voorkeur toe (licht/donker/systeem). */
class PsalmenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyThemeMode(this)
    }
}
