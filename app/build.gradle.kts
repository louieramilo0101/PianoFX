plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.pianofx"
    compileSdk = 34
    defaultConfig { applicationId = "com.pianofx"; minSdk = 23; targetSdk = 34; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
