# Stage 1: Build
FROM gradle:8.5-jdk21 AS build

# Копіюємо вміст папки planner
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Збираємо проект
RUN gradle build -x test --no-daemon

# Stage 2: Runtime (Легкий образ для запуску)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копіюємо тільки основний jar, ігноруючи plain.jar
COPY --from=build /home/gradle/src/build/libs/*-SNAPSHOT.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]