# Checkout Service — Configuration Explained

The checkout service is written in **Node.js**. Like Go, it has no `application.yml`.
Configuration is read directly from environment variables using `process.env` spread
across three source files.

---

## I. How Node.js Reads Configuration

Node.js accesses environment variables through the global `process.env` object:

```js
process.env.VARIABLE_NAME || 'default value'
```

The `||` operator provides the fallback — if the env var is not set or is empty,
the default on the right is used.

| Spring Boot (application.yml)       | Node.js (source code)                         |
|-------------------------------------|-----------------------------------------------|
| `${DB_HOST:checkout-db}`            | `process.env.DB_HOST \|\| 'checkout-db'`      |
| `${RABBITMQ_URL:amqp://...}`        | `process.env.RABBITMQ_URL \|\| 'amqp://...'`  |

---

## II. Port — src/index.js

```js
const PORT = process.env.PORT || 8080;
app.listen(PORT, ...);
```

| Env Var | Default | Purpose                 |
|---------|---------|-------------------------|
| `PORT`  | `8080`  | Port the app listens on |

The health endpoint is also defined here:

```js
app.get('/health', (req, res) => {
    res.json({ status: 'UP', service: 'guitarshop-checkout', timestamp: new Date().toISOString() });
});
```

> Unlike Spring Boot, there is no Actuator. The `/health` endpoint is a plain
> route manually written in `index.js`.

---

## III. Database Connection — src/services/db.js

```js
const pool = new Pool({
    host:     process.env.DB_HOST     || 'checkout-db',
    port:     process.env.DB_PORT     || 5432,
    database: process.env.DB_NAME     || 'guitarshop_checkout',
    user:     process.env.DB_USER     || 'guitarshop',
    password: process.env.DB_PASSWORD || 'guitarshop123',
});
```

PostgreSQL connection using the `pg` (node-postgres) library:

| Env Var       | Default               | Purpose                |
|---------------|-----------------------|------------------------|
| `DB_HOST`     | `checkout-db`         | PostgreSQL container   |
| `DB_PORT`     | `5432`                | PostgreSQL default port|
| `DB_NAME`     | `guitarshop_checkout` | Database to connect to |
| `DB_USER`     | `guitarshop`          | Database user          |
| `DB_PASSWORD` | `guitarshop123`       | Database password      |

The app also retries the connection up to **10 times** (3 seconds apart) and
automatically creates the `checkouts` table on first startup if it doesn't exist.

---

## IV. RabbitMQ Connection — src/services/messaging.js

```js
const url = process.env.RABBITMQ_URL || 'amqp://guitarshop:guitarshop123@rabbitmq:5672';
```

Unlike the orders service which uses four separate env vars (`RABBITMQ_HOST`,
`RABBITMQ_PORT`, etc.), checkout uses a **single connection URL**:

| Env Var        | Default                                           | Purpose                    |
|----------------|---------------------------------------------------|----------------------------|
| `RABBITMQ_URL` | `amqp://guitarshop:guitarshop123@rabbitmq:5672`  | Full RabbitMQ connection   |

The URL format is: `amqp://user:password@host:port`

The checkout service **publishes** to the `checkout.events` queue every time an
order is placed. The orders service listens on that same queue.

```
checkout  ──publishes──▶  [checkout.events]  ──consumed by──▶  orders
```

> RabbitMQ connection also retries up to 10 times. If it never connects, the
> app continues running but logs a warning — checkout still works, orders just
> won't receive the event.

---

## V. Config Spread Across Files

Unlike Spring Boot where all config lives in one `application.yml`, checkout
config is split across three files by concern:

```
src/
├── index.js              → PORT, health endpoint, routes
├── services/
│   ├── db.js             → DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
│   └── messaging.js      → RABBITMQ_URL
```

---

## VI. How Env Vars Flow Into the App

```
docker run -e DB_HOST=test-checkout-db \
           -e RABBITMQ_URL=amqp://guitarshop:guitarshop123@test-rabbitmq:5672
                │                              │
                ▼                              ▼
      process.env.DB_HOST            process.env.RABBITMQ_URL
      (in db.js)                     (in messaging.js)
                │                              │
                ▼                              ▼
      connects to PostgreSQL         publishes to RabbitMQ
```

---

## VII. Connection to docker-compose.yml

```yaml
# docker-compose.yml
checkout:
  environment:
    DB_HOST:      checkout-db                                    # ← db.js
    DB_PORT:      "5432"                                         # ← db.js
    DB_NAME:      guitarshop_checkout                            # ← db.js
    DB_USER:      guitarshop                                     # ← db.js
    DB_PASSWORD:  guitarshop123                                  # ← db.js
    RABBITMQ_URL: amqp://guitarshop:guitarshop123@rabbitmq:5672  # ← messaging.js
```

---

## VIII. Comparison: Node.js vs Go vs Spring Boot Config

| Aspect             | Node.js (checkout)               | Go (catalog)                  | Spring Boot (cart, orders, ui)    |
|--------------------|----------------------------------|-------------------------------|-----------------------------------|
| Config file        | None                             | None                          | `application.yml`                 |
| Reads env vars via | `process.env.VAR \|\| default`   | `os.Getenv()` + helper func   | `${VAR:default}` in yml           |
| Config location    | Split across multiple `.js` files| All in `main.go`              | One central `application.yml`     |
| Health endpoint    | Manually coded in `index.js`     | Manually coded in `main.go`   | Auto-provided by Spring Actuator  |
| RabbitMQ config    | Single `RABBITMQ_URL` string     | N/A                           | Four separate `RABBITMQ_*` vars   |
| DB retry logic     | Manual loop in `db.js`           | Manual loop in `main.go`      | Handled by Spring framework       |
