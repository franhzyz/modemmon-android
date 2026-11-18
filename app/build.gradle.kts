plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.franh.modemmon"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "ru.franh.modemmon"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "2.7"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Ключ и пароли лежат в local.properties (вне репозитория).
        // Если их нет — release собирается неподписанным.
        val lp = java.util.Properties()
        val lpFile = rootProject.file("local.properties")
        if (lpFile.exists()) lpFile.inputStream().use { lp.load(it) }
        val ksPath = lp.getProperty("modemmon.keystore")
        val ksPass = lp.getProperty("modemmon.storePassword")
        if (ksPath != null && ksPass != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = lp.getProperty("modemmon.keyAlias") ?: "modemmon"
                keyPassword = lp.getProperty("modemmon.keyPassword") ?: ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingConfigs.names.contains("release")) signingConfigs.getByName("release") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
