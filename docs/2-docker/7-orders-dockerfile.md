# Orders Service — Dockerfile Walkthrough & Build

The orders service is a Java/Spring Boot app that consumes order events from RabbitMQ
and persists them to a PostgreSQL database. It uses a two-stage Docker build.

---

## I. Prerequisites

- EC2 instance running
- Docker installed
- Repository cloned

---

## II. The Dockerfile Explained

```dockerfile
# Stage 1 — Build
FROM maven:3.9-eclipse-temurin-17 AS builder   # Maven + Java 17 to compile the code

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B               # Download dependencies (cached layer)

COPY src ./src
RUN mvn clean package -DskipTests -B           # Compile into a .jar file

# Stage 2 — Runtime
FROM eclipse-temurin:17-jre-jammy              # JRE only — no compiler, smaller image

WORKDIR /app
RUN groupadd -r guitarshop && useradd -r -g guitarshop guitarshop

COPY --from=builder /app/target/orders-service-*.jar app.jar
RUN chown guitarshop:guitarshop app.jar
USER guitarshop                                # Run as non-root for security

EXPOSE 8080

ENTRYPOINT ["java","-jar", "app.jar"]
```

| Stage   | Base Image                   | Purpose                          |
|---------|------------------------------|----------------------------------|
| builder | maven:3.9-eclipse-temurin-17 | Compile Java source into a jar   |
| runtime | eclipse-temurin:17-jre-jammy | Run the jar with JRE only        |

> The Dockerfile has no mention of PostgreSQL or RabbitMQ. The app reads all
> connection details from environment variables at runtime:
> - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` → PostgreSQL
> - `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` → RabbitMQ

> Note: orders uses `eclipse-temurin:17-jre-jammy` instead of `distroless` (used by cart).
> It includes a shell, which is useful for debugging but slightly larger.

---

## III. Create a Network

```bash
docker network create guitarshop-test
```

---

## IV. Start PostgreSQL

```bash
docker run -d \
  --name test-orders-db \
  --network guitarshop-test \
  -e POSTGRES_DB=guitarshop_orders \
  -e POSTGRES_USER=guitarshop \
  -e POSTGRES_PASSWORD=guitarshop123 \
  postgres:15-alpine
```

---

## V. Start RabbitMQ

```bash
docker run -d \
  --name test-rabbitmq \
  --network guitarshop-test \
  -e RABBITMQ_DEFAULT_USER=guitarshop \
  -e RABBITMQ_DEFAULT_PASS=guitarshop123 \
  rabbitmq:3.12-management-alpine
```

> Wait ~15 seconds for both services to finish initializing.

---

## VI. Build the Orders Image

```bash
cd microservices/orders
docker build -t guitarshop-orders-test .
```

> First build takes ~2-3 minutes (downloads Maven + dependencies).
> Subsequent builds are fast due to layer caching.

---

## VII. Run the Orders Container

```bash
docker run -d \
  --name test-orders \
  --network guitarshop-test \
  -p 8080:8080 \
  -e DB_HOST=test-orders-db \
  -e DB_PORT=5432 \
  -e DB_NAME=guitarshop_orders \
  -e DB_USER=guitarshop \
  -e DB_PASSWORD=guitarshop123 \
  -e RABBITMQ_HOST=test-rabbitmq \
  -e RABBITMQ_PORT=5672 \
  -e RABBITMQ_USER=guitarshop \
  -e RABBITMQ_PASSWORD=guitarshop123 \
  guitarshop-orders-test
```

| Flag                  | Purpose                                              |
|-----------------------|------------------------------------------------------|
| `--network`           | Join the same network as PostgreSQL and RabbitMQ     |
| `-p 8080:8080`        | Expose the app on your host machine                  |
| `-e DB_HOST`          | Tell the app where PostgreSQL is (by container name) |
| `-e RABBITMQ_HOST`    | Tell the app where RabbitMQ is (by container name)   |
| `-e RABBITMQ_PORT`    | RabbitMQ default port                                |

---

## VIII. Verify

Replace `<EC2-PUBLIC-IP>` with your EC2 instance's public IP address.

```bash
curl http://<EC2-PUBLIC-IP>:8080/orders/health
```

Expected response:

```json
{"status":"UP","service":"guitarshop-orders"}
```

---

## IX. Cleanup

```bash
docker stop test-orders test-orders-db test-rabbitmq
docker rm test-orders test-orders-db test-rabbitmq
docker network rm guitarshop-test
```
