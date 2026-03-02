# Catalog Service — Configuration Explained

The catalog service is written in **Go**. Unlike the Spring Boot services (cart, orders, ui),
it has no `application.yml`. Instead, configuration is read directly from environment
variables inside the source code using a helper function.

---

## I. How Go Reads Configuration

In `cmd/main.go` a small helper function handles all config:

```go
func getEnv(key, fallback string) string {
    if v := os.Getenv(key); v != "" {
        return v
    }
    return fallback
}
```

This is the Go equivalent of Spring Boot's `${VAR:default}` syntax:

| Spring Boot (application.yml)      | Go (main.go)                          |
|------------------------------------|---------------------------------------|
| `${DB_HOST:catalog-db}`            | `getEnv("DB_HOST", "catalog-db")`     |
| `${DB_PORT:3306}`                  | `getEnv("DB_PORT", "3306")`           |

---

## II. Database Connection

```go
func connectDB() {
    dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true",
        getEnv("DB_USER",     "guitarshop"),
        getEnv("DB_PASSWORD", "guitarshop123"),
        getEnv("DB_HOST",     "catalog-db"),
        getEnv("DB_PORT",     "3306"),
        getEnv("DB_NAME",     "guitarshop_catalog"),
    )
    ...
}
```

All five MySQL connection values are read from env vars:

| Env Var       | Default               | Purpose                  |
|---------------|-----------------------|--------------------------|
| `DB_HOST`     | `catalog-db`          | MySQL container name     |
| `DB_PORT`     | `3306`                | MySQL default port       |
| `DB_NAME`     | `guitarshop_catalog`  | Database to connect to   |
| `DB_USER`     | `guitarshop`          | Database user            |
| `DB_PASSWORD` | `guitarshop123`       | Database password        |

The app also retries the connection up to **15 times** (4 seconds apart) to wait
for MySQL to finish starting before giving up.

---

## III. Port Configuration

```go
func main() {
    port := getEnv("PORT", "8080")
    log.Fatal(http.ListenAndServe(":"+port, ...))
}
```

| Env Var | Default | Purpose              |
|---------|---------|----------------------|
| `PORT`  | `8080`  | Port the app listens on |

This must match `EXPOSE 8080` in the Dockerfile and `-p 8080:8080` in `docker run`.

---

## IV. Database Seeding

On first startup the app automatically:
1. Creates the `products` and `categories` tables if they don't exist
2. Seeds 6 categories (Electric Guitars, Acoustic Guitars, Bass Guitars, etc.)
3. Seeds 10 products (Fender Stratocaster, Gibson Les Paul, etc.)

This means the catalog database is **ready to use with no manual setup**.

---

## V. Available Endpoints

Registered in `main()`:

| Method | Path              | Purpose                        |
|--------|-------------------|--------------------------------|
| GET    | `/health`         | Health check                   |
| GET    | `/products`       | List all products (filterable) |
| GET    | `/products/{id}`  | Get a single product by ID     |
| GET    | `/categories`     | List all categories            |

> `/products` supports query params: `?category=Electric+Guitars` and `?search=fender`

---

## VI. How Env Vars Flow Into the App

```
docker run -e DB_HOST=test-catalog-db -e DB_PORT=3306
                │                          │
                ▼                          ▼
      getEnv("DB_HOST", "catalog-db")   getEnv("DB_PORT", "3306")
                │                          │
                └──────────┬───────────────┘
                           ▼
              connects to test-catalog-db:3306
```

---

## VII. Connection to docker-compose.yml

```yaml
# docker-compose.yml
catalog:
  environment:
    DB_HOST:     catalog-db          # ← getEnv("DB_HOST", "catalog-db")
    DB_PORT:     "3306"              # ← getEnv("DB_PORT", "3306")
    DB_NAME:     guitarshop_catalog  # ← getEnv("DB_NAME", "guitarshop_catalog")
    DB_USER:     guitarshop          # ← getEnv("DB_USER", "guitarshop")
    DB_PASSWORD: guitarshop123       # ← getEnv("DB_PASSWORD", "guitarshop123")
```

The defaults in `getEnv()` match the Docker Compose service names exactly,
so the app works out of the box with `docker compose up`.

---

## VIII. Comparison: Go vs Spring Boot Config

| Aspect              | Go (catalog)                        | Spring Boot (cart, orders, ui)        |
|---------------------|-------------------------------------|---------------------------------------|
| Config file         | None                                | `application.yml`                     |
| Reads env vars via  | `os.Getenv()` in source code        | `${VAR:default}` in yml               |
| Default values      | Second arg of `getEnv()`            | After the `:` in `${VAR:default}`     |
| Health endpoint     | Manually coded in `main.go`         | Auto-provided by Spring Actuator      |
| Port config         | `getEnv("PORT", "8080")`            | `server.port: 8080` in yml            |
