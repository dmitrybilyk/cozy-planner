plugins {
	java
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.4.0"
}

group = "com.cozy"
version = "0.0.1-SNAPSHOT"
description = "planner"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	// Lombok
	compileOnly("org.projectlombok:lombok:1.18.30")
	annotationProcessor("org.projectlombok:lombok:1.18.30")

	// Для тестів (якщо будеш використовувати Lombok там)
	testCompileOnly("org.projectlombok:lombok:1.18.30")
	testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

	implementation("org.openapitools:jackson-databind-nullable:0.2.6")
	implementation("jakarta.validation:jakarta.validation-api")
	// Якщо використовуєш Spring Boot 3
	implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")
//	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
//	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.postgresql:r2dbc-postgresql")

	// Flyway
	implementation("org.flywaydb:flyway-core")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
//	testImplementation("org.springframework.boot:spring-boot-starter-test")
//	testImplementation("io.projectreactor:reactor-test")
//	testImplementation("org.springframework.kafka:spring-kafka-test")
//	testImplementation("org.springframework.security:spring-security-test")
//	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set("$projectDir/src/main/resources/api/planner-api.yaml")
	outputDir.set("$buildDir/generated-sources")
	apiPackage.set("com.planner.api")
	modelPackage.set("com.planner.model")
	configOptions.set(mapOf(
		"interfaceOnly" to "true", // Тільки інтерфейси, без логіки
		"useSpringBoot3" to "true",
		"reactive" to "true",
		"useTags" to "true",
		"skipDefaultInterface" to "true",
		"enumPropertyNaming" to "original"
	))
}

// Додай згенеровані файли до source sets, щоб IDEA їх бачила
sourceSets {
	main {
		java {
			srcDir("$buildDir/generated-sources/src/main/java")
		}
	}
}

// Автоматично генерувати код перед компіляцією
tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}

tasks.openApiGenerate {
	outputs.upToDateWhen { false }
}

tasks.withType<Test> {
	useJUnitPlatform()
}
