# syntax=docker/dockerfile:1
# Build with the same verified Gradle version as the repository wrapper.
FROM gradle:9.8.1-jdk21@sha256:5ec5f726f14851a11a1bac6e6a5720e41062c33357053e7717eb0d189a6f1db9 AS build
USER root
WORKDIR /app
COPY . .
RUN gradle --no-daemon --max-workers=4 :server:installDist

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/server/build/install/server/ ./
USER app
ENV PORT=9090 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
EXPOSE 9090
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q -O- "http://localhost:${PORT}/health" || exit 1
ENTRYPOINT ["/app/bin/server"]
