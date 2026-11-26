plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aintradutor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aintradutor.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // só as arquiteturas de celular (tira x86 e x86_64, usadas em emuladores)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        // só os textos das bibliotecas em inglês e português
        resourceConfigurations += listOf("en", "pt")
    }

    // O workflow gera o APK "debug"; aqui ele também é encolhido (R8 + recursos não usados)
    buildTypes {
        getByName("debug") {
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
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
