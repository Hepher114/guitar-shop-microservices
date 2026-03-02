# UI Service — Dockerfile Walkthrough & Build

The UI service is a Java/Spring Boot + Thymeleaf app. It is the frontend of the application
— it calls all other microservices (catalog, cart, checkout, orders) and renders the HTML
pages that users see. It uses a two-stage Docker build.

---

## I. Prerequisites

- EC2 instance running
- Docker installed
- Repository cloned
- All four backend services running (catalog, cart, checkout, orders)

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

COPY --from=builder /app/target/ui-service-*.jar app.jar
RUN chown guitarshop:guitarshop app.jar
USER guitarshop                                # Run as non-root for security

EXPOSE 8080

ENTRYPOINT ["java","-jar", "app.jar"]
```

| Stage   | Base Image                   | Purpose                          |
|---------|------------------------------|----------------------------------|
| builder | maven:3.9-eclipse-temurin-17 | Compile Java source into a jar   |
| runtime | eclipse-temurin:17-jre-jammy | Run the jar with JRE only        |

> The UI has no hardcoded service URLs. It reads the locations of all backend
> services from environment variables at runtime:
> - `CATALOG_SERVICE_URL` → catalog service
> - `CART_SERVICE_URL` → cart service
> - `CHECKOUT_SERVICE_URL` → checkout service
> - `ORDERS_SERVICE_URL` → orders service

> Unlike other services, the UI has **no database**. It depends entirely on the
> four backend microservices being reachable on the network.

---

## III. Note on Testing the UI in Isolation

The UI calls the other services on every page load. To test it properly you need
all four services running. The simplest approach is to use Docker Compose for the
full stack rather than running the UI in isolation.

If you still want to run it standalone, start all backend services first, then
point the UI at them via environment variables.

---

## IV. Build the UI Image

```bash
cd microservices/ui
docker build -t guitarshop-ui-test .
```

> First build takes ~2-3 minutes. Subsequent builds are fast due to layer caching.

---

## V. Run the UI Container (full stack on same network)

```bash
docker run -d \
  --name test-ui \
  --network guitarshop-test \
  -p 8080:8080 \
  -e CATALOG_SERVICE_URL=http://test-catalog:8080 \
  -e CART_SERVICE_URL=http://test-cart:8080 \
  -e CHECKOUT_SERVICE_URL=http://test-checkout:8080 \
  -e ORDERS_SERVICE_URL=http://test-orders:8080 \
  guitarshop-ui-test
```

| Flag                    | Purpose                                           |
|-------------------------|---------------------------------------------------|
| `--network`             | Must be on the same network as all backend services |
| `-p 8080:8080`          | Expose the UI on your host machine (port 8080)    |
| `-e CATALOG_SERVICE_URL`| Where the catalog service is (by container name)  |
| `-e CART_SERVICE_URL`   | Where the cart service is (by container name)     |
| `-e CHECKOUT_SERVICE_URL` | Where the checkout service is                   |
| `-e ORDERS_SERVICE_URL` | Where the orders service is                       |

---

## VI. Verify

Replace `<EC2-PUBLIC-IP>` with your EC2 instance's public IP address.

```bash
curl http://<EC2-PUBLIC-IP>:8080/health
```

Expected response:

```json
{"status":"UP","service":"guitarshop-ui"}
```

Open in a browser to see the full storefront:

```
http://<EC2-PUBLIC-IP>:8080
```

---

## VII. Cleanup

```bash
docker stop test-ui
docker rm test-ui
docker network rm guitarshop-test
```
