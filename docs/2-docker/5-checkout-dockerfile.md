# Checkout Service — Dockerfile Walkthrough & Build

The checkout service is a Node.js/Express app that processes orders, stores them in
PostgreSQL, and publishes order events to RabbitMQ for the orders service to consume.
It uses a two-stage Docker build to install only production dependencies in the final image.

---

## I. Prerequisites

- EC2 instance running
- Docker installed
- Repository cloned

---

## II. The Dockerfile Explained

```dockerfile
# Stage 1 — Dependencies
FROM node:18-alpine AS deps        # Node 18 on Alpine to install packages

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev              # Install only production dependencies (no devDependencies)

# Stage 2 — Runtime
FROM node:18-alpine                # Fresh Alpine image, no build tools

WORKDIR /app

RUN addgroup -S guitarshop && adduser -S guitarshop -G guitarshop

COPY --from=deps /app/node_modules ./node_modules   # Copy only what we need
COPY src ./src
COPY package.json .

RUN chown -R guitarshop:guitarshop /app
USER guitarshop                    # Run as non-root for security

EXPOSE 8080

CMD ["node", "src/index.js"]
```

| Stage | Base Image      | Purpose                                    |
|-------|-----------------|--------------------------------------------|
| deps  | node:18-alpine  | Install production npm dependencies        |
| runtime | node:18-alpine | Run the app with only what's needed       |

> The Dockerfile has no mention of PostgreSQL or RabbitMQ. The app reads all
> connection details from environment variables at runtime:
> - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` → PostgreSQL
> - `RABBITMQ_URL` → RabbitMQ

---

## III. Create a Network

```bash
docker network create guitarshop-test
```

---

## IV. Start PostgreSQL

```bash
docker run -d \
  --name test-checkout-db \
  --network guitarshop-test \
  -e POSTGRES_DB=guitarshop_checkout \
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

> Wait ~15 seconds for both PostgreSQL and RabbitMQ to finish initializing.

---

## VI. Build the Checkout Image

```bash
cd microservices/checkout
docker build -t guitarshop-checkout-test .
```

> Node builds are fast (~30 seconds). Only production dependencies are installed.

---

## VII. Run the Checkout Container

```bash
docker run -d \
  --name test-checkout \
  --network guitarshop-test \
  -p 8080:8080 \
  -e DB_HOST=test-checkout-db \
  -e DB_PORT=5432 \
  -e DB_NAME=guitarshop_checkout \
  -e DB_USER=guitarshop \
  -e DB_PASSWORD=guitarshop123 \
  -e RABBITMQ_URL=amqp://guitarshop:guitarshop123@test-rabbitmq:5672 \
  guitarshop-checkout-test
```

| Flag              | Purpose                                            |
|-------------------|----------------------------------------------------|
| `--network`       | Join the same network as PostgreSQL and RabbitMQ   |
| `-p 8080:8080`    | Expose the app on your host machine                |
| `-e DB_HOST`      | Tell the app where PostgreSQL is (by container name) |
| `-e RABBITMQ_URL` | Full connection URL for RabbitMQ                   |

---

## VIII. Verify

Replace `<EC2-PUBLIC-IP>` with your EC2 instance's public IP address.

```bash
curl http://<EC2-PUBLIC-IP>:8080/health
```

Expected response:

```json
{"status":"UP","service":"guitarshop-checkout","timestamp":"..."}
```

---

## IX. Cleanup

```bash
docker stop test-checkout test-checkout-db test-rabbitmq
docker rm test-checkout test-checkout-db test-rabbitmq
docker network rm guitarshop-test
```
