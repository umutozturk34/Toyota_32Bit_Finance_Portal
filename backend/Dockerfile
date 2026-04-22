FROM curlimages/curl:latest AS otel_download
WORKDIR /otel
RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o opentelemetry-javaagent.jar

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

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
RUN mvn dependency:go-offline -B

COPY finance-common/src finance-common/src
COPY finance-cache/src finance-cache/src
COPY finance-crypto/src finance-crypto/src
COPY finance-stock/src finance-stock/src
COPY finance-forex/src finance-forex/src
COPY finance-user/src finance-user/src
COPY finance-fund/src finance-fund/src
COPY finance-bond/src finance-bond/src
COPY finance-commodity/src finance-commodity/src
COPY finance-news/src finance-news/src
COPY finance-portfolio/src finance-portfolio/src
COPY finance-app/src finance-app/src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/finance-app/target/*.jar app.jar
COPY --from=otel_download /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT exec java -javaagent:/app/opentelemetry-javaagent.jar -Dkafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS} -jar app.jar
