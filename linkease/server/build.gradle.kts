plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.linkease"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(project(":composeApp"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Bundles the existing wasmJs browser build as this app's static frontend, instead of
// checking generated JS/Wasm into src/main/resources (kept out of version control).
// Defaults to the unoptimized "development" distribution: the production one runs
// Binaryen wasm-opt and takes 8+ minutes per build, which makes local `bootRun` painfully
// slow. Pass -Plinkease.wasmDist=production (used by the Dockerfile) to get the optimized
// build for an actual deployment.
val wasmDistMode = (findProperty("linkease.wasmDist") as String?) ?: "development"
val wasmDistTaskName = if (wasmDistMode == "production") "wasmJsBrowserDistribution" else "wasmJsBrowserDevelopmentExecutableDistribution"
val wasmDistDirName = if (wasmDistMode == "production") "dist/wasmJs/productionExecutable" else "dist/wasmJs/developmentExecutable"

tasks.named<Copy>("processResources") {
    dependsOn(":composeApp:$wasmDistTaskName", ":composeApp:processSkikoRuntimeForKWasm")
    from(project(":composeApp").layout.buildDirectory.dir(wasmDistDirName)) {
        into("static")
    }
    // index.html references skiko.js directly, but neither *Distribution task above copies
    // it — it's produced separately by the Skiko-for-Wasm runtime processing step.
    from(project(":composeApp").layout.buildDirectory.dir("compose/skiko-runtime-processed-wasmjs")) {
        into("static")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
