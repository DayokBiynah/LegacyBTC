plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.legacybtc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.legacybtc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "MAINNET", "false")
        }
        release {
            buildConfigField("boolean", "MAINNET", "true")
            optimization {
                enable = false
            }
        }
        create("mainnetDebug") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "MAINNET", "true")
            matchingFallbacks += listOf("debug")
        }
    }
    buildFeatures { buildConfig = true }
    // I can run the same disposable instrumentation fixtures against either network build.
    testBuildType = providers.gradleProperty("walletTestBuildType").getOrElse("debug")
    sourceSets {
        getByName("release").res.srcDir("src/mainnet/res")
        getByName("mainnetDebug").res.srcDir("src/mainnet/res")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Adds the bitcoinj library to LegacyBTC.
    // bitcoinj provides the Bitcoin wallet, key, address,
    // transaction, and network functionality used by the application.
    implementation(libs.bitcoinj)
    // Encodes public receiving addresses as QR codes; it never receives private keys.
    implementation(libs.zxing)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
