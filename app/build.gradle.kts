import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release-ondertekening: leest een (niet in versiebeheer opgenomen) keystore.properties.
// Bestaat dat bestand niet, dan blijft de release-build onondertekend (voor CI/lokaal testen).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "nl.psalmbladmuziek.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.psalmbladmuziek.app"
        minSdk = 32
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val sFile = keystoreProperties.getProperty("storeFile")
            val sPass = keystoreProperties.getProperty("storePassword")
            val kAlias = keystoreProperties.getProperty("keyAlias")
            val kPass = keystoreProperties.getProperty("keyPassword")

            if (sFile != null && sPass != null && kAlias != null && kPass != null) {
                storeFile = rootProject.file(sFile)
                storePassword = sPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    androidResources {
        // De app is Nederlandstalig; beperk de meegeleverde bibliotheek-vertalingen
        // (AppCompat/Material bundelen ~80 talen) tot Nederlands + Engelse fallback.
        // Dit verkleint resources.arsc aanzienlijk.
        localeFilters += listOf("en", "nl")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // View-gebaseerde bibliotheken, beheerd via gradle/libs.versions.toml
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
}