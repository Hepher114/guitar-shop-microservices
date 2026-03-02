# UI Service — application.yml Explained

Spring Boot reads `src/main/resources/application.yml` at startup to configure the app.
This file defines the port, backend service URLs, Thymeleaf settings, health endpoints,
and logging levels.

---

## I. What is application.yml?

It is the **settings file** for the Java app. It tells Spring Boot:
- What port to listen on
- Where to find each backend microservice (catalog, cart, checkout, orders)
- How to render HTML templates (Thymeleaf)
- Which HTTP endpoints to expose
- How verbose the logs should be

Unlike cart and orders, the UI has **no database**. Its only dependencies are
the four backend services it calls over HTTP.

---

## II. Full File Walkthrough

```yaml
server:
  port: 8080
```

The app listens on port `8080`. This must match:
- `EXPOSE 8080` in the Dockerfile
- `-p 8080:8080` in the `docker run` command
- The port mapping in `docker-compose.yml`

---

```yaml
spring:
  application:
    name: guitarshop-ui
  thymeleaf:
    cache: false
    mode: HTML
```

Thymeleaf is the HTML templating engine that generates the pages users see in their browser.

| Property      | Value   | Meaning                                              |
|---------------|---------|------------------------------------------------------|
| `cache: false`| false   | Templates are re-read on every request (good for dev)|
| `mode: HTML`  | HTML    | Parse templates as standard HTML5                    |

---

```yaml
services:
  catalog:
    url: ${CATALOG_SERVICE_URL:http://catalog:8080}
  cart:
    url: ${CART_SERVICE_URL:http://cart:8080}
  checkout:
    url: ${CHECKOUT_SERVICE_URL:http://checkout:8080}
  orders:
    url: ${ORDERS_SERVICE_URL:http://orders:8080}
```

The URLs of all four backend services. The UI calls these over HTTP to fetch
data and render pages.

| Service    | Env Var                | Default                     |
|------------|------------------------|-----------------------------|
| `catalog`  | `CATALOG_SERVICE_URL`  | `http://catalog:8080`       |
| `cart`     | `CART_SERVICE_URL`     | `http://cart:8080`          |
| `checkout` | `CHECKOUT_SERVICE_URL` | `http://checkout:8080`      |
| `orders`   | `ORDERS_SERVICE_URL`   | `http://orders:8080`        |

> The defaults (`http://catalog:8080`, etc.) use Docker Compose service names,
> so the app works out of the box with `docker compose up`.

---

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

Enables Spring Boot Actuator endpoints over HTTP:

| Endpoint | URL                | Purpose                         |
|----------|--------------------|---------------------------------|
| `health` | `/actuator/health` | Is the UI app up?               |
| `info`   | `/actuator/info`   | App name and version            |

> The UI only exposes `health` and `info` — no `metrics` unlike cart and orders.

---

```yaml
logging:
  level:
    com.guitarshop: INFO
```

| Package          | Level | Meaning                   |
|------------------|-------|---------------------------|
| `com.guitarshop` | INFO  | Shows normal app messages |

---

## III. How the UI Calls Backend Services

```
Browser
   │
   ▼
guitarshop-ui (port 8080)
   │
   ├── GET http://catalog:8080/products    ← CATALOG_SERVICE_URL
   ├── GET http://cart:8080/cart/{id}      ← CART_SERVICE_URL
   ├── POST http://checkout:8080/checkout  ← CHECKOUT_SERVICE_URL
   └── GET http://orders:8080/orders/{id}  ← ORDERS_SERVICE_URL
```

All backend calls happen **server-side** inside the UI container. The browser
only ever talks to the UI on port 8080 — it never directly contacts the other services.

---

## IV. How Env Vars Flow Into the App

```
docker run -e CATALOG_SERVICE_URL=http://test-catalog:8080
                │
                ▼
      application.yml: ${CATALOG_SERVICE_URL:http://catalog:8080}
                │
                ▼
      UI calls → http://test-catalog:8080
```

---

## V. Connection to docker-compose.yml

```yaml
# docker-compose.yml
ui:
  environment:
    CATALOG_SERVICE_URL:  http://catalog:8080    # ← overrides the default
    CART_SERVICE_URL:     http://cart:8080
    CHECKOUT_SERVICE_URL: http://checkout:8080
    ORDERS_SERVICE_URL:   http://orders:8080
```

In this case the env vars match the defaults exactly — they are set explicitly
to make the configuration visible and easy to change.
