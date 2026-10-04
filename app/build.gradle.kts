import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = keystoreProps.getProperty(key) ?: System.getenv(key)

android {
    namespace = "com.apollof.protocoltracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.apollof.protocoltracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        // The tag without the "v" (CI checks it): "0.5.0" for v0.5.0, "0.5.1-beta.1" for a pre-release.
        versionName = "0.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // GitHub, F-Droid and Obtainium: every preset, no proprietary dependency, always fully unlocked.
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "MONETIZATION", "\"none\"")
        }
        // Google Play: reviewed preset allowlist, neutral labels, no USE_EXACT_ALARM, optional Pro unlock.
        create("play") {
            dimension = "distribution"
            /**
             * Owner decision pending: -Pplay.idSuffix=.play gives Play its own app id. With a suffix both builds can be
             * installed side by side, but moving between them needs backup and restore. Without one (the default) both
             * ship as com.apollof.protocoltracker with the same key, so a user can move between channels by update.
             */
            providers.gradleProperty("play.idSuffix").orNull?.takeIf { it.isNotBlank() }?.let { applicationIdSuffix = it }
            // none | paid_listing | unlock (-Pplay.monetization=...): unlock sells a one-time Pro unlock in the app.
            val monetization = providers.gradleProperty("play.monetization").orNull ?: "unlock"
            require(monetization in setOf("none", "paid_listing", "unlock")) { "play.monetization must be none, paid_listing or unlock, not $monetization" }
            buildConfigField("String", "MONETIZATION", "\"$monetization\"")
            // Launcher label (-Pplay.label=...); see app/src/play/res/values/strings.xml.
            resValue("string", "play_label", providers.gradleProperty("play.label").orNull ?: "ProtocolTracker")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("PT_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("PT_KEYSTORE_PASSWORD")
                keyAlias = signingValue("PT_KEY_ALIAS")
                keyPassword = signingValue("PT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into FileDescriptor internals on JDK 17+.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            // One JVM per test class: Robolectric apps in one JVM share background work and settings files.
            it.forkEvery = 1
            // Local runs may use several test JVMs (-Ptest.forks=N); CI leaves the default of one.
            providers.gradleProperty("test.forks").orNull?.toIntOrNull()?.let { n -> it.maxParallelForks = n }
            // Design-review screenshots: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest' -Pscreenshots.dir=<folder>
            providers.gradleProperty("screenshots.dir").orNull?.let { dir -> it.systemProperty("screenshots.dir", dir) }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Play only: the Pro unlock. GPL-3.0 section 7 permission in LICENSE-EXCEPTION; foss has no proprietary dependency.
    "playImplementation"(libs.play.billing)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    // On-device smoke test (./gradlew connectedDebugAndroidTest with an emulator running).
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.kotlin.test)
}
