plugins {
    alias(libs.plugins.android.application)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("legacybtc.electrumSmoke", providers.gradleProperty("electrumSmoke").getOrElse("false"))
}

android {
    namespace = "com.example.legacybtc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = if (providers.gradleProperty("isolatedWalletTests").orNull == "true")
            "com.example.legacybtc.verification" else "com.example.legacybtc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "REGTEST", "false")

        testInstrumentationRunner = "com.example.legacybtc.IsolatedWalletTestRunner"
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
        create("regtestDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".regtest"
            buildConfigField("boolean", "REGTEST", "true")
            matchingFallbacks += listOf("debug")
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

    // Keys, wallet serialization, local transaction signing and header consensus checks.
    implementation(libs.bitcoinj)
    // Encodes public receiving addresses as QR codes; it never receives private keys.
    implementation(libs.zxing)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
