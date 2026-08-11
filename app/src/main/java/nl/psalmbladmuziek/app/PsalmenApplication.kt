package nl.psalmbladmuziek.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Past bij het starten de opgeslagen thema-voorkeur toe (licht/donker/systeem). */
class PsalmenApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    @Volatile
    private var wasBackgrounded = false

    override fun onCreate() {
        super.onCreate()
        AppSettings.applyThemeMode(this)
        registerActivityLifecycleCallbacks(this)
    }

    fun consumeBackgroundedFlag(): Boolean {
        val value = wasBackgrounded
        wasBackgrounded = false
        return value
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            wasBackgrounded = true
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
