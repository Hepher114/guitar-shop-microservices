# Cart Service — application.yml Explained

Spring Boot reads `src/main/resources/application.yml` at startup to configure the app.
This file defines the port, Redis connection, health endpoints, and logging levels.

---

## I. What is application.yml?

It is the **settings file** for the Java app. It tells Spring Boot:
- What port to listen on
- How to connect to Redis
- Which HTTP endpoints to expose
- How verbose the logs should be

It is baked into the `.jar` at build time, but **connection values are left as
placeholders** — the real values are injected at runtime via environment variables.

---

## II. Full File Walkthrough

```yaml
server:
  port: 8080
```

The app listens on port `8080`. This must match:
- `EXPOSE 8080` in the Dockerfile
- `-p 8080:8080` in the `docker run` command

---

```yaml
spring:
  application:
    name: guitarshop-cart
  data:
    redis:
      host: ${REDIS_HOST:cart-redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
```

Redis connection settings. The `${VAR:default}` syntax means:
- Use the environment variable if it is set
- Fall back to the default value if it is not

| Property   | Env Var          | Default      |
|------------|------------------|--------------|
| `host`     | `REDIS_HOST`     | `cart-redis` |
| `port`     | `REDIS_PORT`     | `6379`       |
| `password` | `REDIS_PASSWORD` | *(empty)*    |

---

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

This enables Spring Boot Actuator endpoints over HTTP. Without this block,
`curl /health` returns **404**. With it, three endpoints become available:

| Endpoint   | URL                    | Purpose                        |
|------------|------------------------|--------------------------------|
| `health`   | `/actuator/health`     | Is the app and Redis up?       |
| `info`     | `/actuator/info`       | App name and version           |
| `metrics`  | `/actuator/metrics`    | CPU, memory, request counts    |

> The cart service maps `/cart/health` as a shortcut to the actuator health endpoint.

---

```yaml
logging:
  level:
    com.guitarshop: INFO
    org.springframework.data.redis: WARN
```

Controls how verbose the logs are per package:

| Package                          | Level  | Meaning                              |
|----------------------------------|--------|--------------------------------------|
| `com.guitarshop`                 | INFO   | Shows normal app messages            |
| `org.springframework.data.redis` | WARN   | Suppresses noisy Redis library logs  |

---

## III. How Env Vars Flow Into the App

```
docker run -e REDIS_HOST=test-redis -e REDIS_PORT=6379
                │                         │
                ▼                         ▼
      application.yml             application.yml
      ${REDIS_HOST:cart-redis}    ${REDIS_PORT:6379}
                │                         │
                └──────────┬──────────────┘
                           ▼
                app connects to test-redis:6379
```

The same image can point to any Redis instance just by changing the `-e` flags —
no rebuild needed.

---

## IV. Connection to the Dockerfile

```
Dockerfile                        application.yml
──────────────────────────────    ────────────────────────────
EXPOSE 8080               ←───→  server.port: 8080
ENTRYPOINT ["java","-jar",        reads env vars at startup
  "app.jar"]
```

The Dockerfile packages the code (including `application.yml`) into the image.
The `-e` flags in `docker run` supply the runtime values that `application.yml` expects.

---

## V. Connection to docker-compose.yml

In the full stack, Docker Compose replaces the `docker run -e` flags:

```yaml
# docker-compose.yml
cart:
  environment:
    REDIS_HOST: cart-redis    # ← maps to ${REDIS_HOST:cart-redis}
    REDIS_PORT: "6379"        # ← maps to ${REDIS_PORT:6379}
```

The default values in `application.yml` (`:cart-redis`, `:6379`) are intentionally
set to match the Docker Compose service names, so the app works out of the box
with `docker compose up` even without explicit env vars.
