package com.apollof.protocoltracker.billing

import android.content.Context

/** The last known Pro state, so the app starts with it without waiting on Google Play. */
interface ProCache {
    var isPro: Boolean
}

/** In the "billing" preferences of the Play build; never part of a backup (cloud backup is off, JSON backups skip it). */
class PrefsProCache(context: Context) : ProCache {
    private val prefs = context.applicationContext.getSharedPreferences("billing", Context.MODE_PRIVATE)

    override var isPro: Boolean
        get() = prefs.getBoolean(KEY_PRO, false)
        set(value) = prefs.edit().putBoolean(KEY_PRO, value).apply()

    private companion object {
        const val KEY_PRO = "pro"
    }
}
