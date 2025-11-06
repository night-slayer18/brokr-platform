FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY brokr-backend/pom.xml brokr-backend/
COPY brokr-core/pom.xml brokr-core/
COPY brokr-storage/pom.xml brokr-storage/
COPY brokr-kafka/pom.xml brokr-kafka/
COPY brokr-security/pom.xml brokr-security/
COPY brokr-api/pom.xml brokr-api/
COPY brokr-app/pom.xml brokr-app/
COPY brokr-frontend/pom.xml brokr-frontend/
RUN mvn -B dependency:go-offline
COPY brokr-backend/src brokr-backend/src
COPY brokr-core/src brokr-core/src
COPY brokr-storage/src brokr-storage/src
COPY brokr-kafka/src brokr-kafka/src
COPY brokr-security/src brokr-security/src
COPY brokr-api/src brokr-api/src
COPY brokr-app/src brokr-app/src
COPY brokr-frontend/package.json brokr-frontend/package.json
RUN mvn -B clean install -DskipTests -pl brokr-app -am

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 1001 brokr && \
    useradd --uid 1001 --gid 1001 --shell /bin/bash --create-home brokr
WORKDIR /app
COPY --from=builder /app/brokr-app/target/*.jar app.jar
RUN chown brokr:brokr app.jar
USER brokr
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]