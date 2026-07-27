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
RUN apk add --no-cache curl
WORKDIR /app

# Kopiujemy gotowy JAR (z obsługą wersji 2.1.0-SNAPSHOT)
COPY --from=build /app/build/libs/*.jar app.jar

# Ustawiamy profil 'docker' dla application-docker.yml
ENV SPRING_PROFILES_ACTIVE=docker

# Uruchamiaj jako nieuprzywilejowany użytkownik (pinned uid 1001 → deterministyczny
# chown wolumenu). /app/uploads jest pre-tworzony i nadany userowi: ŚWIEŻY named volume
# dziedziczy tę własność, więc nowe wdrożenia działają bez ręcznego chown. ISTNIEJĄCY
# (rootowy) wolumen wymaga jednorazowo, przed startem tego obrazu:
#   docker run --rm -v uploads_data_prod:/u alpine chown -R 1001:1001 /u
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app \
    && mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080
# Memory tuning for a ~1GB RAM / 400m-limited container (see CLAUDE.md "Wydajność"):
# - MaxRAMPercentage=55 -> ~220MB heap, leaving room for metaspace+threads+direct so
#   heap+non-heap stays under the 400m mem_limit (75% risked OOM-kill: 300MB heap + ~120MB
#   non-heap > 400MB). Smaller committed heap also cuts swap usage on the tight box.
# - MaxMetaspaceSize caps non-heap growth. ExitOnOutOfMemoryError -> clean restart (compose
#   restart: unless-stopped) instead of a wedged JVM. GC left to ergonomics (SerialGC on <2GB).
# - TieredStopAtLevel=1 -> C1-only JIT: skips the C2 optimizing compiler. Slightly faster
#   warmup and a bit less RAM (no C2 compiler threads / C2 code cache) — same memory-first
#   trade as the heap caps above, on a box where CPU is idle and RAM is the bottleneck. The
#   only cost is lower peak throughput of hot paths, irrelevant at this load and a one-flag
#   revert if traffic ever grows. (CDS was evaluated and dropped: ~4% local gain wasn't worth
#   the build-time no-DB training run breaking silently on future Spring upgrades.)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=55.0", "-XX:MaxMetaspaceSize=128m", "-XX:+ExitOnOutOfMemoryError", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]