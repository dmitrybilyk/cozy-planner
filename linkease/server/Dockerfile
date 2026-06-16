# Build context must be the linkease/ root (not server/), since :server is a module in
# linkease's multi-module Gradle build and depends on :composeApp's jvm target + wasmJs
# browser distribution (bundled as this app's static frontend).
FROM gradle:8.5-jdk21 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Uses the project's own wrapper (not the image's bundled gradle) so the pinned Gradle
# version in gradle-wrapper.properties is used. No Android SDK is required here: :server
# only depends on composeApp's jvm target, and Gradle's variant-aware resolution skips
# androidTarget entirely for that dependency.
RUN ./gradlew :server:bootJar -Plinkease.wasmDist=production -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /home/gradle/src/server/build/libs/*-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-XX:+UseSerialGC -Xms128m -Xmx192m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
