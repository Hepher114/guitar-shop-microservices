# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN groupadd -r guitarshop && useradd -r -g guitarshop guitarshop

COPY --from=builder /app/target/orders-service-*.jar app.jar
RUN chown guitarshop:guitarshop app.jar
USER guitarshop

EXPOSE 8080

ENTRYPOINT ["java","-jar", "app.jar"]
