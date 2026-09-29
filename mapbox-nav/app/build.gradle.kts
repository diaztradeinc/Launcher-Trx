plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.apex.mapboxlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.diaztradeinc.trxmapboxlab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "mapbox_public_token", (System.getenv("MAPBOX_PUBLIC_TOKEN") ?: "").trim())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs {
        create("permanent") {
            System.getenv("APEX_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("APEX_KEYSTORE_PASSWORD")
            keyAlias = "trxapex"
            keyPassword = System.getenv("APEX_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") {
            if (System.getenv("APEX_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("permanent")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.mapbox.navigationcore:android-ndk27:3.31.1")
    implementation("com.mapbox.navigationcore:ui-maps-ndk27:3.31.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
