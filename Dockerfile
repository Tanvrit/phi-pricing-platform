# syntax=docker/dockerfile:1.7

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# ─────────────────────────────────────────────────────────────────────────────
FROM gradle:8.11.1-jdk21 AS build

WORKDIR /app

# Cache Gradle dependencies separately from source.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
COPY shared/build.gradle.kts ./shared/
COPY server/build.gradle.kts ./server/
COPY desktop/build.gradle.kts ./desktop/
COPY buyonline/build.gradle.kts ./buyonline/

# Pre-fetch deps (best-effort; if it fails the next step still works).
RUN gradle --no-daemon :server:dependencies > /dev/null 2>&1 || true

# Copy actual source.
COPY shared/ ./shared/
COPY server/ ./server/

# Build the shadow / fat jar.
RUN gradle --no-daemon :server:build -x test

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# Use slim JRE image; run as non-root.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Drop privileges
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copy the built jar (shadow output if shadow plugin lands; otherwise the
# regular fat jar produced by tasks.jar).
COPY --from=build /app/server/build/libs/*.jar /app/server.jar

# Application config: every secret comes from env var. Do not bake .conf with creds.
ENV PORT=9090 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 9090

# Healthcheck — readiness probe equivalent.
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q -O- "http://localhost:${PORT}/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/server.jar"]
