plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aintradutor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aliceeditor.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // só celulares de 64 bits (arm64): é o que quase todo celular atual usa
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        // só os textos das bibliotecas em inglês e português
        resourceConfigurations += listOf("en", "pt")
    }

    // Chave fixa: todo build sai com a mesma assinatura, então uma versão nova instala por cima da anterior.
    signingConfigs {
        create("release") {
            storeFile = file("alice-release.jks")
            storeType = "pkcs12"
            storePassword = "aliceeditor"
            keyAlias = "alice"
            keyPassword = "aliceeditor"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        // o workflow antigo gera o APK "debug": também sai encolhido e com a mesma assinatura
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
