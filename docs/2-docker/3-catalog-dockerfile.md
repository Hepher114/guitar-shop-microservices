# Catalog Service — Dockerfile Walkthrough & Build

The catalog service is a Go app that serves guitar product data from a MySQL database.
It uses a two-stage Docker build to produce a small, secure final image.

---

## I. Prerequisites

- EC2 instance running
- Docker installed
- Repository cloned

---

## II. The Dockerfile Explained

```dockerfile
# Stage 1 — Build
FROM golang:1.21-alpine AS builder   # Go 1.21 compiler on Alpine Linux

WORKDIR /app

RUN apk add --no-cache git           # Git required by go mod download

COPY go.mod go.sum ./
RUN go mod download                  # Download dependencies (cached layer)

COPY cmd/ ./cmd/
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o catalog ./cmd/main.go
                                     # Compile into a single binary

# Stage 2 — Runtime
FROM alpine:3.19                     # Minimal Linux, no compiler, no Go toolchain

WORKDIR /app
RUN addgroup -S guitarshop && adduser -S guitarshop -G guitarshop

COPY --from=builder /app/catalog .
RUN chown guitarshop:guitarshop catalog
USER guitarshop                      # Run as non-root for security

EXPOSE 8080

ENTRYPOINT ["/app/catalog"]
```

| Stage   | Base Image         | Purpose                              |
|---------|--------------------|--------------------------------------|
| builder | golang:1.21-alpine | Compile Go source into a binary      |
| runtime | alpine:3.19        | Run the binary, nothing else         |

> Key build flags: `CGO_ENABLED=0` disables C bindings (pure Go binary),
> `GOOS=linux` targets Linux, `-ldflags="-s -w"` strips debug info to reduce image size.

> The Dockerfile has no mention of MySQL. The app reads `DB_HOST`, `DB_PORT`,
> `DB_NAME`, `DB_USER`, and `DB_PASSWORD` from environment variables at runtime.

---

## III. Create a Network

```bash
docker network create guitarshop-test
```

> Containers on the same network can reach each other by name.

---

## IV. Start MySQL

```bash
docker run -d \
  --name test-catalog-db \
  --network guitarshop-test \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=guitarshop_catalog \
  -e MYSQL_USER=guitarshop \
  -e MYSQL_PASSWORD=guitarshop123 \
  mysql:8.0
```

> Wait ~20 seconds for MySQL to finish initializing before running the next step.

---

## V. Build the Catalog Image

```bash
cd microservices/catalog
docker build -t guitarshop-catalog-test .
```

> Go builds are fast (~30 seconds). Dependencies are cached after the first build.

---

## VI. Run the Catalog Container

```bash
docker run -d \
  --name test-catalog \
  --network guitarshop-test \
  -p 8080:8080 \
  -e DB_HOST=test-catalog-db \
  -e DB_PORT=3306 \
  -e DB_NAME=guitarshop_catalog \
  -e DB_USER=guitarshop \
  -e DB_PASSWORD=guitarshop123 \
  guitarshop-catalog-test
```

| Flag          | Purpose                                          |
|---------------|--------------------------------------------------|
| `--network`   | Join the same network as MySQL                   |
| `-p 8080:8080`| Expose the app on your host machine              |
| `-e DB_HOST`  | Tell the app where MySQL is (by container name)  |
| `-e DB_PORT`  | MySQL default port                               |
| `-e DB_NAME`  | Database name to connect to                      |
| `-e DB_USER`  | Database user                                    |
| `-e DB_PASSWORD` | Database password                            |

---

## VII. Verify

Replace `<EC2-PUBLIC-IP>` with your EC2 instance's public IP address.

```bash
curl http://<EC2-PUBLIC-IP>:8080/health
```

Expected response:

```json
{"status":"UP","service":"guitarshop-catalog"}
```

---

## VIII. Cleanup

```bash
docker stop test-catalog test-catalog-db
docker rm test-catalog test-catalog-db
docker network rm guitarshop-test
```
