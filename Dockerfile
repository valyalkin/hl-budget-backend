# syntax=docker/dockerfile:1
# hl-budget-backend for the homelab (research R-015, homelab service-contract.md).
# Base images pinned by version and multi-arch index digest.
FROM eclipse-temurin:21.0.12.1_1-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /src
# Wrapper and build scripts first, so dependency resolution is cached apart from source changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
# The gates (tests, lint, coverage) run in CI; the image build only packages.
RUN ./gradlew --no-daemon bootJar -x check

FROM eclipse-temurin:21.0.12.1_1-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
WORKDIR /app
COPY --from=build /src/build/libs/app.jar /app/app.jar
# The pod runs as 10001 with a read-only root filesystem; /tmp is the only writable dir.
ENV JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp
USER 10001:10001
EXPOSE 8080
# Exec form, so the JVM is PID 1 and receives SIGTERM (graceful shutdown, 20 s per phase).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
