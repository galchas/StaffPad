plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.staffpad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.staffpad"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

// Exclude legacy support libraries to avoid duplicate classes with AndroidX
configurations.all {
    exclude(group = "com.android.support")
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.constraintlayout.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Room components
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Optional - Kotlin coroutines support for Room
    implementation(libs.room.ktx)

    // Optional - Test helpers
    testImplementation(libs.room.testing)

    // Java language implementation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    // Feature module support
    implementation(libs.navigation.dynamic.features.fragment)

    // Testing Navigation
    androidTestImplementation(libs.navigation.testing)

    implementation(libs.pdfbox.android)
    implementation(libs.photoview)
    implementation(libs.colorpickerview)

    //YOUTUBE PLAYER
    implementation(libs.androidyoutubeplayercore)

    //Piano View
    implementation(libs.pianoview)
}