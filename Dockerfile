# ── Stage 1: Build ──────────────────────────────────────────────────────────
# Use the official Gradle image — no wrapper JAR needed, works out of the box
FROM gradle:8.13-jdk21 AS builder

WORKDIR /app

# Copy build config first (better layer caching for dependencies)
COPY build.gradle .
COPY settings.gradle .

# Download dependencies — cached unless build files change
RUN gradle dependencies --no-daemon

# Copy source and build the fat JAR
COPY src src/
RUN gradle bootJar --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
