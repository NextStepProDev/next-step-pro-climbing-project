# This image PACKAGES a built artifact; it does not build one.
#
# app.jar is produced by the build-and-test job (./gradlew bootJar, right after ./gradlew test)
# and handed here as a workflow artifact. This image used to run `./gradlew bootJar -x test`
# itself, which meant the jar on production was a SECOND compilation that no test had ever run —
# the suite passed on one file and a different one shipped. Now the tested file is the shipped
# file.
#
# It was also compiled once per platform, and the arm64 pass ran under QEMU emulation: ~12 of the
# docker job's 15 minutes spent emulating a CPU to produce identical JVM bytecode. (The layer
# cache could not help either — `COPY src src` sat above the build, so every commit invalidated
# it and Gradle re-fetched every dependency from Maven Central, twice.)
#
# Building locally: `./gradlew clean bootJar && cp build/libs/*.jar app.jar` BEFORE
# `docker build .`. The `clean` is not optional — build/libs keeps a jar for every version ever
# built on that machine (plus a -plain.jar wherever the `jar` task has run), so the glob is
# ambiguous exactly the way the old `COPY --from=build …/*.jar` was. CI never saw this: it
# checks out a fresh tree, which is why the packaging step there asserts the count instead.
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app

# ⚠️ Must stay ABOVE the addgroup/chown block below: that is what makes the jar owned by `app`,
# the user this container runs as.
COPY app.jar app.jar

# This used to read `docker` — a profile no application-docker.yml has ever defined, and the
# comment beside it named that file as if it existed. docker-compose.prod.yml sets no profile of
# its own, so this ENV is what production actually runs on: the image has been starting on a
# profile with no configuration behind it, and application-prod.yml has never been loaded.
#
# The damage was small, which is why nothing surfaced it: everything in application-prod.yml is
# either the framework default already (show-sql: false) or driven by env vars compose does set
# (MAIL_AUTH, MAIL_STARTTLS). The live delta is log level — org.springframework.security and
# org.hibernate ran at INFO instead of WARN, so production has been paying for chatty logs on a
# box where disk and rotation are budgeted.
#
# Nothing in the code branches on this profile: `dev` is the only one anyone reads
# (SecurityConfig, DevAuthController, DevDataSeeder), so `prod` changes no code path.
ENV SPRING_PROFILES_ACTIVE=prod

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