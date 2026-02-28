# GuitarShop — Developer Guide

A polyglot microservices e-commerce app for guitars, amps, and accessories.
5 services, 3 languages, runs fully locally with Docker Compose.

---

## Architecture

```
                         ┌───────────────────┐
                         │    UI Service      │
                         │ Spring Boot :8080  │
                         └────────┬──────────┘
                                  │ HTTP
         ┌───────────────┬────────┴────────┬───────────────┐
         ▼               ▼                 ▼               ▼
   ┌───────────┐  ┌─────────────┐  ┌────────────┐  ┌───────────┐
   │  Catalog  │  │    Cart     │  │  Checkout  │  │  Orders   │
   │   (Go)    │  │   (Java)    │  │  (Node.js) │  │  (Java)   │
   └─────┬─────┘  └──────┬──────┘  └─────┬──────┘  └─────┬─────┘
         │               │               │               │
         ▼               ▼               ▼               ▲
       MySQL           Redis         PostgreSQL      PostgreSQL
                                         │               │
                                         └───RabbitMQ────┘
```

When a customer checks out, **Checkout** publishes an `ORDER_CREATED` event to RabbitMQ.
**Orders** consumes it asynchronously — the customer gets an instant response while order processing happens in the background.

---

## Services

| Service | Language | Database | Port |
|---------|----------|----------|------|
| **ui** | Java 17 + Spring Boot + Thymeleaf | — | 8080 |
| **catalog** | Go 1.21 | MySQL 8.0 | 8080 (internal) |
| **cart** | Java 17 + Spring Boot | Redis 7 | 8080 (internal) |
| **checkout** | Node.js 18 + Express | PostgreSQL 15 | 8080 (internal) |
| **orders** | Java 17 + Spring Boot | PostgreSQL 15 | 8080 (internal) |

---

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) — that's it

No need to install Go, Java, or Node.js locally. Everything builds inside containers.

---

## Running Locally

```bash
git clone https://github.com/YOUR_USERNAME/guitarshop.git
cd guitarshop

docker compose up --build
```

| URL | What it is |
|-----|------------|
| http://localhost:8080 | GuitarShop storefront |
| http://localhost:15672 | RabbitMQ management UI (`guitarshop` / `guitarshop123`) |

```bash
# Stop everything and wipe all data
docker compose down -v

# Rebuild a single service after code changes
docker compose up --build catalog
```

---

## Project Structure

```
guitarshop/
├── docker-compose.yml
└── services/
    ├── catalog/              # Go
    │   ├── Dockerfile
    │   ├── cmd/main.go
    │   ├── go.mod
    │   └── go.sum
    ├── cart/                 # Java / Maven
    │   ├── Dockerfile
    │   ├── pom.xml
    │   └── src/
    ├── checkout/             # Node.js
    │   ├── Dockerfile
    │   ├── package.json
    │   └── src/
    ├── orders/               # Java / Maven
    │   ├── Dockerfile
    │   ├── pom.xml
    │   └── src/
    └── ui/                   # Java / Maven + Thymeleaf templates
        ├── Dockerfile
        ├── pom.xml
        └── src/
            └── main/
                ├── java/         # Controllers, clients
                └── resources/
                    ├── templates/    # HTML pages (Thymeleaf)
                    └── static/       # CSS, JS, images
```

---

## API Reference

### Catalog
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/products` | List all products |
| `GET` | `/products?category=electric-guitars` | Filter by category |
| `GET` | `/products?search=fender` | Search products |
| `GET` | `/products/{id}` | Get product by ID |
| `GET` | `/categories` | List categories |
| `GET` | `/health` | Health check |

### Cart
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/cart/{customerId}` | Get cart |
| `POST` | `/cart/{customerId}/items` | Add item |
| `PUT` | `/cart/{customerId}/items/{productId}` | Update quantity |
| `DELETE` | `/cart/{customerId}/items/{productId}` | Remove item |

### Checkout
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/checkout` | Submit a checkout |
| `GET` | `/checkout/{id}` | Get checkout by ID |

### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/orders/customer/{customerId}` | Get orders for a customer |
| `PATCH` | `/orders/{id}/status` | Update order status |

---

## Environment Variables

Each service is configured via environment variables set in `docker-compose.yml`. No `.env` files needed for local development.

| Service | Key Variables |
|---------|--------------|
| catalog | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` |
| cart | `REDIS_HOST`, `REDIS_PORT` |
| checkout | `DB_HOST`, `DB_PORT`, `DB_NAME`, `RABBITMQ_URL` |
| orders | `DB_HOST`, `DB_PORT`, `DB_NAME`, `RABBITMQ_HOST`, `RABBITMQ_PORT` |
| ui | `CATALOG_SERVICE_URL`, `CART_SERVICE_URL`, `CHECKOUT_SERVICE_URL`, `ORDERS_SERVICE_URL` |

---

## Tech Stack

- **Go 1.21** — Catalog service
- **Java 17** + Spring Boot 3.2 — Cart, Orders, UI
- **Node.js 18** + Express — Checkout
- **MySQL 8** — Product catalog
- **PostgreSQL 15** — Checkout and order data
- **Redis 7** — Shopping cart sessions
- **RabbitMQ 3.12** — Async order events
- **Docker + Docker Compose** — Local development
