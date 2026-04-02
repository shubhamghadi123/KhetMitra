import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
}

android {
    namespace = "com.example.khetmitra"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    defaultConfig {
        applicationId = "com.example.khetmitra"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_ALIGNED_16KB=ON")
            }
        }

        packaging {
            jniLibs {
                useLegacyPackaging = false
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Mapbox Keys
        val mapboxToken = localProperties.getProperty("MAPBOX_PUBLIC_TOKEN") ?: ""
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxToken\"")
        resValue("string", "mapbox_access_token", mapboxToken)

        // Supabase Keys
        val supabaseUrl = localProperties.getProperty("SUPABASE_URL") ?: ""
        val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Agromonitoring Keys
        val agroKey = localProperties.getProperty("AGRO_API_KEY") ?: ""
        buildConfigField("String", "AGRO_API_KEY", "\"$agroKey\"")
        buildConfigField("String", "AGRO_BASE_URL", "\"https://api.agromonitoring.com/agro/1.0/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.0.12077973"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("com.mapbox.maps:android-ndk27:11.18.1")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.4.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.4.0")
    implementation("io.ktor:ktor-client-android:2.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.5.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    kapt("com.github.bumptech.glide:compiler:5.0.5")
    implementation("androidx.core:core-splashscreen:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}