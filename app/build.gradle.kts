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
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // stable: the released app. dev: installs next to it and adds features still in development
    // (symptom logging and bloodwork), switched on through BuildConfig.DEV_FEATURES.
    flavorDimensions += "track"
    productFlavors {
        create("stable") {
            dimension = "track"
            buildConfigField("boolean", "DEV_FEATURES", "false")
        }
        create("dev") {
            dimension = "track"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "DEV_FEATURES", "true")
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into FileDescriptor internals on JDK 17+.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            // One JVM per test class: Robolectric apps in one JVM share background work and settings files.
            it.forkEvery = 1
            // Design-review screenshots: ./gradlew :app:testDevDebugUnitTest --tests '*ScreenshotTest' -Pscreenshots.dir=<folder>
            providers.gradleProperty("screenshots.dir").orNull?.let { dir -> it.systemProperty("screenshots.dir", dir) }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// With flavors there is no plain debug variant; these keep `testDebugUnitTest` and `lintDebug` covering both builds.
tasks.register("testDebugUnitTest") { dependsOn("testStableDebugUnitTest", "testDevDebugUnitTest") }
tasks.register("lintDebug") { dependsOn("lintStableDebug", "lintDevDebug") }

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
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
