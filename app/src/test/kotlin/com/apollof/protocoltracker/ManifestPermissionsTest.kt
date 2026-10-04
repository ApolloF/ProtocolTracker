package com.apollof.protocoltracker

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs for both flavours. The foss build asks for no network access: no manifest, including one merged in from a
 * dependency, may request `INTERNET` (FossManifestTest also rules out `ACCESS_NETWORK_STATE`). The play build may get
 * `INTERNET` from Play Billing (PlayManifestTest). Unit tests read the debug merged manifest; release has no dependencies of its own
 * (no `releaseImplementation`).
 * A plain [Application] keeps the app's startup work (database, WorkManager) out of this manifest-only test.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ManifestPermissionsTest {
    private val requested: List<String> by lazy {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun readsTheMergedManifest() {
        // WAKE_LOCK comes only from WorkManager's manifest, so seeing it proves the merged manifest is read.
        assertTrue("android.permission.WAKE_LOCK" in requested, "merged manifest not read: $requested")
    }

    @Test
    fun noInternetPermissionInFoss() {
        if (BuildConfig.FLAVOR == "foss") assertFalse("android.permission.INTERNET" in requested, "INTERNET must never be requested: $requested")
    }
}
