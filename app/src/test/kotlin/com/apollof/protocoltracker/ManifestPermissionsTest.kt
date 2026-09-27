package com.apollof.protocoltracker

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app stays offline in both flavors: no manifest, including one merged in from a dependency, may request
 * `INTERNET`. `ACCESS_NETWORK_STATE` (added by WorkManager) is allowed: it only reads the connection state.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPermissionsTest {
    private val requested: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun readsTheMergedManifest() {
        // WAKE_LOCK comes only from WorkManager's manifest, so seeing it proves the merged manifest is read.
        assertTrue("android.permission.WAKE_LOCK" in requested, "merged manifest not read: $requested")
    }

    @Test
    fun noInternetPermission() {
        assertFalse("android.permission.INTERNET" in requested, "INTERNET must never be requested: $requested")
    }
}
