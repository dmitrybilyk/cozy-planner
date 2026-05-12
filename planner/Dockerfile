FROM gradle:8.14-jdk21
EXPOSE 8080
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon
ENTRYPOINT ["java", "-jar", "/home/gradle/src/build/libs/*.jar"]
