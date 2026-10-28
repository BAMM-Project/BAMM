plugins {
    id("com.android.application")
}

android {
    namespace = "bj.bamm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "bj.bamm.app"
        minSdk = 26          // Android 8 : UssdResponseCallback / sendUssdRequest
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        // URL de l'API communautaire (10.0.2.2 = machine hôte depuis l'émulateur)
        buildConfigField("String", "DEFAULT_API_URL", "\"http://10.0.2.2:8000\"")
    }

    // Play Protect (protection renforcée contre la fraude) bloque l'installation hors
    // Play Store des APK qui déclarent un service d'accessibilité. L'édition « standard »,
    // distribuée sur le site, s'en passe ; la « complete » l'ajoute (installation par ADB).
    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            buildConfigField("boolean", "USSD_ACCESSIBILITY", "false")
        }
        create("complete") {
            dimension = "edition"
            versionNameSuffix = "-complete"
            buildConfigField("boolean", "USSD_ACCESSIBILITY", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // Téléphones réels uniquement (ARM) : divise la taille de l'APK par ~2
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"   // le modèle est mappé en mémoire depuis les assets
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
