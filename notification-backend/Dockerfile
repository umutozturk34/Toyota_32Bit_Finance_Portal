FROM curlimages/curl:latest AS otel_download
WORKDIR /otel
RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o opentelemetry-javaagent.jar

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY finance-common/pom.xml finance-common/pom.xml
COPY finance-common/src finance-common/src
RUN --mount=type=cache,target=/root/.m2/repository \
    cd finance-common && mvn -B install -DskipTests

COPY notification-backend/pom.xml notification-backend/pom.xml
COPY notification-backend/src notification-backend/src
RUN --mount=type=cache,target=/root/.m2/repository \
    cd notification-backend && mvn -B -U package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /workspace/notification-backend/target/*.jar app.jar
COPY --from=otel_download /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT exec java -javaagent:/app/opentelemetry-javaagent.jar -jar app.jar
