plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.apex.navlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.diaztradeinc.trxnavprototype"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
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
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.filament:filament-android:1.56.0")
    implementation("com.google.android.filament:gltfio-android:1.56.0")
    implementation("com.google.android.filament:filament-utils-android:1.56.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
