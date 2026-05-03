FROM curlimages/curl:latest AS otel_download
WORKDIR /otel
RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o opentelemetry-javaagent.jar

FROM maven:3.9-eclipse-temurin-21-alpine AS common_install
WORKDIR /workspace
COPY pom.xml .
COPY finance-common/pom.xml finance-common/pom.xml
COPY finance-cache/pom.xml finance-cache/pom.xml
COPY finance-crypto/pom.xml finance-crypto/pom.xml
COPY finance-stock/pom.xml finance-stock/pom.xml
COPY finance-forex/pom.xml finance-forex/pom.xml
COPY finance-user/pom.xml finance-user/pom.xml
COPY finance-fund/pom.xml finance-fund/pom.xml
COPY finance-bond/pom.xml finance-bond/pom.xml
COPY finance-commodity/pom.xml finance-commodity/pom.xml
COPY finance-news/pom.xml finance-news/pom.xml
COPY finance-portfolio/pom.xml finance-portfolio/pom.xml
COPY finance-app/pom.xml finance-app/pom.xml
COPY finance-common/src finance-common/src
RUN mvn install -pl finance-common -DskipTests

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY --from=common_install /root/.m2 /root/.m2
COPY finance-notification-app/pom.xml pom.xml
COPY finance-notification-app/src src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY --from=otel_download /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT exec java -javaagent:/app/opentelemetry-javaagent.jar -jar app.jar
