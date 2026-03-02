# Orders Service — application.yml Explained

Spring Boot reads `src/main/resources/application.yml` at startup to configure the app.
This file defines the port, PostgreSQL connection, RabbitMQ connection, health endpoints,
and logging levels.

---

## I. What is application.yml?

It is the **settings file** for the Java app. It tells Spring Boot:
- What port to listen on
- How to connect to PostgreSQL
- How to connect to RabbitMQ
- Which queue to listen on for incoming order events
- Which HTTP endpoints to expose
- How verbose the logs should be

Connection values are left as placeholders — the real values are injected at
runtime via environment variables.

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
    name: guitarshop-orders
  datasource:
    url: jdbc:postgresql://${DB_HOST:orders-db}:${DB_PORT:5432}/${DB_NAME:guitarshop_orders}
    username: ${DB_USER:guitarshop}
    password: ${DB_PASSWORD:guitarshop123}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

PostgreSQL connection settings using JPA (Java Persistence API).

| Property      | Env Var       | Default               |
|---------------|---------------|-----------------------|
| `DB_HOST`     | `DB_HOST`     | `orders-db`           |
| `DB_PORT`     | `DB_PORT`     | `5432`                |
| `DB_NAME`     | `DB_NAME`     | `guitarshop_orders`   |
| `DB_USER`     | `DB_USER`     | `guitarshop`          |
| `DB_PASSWORD` | `DB_PASSWORD` | `guitarshop123`       |

> `ddl-auto: update` means Spring will automatically create or update database
> tables on startup based on the Java entity classes — no manual SQL needed.

---

```yaml
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guitarshop}
    password: ${RABBITMQ_PASSWORD:guitarshop123}
```

RabbitMQ connection settings. The orders service **consumes** messages published
by the checkout service.

| Property            | Env Var             | Default       |
|---------------------|---------------------|---------------|
| `RABBITMQ_HOST`     | `RABBITMQ_HOST`     | `rabbitmq`    |
| `RABBITMQ_PORT`     | `RABBITMQ_PORT`     | `5672`        |
| `RABBITMQ_USER`     | `RABBITMQ_USER`     | `guitarshop`  |
| `RABBITMQ_PASSWORD` | `RABBITMQ_PASSWORD` | `guitarshop123` |

---

```yaml
guitarshop:
  rabbitmq:
    queue: checkout.events
```

A custom app-level setting (not a Spring default). It tells the orders service
which RabbitMQ queue to listen on. The checkout service publishes to `checkout.events`,
and the orders service reads from it.

```
checkout service  ──publishes──▶  [checkout.events queue]  ──consumed by──▶  orders service
```

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

Enables Spring Boot Actuator endpoints over HTTP:

| Endpoint  | URL                 | Purpose                              |
|-----------|---------------------|--------------------------------------|
| `health`  | `/actuator/health`  | Is the app, PostgreSQL, RabbitMQ up? |
| `info`    | `/actuator/info`    | App name and version                 |
| `metrics` | `/actuator/metrics` | CPU, memory, request counts          |

> The orders service maps `/orders/health` as a shortcut to the actuator health endpoint.

---

```yaml
logging:
  level:
    com.guitarshop: INFO
    org.hibernate.SQL: WARN
```

| Package           | Level | Meaning                                |
|-------------------|-------|----------------------------------------|
| `com.guitarshop` | INFO  | Shows normal app messages              |
| `org.hibernate.SQL` | WARN | Suppresses noisy SQL query logs      |

---

## III. How Env Vars Flow Into the App

```
docker run -e DB_HOST=test-orders-db -e RABBITMQ_HOST=test-rabbitmq
                │                              │
                ▼                              ▼
      ${DB_HOST:orders-db}         ${RABBITMQ_HOST:rabbitmq}
                │                              │
                ▼                              ▼
      connects to PostgreSQL        listens on RabbitMQ queue
```

---

## IV. Connection to docker-compose.yml

```yaml
# docker-compose.yml
orders:
  environment:
    DB_HOST:           orders-db       # ← maps to ${DB_HOST:orders-db}
    DB_PORT:           "5432"          # ← maps to ${DB_PORT:5432}
    DB_NAME:           guitarshop_orders
    DB_USER:           guitarshop
    DB_PASSWORD:       guitarshop123
    RABBITMQ_HOST:     rabbitmq        # ← maps to ${RABBITMQ_HOST:rabbitmq}
    RABBITMQ_PORT:     "5672"
    RABBITMQ_USER:     guitarshop
    RABBITMQ_PASSWORD: guitarshop123
```

The default values in `application.yml` match the Docker Compose service names,
so the app works out of the box with `docker compose up`.
