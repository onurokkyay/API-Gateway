# ---- Build stage ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Cache the Gradle distribution and dependencies across builds.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY config ./config
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Layer extraction for image caching ----
FROM eclipse-temurin:25-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /workspace/build/libs/api-gateway.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Runtime ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S gateway && adduser -S gateway -G gateway
USER gateway

COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

EXPOSE 8080

# `java -jar app.jar`, not JarLauncher: the `tools` jarmode above writes a thin jar whose
# manifest names the main class and points Class-Path at lib/, and ships an empty
# spring-boot-loader/ layer. Launching through JarLauncher builds green and then fails to start.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
