plugins {
    id("com.android.application")
}

android {
    namespace = "btm.m.os4.iosnotificationcenter"
    compileSdk = 37

    defaultConfig {
        applicationId = "btm.m.os4.iosnotificationcenter"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
