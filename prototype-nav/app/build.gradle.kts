plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.apex.navlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.diaztradeinc.trxnavprototype"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.5.1"
        manifestPlaceholders["MAPS_API_KEY"] = (System.getenv("MAPS_API_KEY") ?: "").trim()
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
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.google.android.libraries.navigation:navigation:7.9.0")
    implementation("com.google.android.gms:play-services-maps3d:0.2.2")
    implementation("org.chromium.net:cronet-fallback:119.6045.31")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.filament:filament-android:1.56.0")
    implementation("com.google.android.filament:gltfio-android:1.56.0")
    implementation("com.google.android.filament:filament-utils-android:1.56.0")
    implementation("androidx.core:core-location-altitude:1.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
