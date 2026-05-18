# Stage 1: Build native image with GraalVM
FROM ghcr.io/graalvm/graalvm-community:21 AS build

ENV GRADLE_VERSION=8.5
RUN microdnf install -y unzip wget findutils && \
    wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip && \
    unzip /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip && \
    microdnf clean all

WORKDIR /app
COPY . .
RUN gradle nativeCompile -x test --no-daemon

# Stage 2: Distroless runtime with glibc
FROM gcr.io/distroless/cc-debian12 AS runtime
WORKDIR /app
COPY --from=build /app/build/native/nativeCompile/planner .
EXPOSE 8080
ENTRYPOINT ["./planner"]
