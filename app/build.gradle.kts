import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// Signing keys, from keystore.properties (git-ignored) or the environment (GitHub Actions):
// - the release key signs the APKs on GitHub: storeFile, storePassword, keyAlias, keyPassword, or
//   KOLAHAL_RELEASE_STORE_FILE, _STORE_PASSWORD, _KEY_ALIAS, _KEY_PASSWORD;
// - the upload key signs the bundle for Google Play: uploadStoreFile… or KOLAHAL_UPLOAD_….
// tools/make-keys.sh makes both.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** File, store password, alias and key password of a key, or null when any is missing. */
fun signingKey(prefix: String, env: String): List<String>? {
    val values = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").map { name ->
        val prop = if (prefix.isEmpty()) name else prefix + name.replaceFirstChar(Char::uppercase)
        val variable = "KOLAHAL_${env}_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()
        keystoreProps.getProperty(prop) ?: System.getenv(variable)?.takeIf { it.isNotEmpty() }
    }
    return if (values.all { it != null }) values.map { it!! } else null
}
val releaseKey = signingKey("", "RELEASE")
val uploadKey = signingKey("upload", "UPLOAD")

android {
    namespace = "com.mtwabpin.kolahal"
    compileSdk = 37
    // The NDK strips the native libraries (Lua, QuickJS) and keeps their symbols for Play: the
    // workflows install this version, as runner images don't always carry it.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.mtwabpin.kolahal"
        minSdk = 28
        targetSdk = 37
        versionCode = 18
        versionName = "0.4.9-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The accessibility service (double-tap / `lock`), the call log and READ_CONTACTS are in the
        // manifest; the play build leaves them out.
        buildConfigField("boolean", "LOCK_SERVICE", "true")
        buildConfigField("boolean", "CALL_LOG", "true")
        buildConfigField("boolean", "CONTACTS", "true")
buildConfigField("boolean", "SELF_UPDATE", "true")
    }

    signingConfigs {
        releaseKey?.let { (file, storePass, alias, keyPass) ->
            create("release") {
                storeFile = rootProject.file(file)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
        uploadKey?.let { (file, storePass, alias, keyPass) ->
            create("upload") {
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
            // Without keystore.properties, sign with the debug key so the optimized build is still installable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The native libraries' symbol tables go into the bundle (BUNDLE-METADATA), so Play
            // Console can read native crashes; the APKs carry the stripped libraries.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
        debug {
            applicationIdSuffix = ".debug"
            // -Pcoverage: JaCoCo for the test runs (tools/coverage.pl reads the reports). Off by
            // default: it instruments the debug build and slows it down.
            val coverage = providers.gradleProperty("coverage").isPresent
            enableUnitTestCoverage = coverage
            enableAndroidTestCoverage = coverage
        }
        // Google Play (`bundlePlay`): the release build without the accessibility service and the call
        // log (src/play removes them), which Play's policy rules out or wants a declaration and a video
        // for. No `lock` or double-tap lock, no calls in the log. No READ_CONTACTS either (Play's
        // Contacts policy): call, sms and contacts use Android's contact picker. Notification access stays: `notifs
        // enable` says what it reads and asks first, as Play's User Data policy wants. The bundle is signed with the
        // upload key; without one it stays unsigned, never debug-signed (Play refuses those).
        // `-PplayApk` signs with the release key instead, as the full build: the Play build's APK on
        // GitHub (`assemblePlay -PplayApk`), for phones where Play Protect blocks the full one.
        // The bundle must stay on the upload key: one run can't build it and the release-signed APK.
        if (providers.gradleProperty("playApk").isPresent && gradle.startParameter.taskNames.any { "bundle" in it.lowercase() }) {
            throw GradleException("-PplayApk signs the Play build with the release key: build the bundle in a run of its own")
        }
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            if (!providers.gradleProperty("playApk").isPresent) signingConfig = signingConfigs.findByName("upload")
            buildConfigField("boolean", "LOCK_SERVICE", "false")
            buildConfigField("boolean", "CALL_LOG", "false")
            buildConfigField("boolean", "CONTACTS", "false")
buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    // The same startup profile as release.
    sourceSets.getByName("play").baselineProfiles.directories.add("src/release/generated/baselineProfiles")

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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.multiprocess)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Scripting engines: native Lua 5.4 (LuaJava) and QuickJS (quickjs-kt)
    implementation(libs.luajava)
    implementation(libs.luajava.lua54)
    runtimeOnly(variantOf(libs.luajava.android) { classifier("lua54"); artifactType("aar") })
    implementation(libs.quickjs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI tests of the home screen (the test activity comes with ui-test-manifest, debug only).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
