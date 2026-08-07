plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tvlauncher"
        minSdk = 28
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Local JVM unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    // 提供真实 org.json 实现，避免单测中用到 mockable-android.jar 里抛异常的桩
    testImplementation("org.json:json:20231013")
}

// 运行单测的 JVM 为 Java 25，而 Mockito 5.7.0 的 Byte Buddy 仅官方支持到 Java 22，
// 通过该 VM 属性启用实验性支持以允许 mock
tasks.withType<Test> {
    systemProperty("net.bytebuddy.experimental", "true")
}
