# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy and install the root CA certificate into Java truststore
COPY rootCA.pem /tmp/rootCA.pem
RUN keytool -import -trustcacerts -noprompt \
    -alias odell-root-ca \
    -file /tmp/rootCA.pem \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the application (dependencies will be downloaded during build)
# Run as root in builder stage to avoid permission issues with mounted volumes in CI
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy and install the root CA certificate into Java truststore (runtime stage)
COPY rootCA.pem /tmp/rootCA.pem
RUN keytool -import -trustcacerts -noprompt \
    -alias odell-root-ca \
    -file /tmp/rootCA.pem \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit && \
    rm /tmp/rootCA.pem

# Create non-root user
RUN groupadd -r vmidentity && useradd -r -g vmidentity vmidentity

# Copy the built artifact
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R vmidentity:vmidentity /app

# Switch to non-root user
USER vmidentity

# Expose ports
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
