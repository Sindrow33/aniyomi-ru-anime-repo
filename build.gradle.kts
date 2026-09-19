plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = 31
    sourceSets {
        getByName("main").apply {
            manifest.srcFile("src/ru/myanimelist/AndroidManifest.xml")
        }
    }
    defaultConfig {
        minSdk = 21
        targetSdk = 31
        versionCode = 1
        versionName = "1.0.0"
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
