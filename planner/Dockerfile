# Етап 1: Збірка проекту
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Збірка jar-файла без запуску тестів для швидкості
RUN gradle build -x test --no-daemon

# Етап 2: Запуск
FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080
RUN mkdir /app
# Копіюємо тільки зібраний jar зі стадії build
COPY --from=build /home/gradle/src/build/libs/*.jar /app/planner-app.jar
ENTRYPOINT ["java", "-jar", "/app/planner-app.jar"]