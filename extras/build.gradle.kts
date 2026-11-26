import java.util.Properties

// Kolahal Extras: a small second app that holds what Google Play keeps out of Kolahal's Play build (the screen
// lock's accessibility service, the call log, the contacts) and hands it to Kolahal only. It has no internet
// access. Signed as the app is in each build type, so the two always carry the same key: Extras answers only an
// app signed with its own key (ExtrasProvider), and Kolahal trusts only an Extras signed with its own.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release key, as app/build.gradle.kts reads it: keystore.properties (git-ignored) or the environment.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").map { name ->
    keystoreProps.getProperty(name)
        ?: System.getenv("KOLAHAL_RELEASE_" + name.replace(Regex("([A-Z])"), "_$1").uppercase())?.takeIf { it.isNotEmpty() }
}.takeIf { values -> values.all { it != null } }?.map { it!! }

android {
    namespace = "com.mtwabpin.kolahal.extras"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mtwabpin.kolahal.extras"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // The contract, one file shared with the app.
    sourceSets.getByName("main").kotlin.directories.add("contract")

    signingConfigs {
        releaseKey?.let { (file, storePass, alias, keyPass) ->
            create("release") {
                storeFile = rootProject.file(file)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // As the app: without keystore.properties, the debug key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
