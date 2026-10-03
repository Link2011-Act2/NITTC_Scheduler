plugins { id("com.android.application") version "9.1.1" }
android {
    namespace = "jp.linkserver.nittcsc.qrbenchmark"
    compileSdk = 36
    defaultConfig {
        applicationId = "jp.linkserver.nittcsc.qrbenchmark"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].kotlin.directories += "../out/app-sources"
    sourceSets["main"].assets.directories += "../out/assets"
}
dependencies {
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("io.github.zxing-cpp:android:3.1.1")
}
