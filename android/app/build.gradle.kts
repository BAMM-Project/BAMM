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
        versionCode = 1
        versionName = "1.0.0"
        // URL de l'API communautaire (10.0.2.2 = machine hôte depuis l'émulateur)
        buildConfigField("String", "DEFAULT_API_URL", "\"http://10.0.2.2:8000\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
