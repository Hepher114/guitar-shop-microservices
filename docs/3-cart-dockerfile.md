# Cart Service — Dockerfile Walkthrough & Build

The cart service is a Java/Spring Boot app that stores shopping cart data in Redis.
It uses a two-stage Docker build to keep the final image small and secure.

---

## I. Prerequisites

- EC2 instance running
- Docker installed
- Repository cloned

---

## II. Create the Cart Service Dockerfile

Navigate to the cart service directory and create the Dockerfile.

```bash
cd cart
vim Dockerfile
```

Dockerfile:
```dockerfile
# Stage 1 — Build
FROM maven:3.9-eclipse-temurin-17 AS builder   # Maven + Java 17 to compile the code

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B            

COPY src ./src
RUN mvn clean package -DskipTests -B           

# Stage 2 — Runtime
FROM gcr.io/distroless/java17-debian12         

WORKDIR /app
COPY --from=builder /app/target/cart-service.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar", "app.jar"]
```

| Stage   | Base Image                        | Purpose                          |
|---------|-----------------------------------|----------------------------------|
| builder | maven:3.9-eclipse-temurin-17      | Compile Java source into a jar   |
| runtime | gcr.io/distroless/java17-debian12 | Run the jar, nothing else        |

---

## III. Create a Network

```bash
docker network create guitarshop-test
```

> Containers on the same network can reach each other by name.

---

## IV. Start Redis

```bash
docker run -d \
  --name test-redis \
  --network guitarshop-test \
  redis:7-alpine
```

---

## V. Build the Cart Image

```bash
cd microservices/cart
docker build -t guitarshop-cart-test .
```

---

## VI. Run the Cart Container

```bash
docker run -d \
  --name test-cart \
  --network guitarshop-test \
  -p 8080:8080 \
  -e REDIS_HOST=test-redis \
  -e REDIS_PORT=6379 \
  guitarshop-cart-test
```

| Flag            | Purpose                                         |
|-----------------|-------------------------------------------------|
| `--network`     | Join the same network as Redis                  |
| `-p 8080:8080`  | Expose the app on your host machine             |
| `-e REDIS_HOST` | Tell the app where Redis is (by container name) |
| `-e REDIS_PORT` | Redis default port                              |

---

## VII. Verify

Replace `<EC2-PUBLIC-IP>` with your EC2 instance's public IP address.

```bash
curl http://<EC2-PUBLIC-IP>:8080/cart/health
```

Expected response:

```json
{"status":"UP","service":"guitarshop-cart"}
```

---

## VIII. Cleanup

```bash
docker stop test-cart test-redis
docker rm test-cart test-redis
docker network rm guitarshop-test
```
