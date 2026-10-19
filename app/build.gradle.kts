import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.autolog.app"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "ru.autolog.app"
        minSdk = 26
        targetSdk = 35

        val appVersionName = "0.7.8"
        versionCode = 69
        versionName = appVersionName
        resValue("string", "app_name", "Autolog $appVersionName")
        resValue("string", "app_version", appVersionName)
        buildConfigField("String", "APP_VERSION", "\"$appVersionName\"")
        buildConfigField("String", "AUTHOR", "\"СпустяРуковаРекордс\"")
        buildConfigField("String", "AUTHOR_ID", "\"srr-autolog-2026\"")
        buildConfigField("String", "AUTHOR_MARK", "\"© СпустяРуковаРекордс\"")
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"dyatlovvv-crypto\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"autolog\"")
        // Overridden per buildType: Espresso pick on map for instrumented tests.
        buildConfigField("boolean", "SHOW_MAP_TEST_PICK", "false")

        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val googleWebClientId = localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Same keystore as release: can update a store/release install without uninstall.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "SHOW_MAP_TEST_PICK", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "SHOW_MAP_TEST_PICK", "false")
        }
        /**
         * UI-test target: release signing (no wipe / no uninstall of user app),
         * minify off so Compose semantics stay stable. Ship store builds via [release].
         */
        create("releaseTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "SHOW_MAP_TEST_PICK", "true")
        }
    }

    // Instrument androidTest against release-signed APK, not debug.
    testBuildType = "releaseTest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    testOptions {
        animationsDisabled = false
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register("verifyAndInstall") {
    group = "verification"
    description =
        "UI tests on releaseTest, then install store release APK (same signing, no wipe)"
    dependsOn("connectedReleaseTestAndroidTest", "installRelease")
    tasks.findByName("installRelease")?.mustRunAfter("connectedReleaseTestAndroidTest")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.google.api-client:google-api-client-android:2.6.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("com.google.truth:truth:1.4.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Compose UI tests run on releaseTest (see testBuildType), not debug.
    add("releaseTestImplementation", "androidx.compose.ui:ui-test-manifest")
    add("releaseTestImplementation", "androidx.compose.ui:ui-tooling")
}
