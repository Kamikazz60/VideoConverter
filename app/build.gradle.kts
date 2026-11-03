import java.util.Properties

plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose"); kotlin("plugin.serialization") }

val keystoreProps = Properties().apply {
    rootProject.file("keys/keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "fr.videoconverter"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.videoconverter"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            // Même clé en debug et en release : les mises à jour s'installent par-dessus l'ancienne version.
            getByName("debug") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        jniLibs { useLegacyPackaging = true }   // bibliothèques FFmpeg extraites sur disque
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/*.kotlin_module")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(files("libs/ffmpeg-kit-full-gpl-6.0.4-arm64-v8a-x86_64.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
