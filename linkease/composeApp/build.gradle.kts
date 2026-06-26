import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }

        wasmJsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }

        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.glance:glance-appwidget:1.1.0")
            implementation("androidx.datastore:datastore-preferences:1.1.1")
            implementation("androidx.work:work-runtime-ktx:2.9.1")
            implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-auth-ktx")
            implementation("com.google.firebase:firebase-firestore-ktx")
        }
    }
}

// Copy APK to both ../dist/ and the planner's static resources after every debug build.
// The planner serves it at /linkease.apk when deployed to Oracle Cloud.
tasks.register<Copy>("copyApk") {
    val src = layout.buildDirectory.file("outputs/apk/debug/composeApp-debug.apk")
    from(src)
    // Primary: dist/ folder next to linkease/
    into(rootProject.projectDir.resolve("../dist"))
    rename { "linkease.apk" }
    doFirst {
        rootProject.projectDir.resolve("../dist").mkdirs()
        // Also copy to planner static resources for Oracle Cloud serving
        val plannerStatic = rootProject.projectDir.resolve("../planner/src/main/resources/static")
        if (plannerStatic.exists()) {
            src.get().asFile.copyTo(plannerStatic.resolve("linkease.apk"), overwrite = true)
        }
    }
}
afterEvaluate {
    tasks.named("assembleDebug") { finalizedBy("copyApk") }
}

android {
    namespace = "com.linkease"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.linkease"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
