plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autogcoin" // Sếp đổi lại nếu package của sếp khác
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autogcoin" // Sếp đổi lại nếu package của sếp khác
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Thư viện giao diện cơ bản của Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Thư viện mạng & JSON (Dùng cho API tạo Email 10 phút)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Thư viện Coroutines (Dùng cho vòng lặp Auto chạy ngầm không treo App)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
