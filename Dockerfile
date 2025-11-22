# Multi-stage Dockerfile for Brokr Platform
# Stage 1: Build Frontend
FROM node:22-alpine AS frontend-builder
WORKDIR /frontend

# Copy frontend package files
COPY brokr-frontend/package*.json ./
RUN npm ci

# Copy frontend source
COPY brokr-frontend/ ./

# Build frontend for production (will use relative /graphql endpoint)
ENV VITE_GRAPHQL_ENDPOINT=/graphql
RUN npm run build

# Stage 2: Build Backend
FROM maven:3.9-eclipse-temurin-17 AS backend-builder
WORKDIR /app

# Copy all pom.xml files first for dependency caching
COPY pom.xml .
COPY brokr-backend/pom.xml brokr-backend/
COPY brokr-backend/brokr-core/pom.xml brokr-backend/brokr-core/
COPY brokr-backend/brokr-storage/pom.xml brokr-backend/brokr-storage/
COPY brokr-backend/brokr-kafka/pom.xml brokr-backend/brokr-kafka/
COPY brokr-backend/brokr-security/pom.xml brokr-backend/brokr-security/
COPY brokr-backend/brokr-api/pom.xml brokr-backend/brokr-api/
COPY brokr-backend/brokr-app/pom.xml brokr-backend/brokr-app/

# Download dependencies
RUN mvn -B dependency:go-offline -f brokr-backend/pom.xml

# Copy source code
COPY brokr-backend/ brokr-backend/

# Copy frontend build to backend static resources
COPY --from=frontend-builder /frontend/dist /app/brokr-backend/brokr-app/src/main/resources/static/

# Build backend
RUN mvn -B clean package -DskipTests -f brokr-backend/pom.xml

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-jammy

# Install wget for health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd --gid 1001 brokr && \
    useradd --uid 1001 --gid 1001 --shell /bin/bash --create-home brokr

WORKDIR /app

# Copy the built application (Spring Boot creates repackaged JAR without -original suffix)
# The repackaged JAR is the executable one with all dependencies
COPY --from=backend-builder /app/brokr-backend/brokr-app/target/brokr-app-2.0.1-SNAPSHOT.jar app.jar

# Change ownership
RUN chown -R brokr:brokr /app

# Switch to non-root user
USER brokr

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

