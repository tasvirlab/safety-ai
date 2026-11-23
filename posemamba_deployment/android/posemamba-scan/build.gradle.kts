plugins { id("com.android.library") }

val ortHeaders = providers.gradleProperty("SCAN_ORT_INCLUDE").orNull
    ?: error("Set SCAN_ORT_INCLUDE to the absolute directory containing ORT 1.22.1 headers")

android {
    namespace = "com.azizbek.safetyai.ondevice"
    compileSdk = 37
    ndkVersion = providers.gradleProperty("SCAN_NDK_VERSION").get()
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DSCAN_BUILD_ORT=ON", "-DORT_INCLUDE_DIR=$ortHeaders", "-DANDROID_STL=c++_shared")
                targets += "posemamba_scan_ort"
            }
        }
    }
    externalNativeBuild { cmake { path = file("../../CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    api("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
