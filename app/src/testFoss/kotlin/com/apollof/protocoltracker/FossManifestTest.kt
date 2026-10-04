package com.apollof.protocoltracker

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The foss build: exact reminders by USE_EXACT_ALARM, no network permission at all, no proprietary billing code. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FossManifestTest {
    private val requested: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun useExactAlarmPresent() {
        assertTrue("android.permission.USE_EXACT_ALARM" in requested, "USE_EXACT_ALARM missing: $requested")
    }

    @Test
    fun noNetworkPermissions() {
        assertFalse("android.permission.INTERNET" in requested, "INTERNET must never be requested: $requested")
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in requested, "ACCESS_NETWORK_STATE must not be requested: $requested")
    }

    @Test
    fun noBillingClasses() {
        assertFalse("com.android.vending.BILLING" in requested, "BILLING must not be requested: $requested")
        assertFailsWith<ClassNotFoundException> { Class.forName("com.android.billingclient.api.BillingClient") }
    }
}
