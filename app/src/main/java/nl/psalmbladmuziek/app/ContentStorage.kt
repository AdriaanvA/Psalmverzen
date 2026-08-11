package nl.psalmbladmuziek.app

import android.content.Context

object ContentStorage {
    fun readBundledAsset(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }
}