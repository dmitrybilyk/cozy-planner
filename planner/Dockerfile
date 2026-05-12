# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Збираємо проект, пропускаючи тести для швидкості
RUN gradle build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копіюємо тільки зібраний jar. 
# Ми використовуємо конкретне ім'я, яке зазвичай генерує Gradle
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]