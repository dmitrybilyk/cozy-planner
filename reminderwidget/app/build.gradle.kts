import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.reminderwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reminderwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    val ksFile = rootProject.file("keystore.properties")
    val ksProps: Properties? = if (ksFile.exists()) Properties().apply { ksFile.inputStream().use(::load) } else null

    signingConfigs {
        if (ksProps != null) {
            create("release") {
                storeFile     = rootProject.file(ksProps.getProperty("storeFile") ?: "")
                storePassword = ksProps.getProperty("storePassword")
                keyAlias      = ksProps.getProperty("keyAlias")
                keyPassword   = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (ksProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "Remindly-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
