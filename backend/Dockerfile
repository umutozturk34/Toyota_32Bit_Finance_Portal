FROM curlimages/curl:latest AS otel_download
WORKDIR /otel
RUN curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o opentelemetry-javaagent.jar

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY finance-common/pom.xml finance-common/pom.xml
COPY finance-common/src finance-common/src
RUN --mount=type=cache,target=/root/.m2/repository \
    cd finance-common && mvn -B install -DskipTests

COPY backend/pom.xml backend/pom.xml
COPY backend/finance-monolith-shared/pom.xml backend/finance-monolith-shared/pom.xml
COPY backend/finance-market/pom.xml backend/finance-market/pom.xml
COPY backend/finance-user/pom.xml backend/finance-user/pom.xml
COPY backend/finance-news/pom.xml backend/finance-news/pom.xml
COPY backend/finance-portfolio/pom.xml backend/finance-portfolio/pom.xml
COPY backend/finance-app/pom.xml backend/finance-app/pom.xml

RUN --mount=type=secret,id=github_token,target=/run/secrets/github_token \
    --mount=type=cache,target=/root/.m2/repository \
    mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>umutozturk34</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/github_token)" > /root/.m2/settings.xml && \
    cd backend && mvn -B dependency:go-offline

COPY backend/finance-monolith-shared/src backend/finance-monolith-shared/src
COPY backend/finance-market/src backend/finance-market/src
COPY backend/finance-user/src backend/finance-user/src
COPY backend/finance-news/src backend/finance-news/src
COPY backend/finance-portfolio/src backend/finance-portfolio/src
COPY backend/finance-app/src backend/finance-app/src

RUN --mount=type=secret,id=github_token,target=/run/secrets/github_token \
    --mount=type=cache,target=/root/.m2/repository \
    mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>umutozturk34</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/github_token)" > /root/.m2/settings.xml && \
    cd backend && mvn -B clean package -DskipTests && \
    cp finance-app/target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app.jar app.jar
COPY --from=otel_download /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT exec java -javaagent:/app/opentelemetry-javaagent.jar -jar app.jar
