plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "ai.adapt.inference"
    compileSdk = 35
    defaultConfig { minSdk = 28; ndk { abiFilters += "arm64-v8a" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.pytorch:executorch-android:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
