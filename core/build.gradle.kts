plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.soojeongmin.storydrive.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        aarMetadata {
            minCompileSdk = 34
        }
    }

    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.pleos.vehicle)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
