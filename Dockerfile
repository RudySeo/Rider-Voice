# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon

COPY src/main ./src/main

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon && \
    jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" && \
    test -n "$jar_path" && \
    cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:25-jre-noble AS runtime

LABEL org.opencontainers.image.title="Rider Voice API" \
      org.opencontainers.image.description="Backend API for the Rider Voice public review MVP"

RUN groupadd --system --gid 10001 ridervoice && \
    useradd --system --uid 10001 --gid ridervoice --home-dir /app --shell /usr/sbin/nologin ridervoice

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

USER 10001:10001

CMD ["java", "-jar", "/app/app.jar"]
