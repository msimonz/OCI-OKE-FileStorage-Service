# ── Stage 1: Build ────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
# Descarga dependencias primero (cache de Docker)
RUN mvn dependency:go-offline -q

COPY src/ ./src/
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────
FROM docker.io/library/eclipse-temurin:17-jre

WORKDIR /app

RUN mkdir -p /mnt/filestore

COPY --from=builder /build/target/filestore-api-1.0.0.jar app.jar

ENV STORAGE_PATH=/mnt/filestore
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]