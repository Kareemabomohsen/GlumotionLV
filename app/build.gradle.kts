plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.glumotion"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.glumotion"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }

    // 📁 Every section has its own folder of XML pages (res-screens/food/layout, res-screens/workout/layout ...).
    // Android joins all the folders together when it builds the app.
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res-screens/common",
                "src/main/res-screens/start",
                "src/main/res-screens/home",
                "src/main/res-screens/glucose",
                "src/main/res-screens/devices",
                "src/main/res-screens/food",
                "src/main/res-screens/recipes",
                "src/main/res-screens/workout",
                "src/main/res-screens/health",
                "src/main/res-screens/medications",
                "src/main/res-screens/profile",
                "src/main/res-screens/chat",
                "src/main/res-screens/coach"
            )
        }
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}