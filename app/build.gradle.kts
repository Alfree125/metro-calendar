import java.util.Properties

plugins {
    id("com.android.application")
}

// Chiave di firma release: vive fuori dal repository. Il percorso del file .properties
// (storeFile, storePassword, keyAlias, keyPassword) si puo' cambiare con -PfirmaRelease=...
// o con la variabile d'ambiente CALENDARIO_ANNO_FIRMA.
val fileFirma = file(
    providers.gradleProperty("firmaRelease")
        .orElse(providers.environmentVariable("CALENDARIO_ANNO_FIRMA"))
        .getOrElse(System.getProperty("user.home") + "/android-keys/calendario-anno.properties")
)
val firma = Properties().apply {
    if (fileFirma.exists()) fileFirma.inputStream().use { load(it) }
}

android {
    namespace = "com.calendarioanno.app"
    compileSdk = 37 // Android 17

    defaultConfig {
        applicationId = "com.calendarioanno.app"
        minSdk = 23 // Android 6.0 (java.time arriva col desugaring qui sotto)
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (fileFirma.exists()) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: run {
                logger.warn("Chiave release non trovata ($fileFirma): APK firmato con la chiave debug")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        // java.time, Map.merge, List.sort... anche su Android 6 e 7
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.19.1")
}
