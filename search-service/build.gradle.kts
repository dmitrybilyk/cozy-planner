plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.cozy"
version = "0.0.1-SNAPSHOT"
description = "search-service"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("org.springframework.kafka:spring-kafka")

    implementation("co.elastic.clients:elasticsearch-java:8.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
