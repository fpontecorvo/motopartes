# Stage 1: Build
FROM eclipse-temurin:23-jdk AS build

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/
COPY shared/ shared/
COPY api/ api/

# Create empty desktop dir so Gradle doesn't fail (desktop is excluded from Docker build)
RUN mkdir -p desktop && touch desktop/build.gradle.kts

RUN chmod +x gradlew && ./gradlew :api:installDist --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:23-jre

WORKDIR /app
COPY --from=build /app/api/build/install/api/ ./

# SQLite data directory
RUN mkdir -p /data
ENV MOTOPARTES_DATA_DIR=/data

EXPOSE 8080

ENTRYPOINT ["./bin/api"]
