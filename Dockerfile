# -------- Stage 1: Build the application --------
FROM gradle:8.7-jdk21 AS builder

# Set working directory
WORKDIR /app

# Copy only essential files for caching efficiency
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle

# Download dependencies
RUN gradle build -x test --no-daemon || return 0

# Copy source code last (reduces cache invalidation)
COPY . .

# Build the JAR file
RUN gradle bootJar --no-daemon

# -------- Stage 2: Create minimal runtime image --------
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the built JAR from builder
COPY --from=builder /app/build/libs/gateway-*.jar app.jar

# Expose port (Spring Boot default)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]