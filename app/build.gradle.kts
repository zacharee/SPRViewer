plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "tk.zwander.sprviewer"
        minSdk = 23
        targetSdk = 34
        versionCode = 14
        versionName = versionCode.toString()
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    viewBinding {
        enable = true
    }
    namespace = "tk.zwander.sprviewer"
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.material)
    implementation(libs.documentfile)
    implementation(libs.lifecycle.viewmodel.ktx)

    implementation(libs.progresscircula)
    implementation(libs.picasso)
    implementation(libs.apk.parser)
    implementation(libs.photoview)
    implementation(libs.pngj)
    implementation(libs.numberprogressbar)
    implementation(libs.indicatorfastscroll)
    implementation(libs.balloon)
    implementation(libs.colorpicker)
    implementation(libs.hiddenapibypass)

    implementation(libs.bugsnag.android)
}
