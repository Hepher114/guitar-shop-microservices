# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Install git (needed for go mod download)
RUN apk add --no-cache git

# Copy dependency files first for better layer caching
COPY go.mod go.sum ./

# Download dependencies (this runs with full internet access during build)
RUN go mod download

# Copy source and build
COPY cmd/ ./cmd/
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o catalog ./cmd/main.go

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM alpine:3.19

WORKDIR /app
RUN addgroup -S guitarshop && adduser -S guitarshop -G guitarshop

COPY --from=builder /app/catalog .
RUN chown guitarshop:guitarshop catalog
USER guitarshop

EXPOSE 8080

ENTRYPOINT ["/app/catalog"]
