FROM curlimages/curl:latest AS otel_download
WORKDIR /otel
RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o opentelemetry-javaagent.jar

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=secret,id=github_token,target=/run/secrets/github_token \
    --mount=type=cache,target=/root/.m2/repository \
    mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>umutozturk34</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/github_token)" > /root/.m2/settings.xml && \
    mvn -B -U package -DskipTests && \
    rm /root/.m2/settings.xml

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY --from=otel_download /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT exec java -javaagent:/app/opentelemetry-javaagent.jar -jar app.jar
