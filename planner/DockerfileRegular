FROM gradle:8.5-jdk21 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-XX:+UseSerialGC -Xms128m -Xmx192m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]