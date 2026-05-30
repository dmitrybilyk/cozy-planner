# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /home/gradle/src

# Оптимізація кешування: Спочатку копіюємо лише файли конфігурації Gradle.
# Це дозволить Docker кешувати завантажені залежності (dependencies),
# і не викачувати їх заново, якщо ви змінили лише код програми.
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon || true

# Копіюємо решту вихідного коду
COPY . .

# Збираємо проект
RUN gradle bootJar -x test --no-daemon

# Stage 2: Runtime (Легкий образ для запуску)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Оптимізація розміру: Копіюємо конкретно bootJar (екExecutable JAR),
# ігноруючи plain.jar, який часто важить стільки ж і подвоює розмір образу.
COPY --from=build /home/gradle/src/build/libs/*[!-plain].jar app.jar

# Налаштовуємо оптимальні параметри пам'яті для 1GB інстансу
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms128m -Xmx192m -XX:MaxMetaspaceSize=80m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]