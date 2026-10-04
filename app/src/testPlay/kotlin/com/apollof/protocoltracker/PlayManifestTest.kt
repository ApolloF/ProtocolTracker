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

/** The Play build: no USE_EXACT_ALARM (Play allows it only for alarm and calendar apps); INTERNET is allowed. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayManifestTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val requested: List<String> by lazy {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun useExactAlarmAbsent() {
        assertFalse("android.permission.USE_EXACT_ALARM" in requested, "USE_EXACT_ALARM must not be requested: $requested")
    }

    @Test
    fun scheduleExactAlarmOnEveryVersion() {
        assertTrue("android.permission.SCHEDULE_EXACT_ALARM" in requested, "SCHEDULE_EXACT_ALARM missing: $requested")
    }

    @Test
    fun appNameResolves() {
        assertTrue(context.getString(R.string.app_name).isNotBlank())
    }
}
