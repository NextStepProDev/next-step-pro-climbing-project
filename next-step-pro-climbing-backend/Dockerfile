# Etap 1: Budowanie (Build Stage)
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
ARG APP_VERSION=dev

# 1. Kopiujemy wrapper i konfigurację Gradle
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle gradle.properties ./

# 2. Kopiujemy kod źródłowy
COPY src src

# 3. Budujemy aplikację
RUN chmod +x gradlew
RUN APP_VERSION=$APP_VERSION ./gradlew bootJar -x test

# Etap 2: Uruchamianie (Runtime Stage)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Kopiujemy gotowy JAR (z obsługą wersji 2.1.0-SNAPSHOT)
COPY --from=build /app/build/libs/*.jar app.jar

# Ustawiamy profil 'docker' dla application-docker.yml
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]