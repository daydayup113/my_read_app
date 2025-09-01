plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication2"
        minSdk = 33
        targetSdk = 36
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

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(files("C:\\Users\\11739\\Downloads\\epublib-core-3.1.jar"))
    testImplementation(libs.junit)
    // 尝试使用其他可用版本
    implementation("org.slf4j:slf4j-simple:1.7.32")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
