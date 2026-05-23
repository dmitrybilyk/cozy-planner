FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
