# GuitarShop

A cloud-native e-commerce application for guitars, amps, and accessories. Built with 5 microservices across 10 containers using a polyglot stack — Go, Java, and Node.js. Runs locally with Docker Compose and deploys to AWS EKS.

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

When a customer checks out, Checkout publishes an `ORDER_CREATED` event to RabbitMQ. Orders consumes it asynchronously — so the customer gets an instant response while order processing happens in the background.

---

## Services

| Service | Language | Database | Responsibility |
|---------|----------|----------|----------------|
| **UI** | Java Spring Boot + Thymeleaf | — | Server-rendered frontend, proxies to backend services |
| **Catalog** | Go | MySQL 8.0 | Product listings, categories, search |
| **Cart** | Java Spring Boot | Redis 7 | Per-customer shopping cart (7-day TTL) |
| **Checkout** | Node.js 18 | PostgreSQL 15 | Processes checkouts, publishes events |
| **Orders** | Java Spring Boot | PostgreSQL 15 | Consumes checkout events, tracks order history |

---

## Quick Start

**Prerequisites:** Docker Desktop

```bash
git clone https://github.com/YOUR_USERNAME/guitarshop.git
cd guitarshop

docker compose up --build -d
```

Open [http://localhost:8080](http://localhost:8080)


| URL | Description |
|-----|-------------|
| http://localhost:8080 | GuitarShop storefront |
| http://localhost:15672 | RabbitMQ management (`guitarshop` / `guitarshop123`) |

```bash
# Stop and remove all data
docker compose down -v
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
| `POST` | `/checkout` | Create checkout |
| `GET` | `/checkout/{id}` | Get checkout by ID |

### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/orders/customer/{customerId}` | Get orders for customer |
| `PATCH` | `/orders/{id}/status` | Update order status |

---

## Deploy to AWS EKS

### Prerequisites

```bash
aws --version      # AWS CLI v2
kubectl version    # 1.28+
eksctl version     # 0.160+
docker --version   # 24+
```

### 1. Create the cluster

```bash
eksctl create cluster \
  --name guitarshop-eks \
  --region us-east-1 \
  --nodegroup-name guitarshop-nodes \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 5 \
  --managed
```

### 2. Create ECR repositories

```bash
for SERVICE in catalog cart checkout orders ui; do
  aws ecr create-repository \
    --repository-name guitarshop-$SERVICE \
    --region us-east-1
done
```

### 3. Build and push images

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com"

aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin $ECR_REGISTRY

for SERVICE in catalog cart checkout orders ui; do
  docker build -t $ECR_REGISTRY/guitarshop-$SERVICE:latest ./services/$SERVICE
  docker push $ECR_REGISTRY/guitarshop-$SERVICE:latest
done
```

### 4. Update Kubernetes manifests

```bash
find infrastructure/k8s -name "*.yaml" -exec \
  sed -i "s|YOUR_ECR_REGISTRY|$ECR_REGISTRY|g" {} \;
```

### 5. Install NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/aws/deploy.yaml

kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### 6. Deploy

```bash
kubectl apply -f infrastructure/k8s/namespace/namespace.yaml
kubectl apply -f infrastructure/k8s/messaging/rabbitmq.yaml
kubectl apply -f infrastructure/k8s/catalog/catalog.yaml
kubectl apply -f infrastructure/k8s/cart/cart.yaml
kubectl apply -f infrastructure/k8s/checkout/checkout.yaml
kubectl apply -f infrastructure/k8s/orders/orders.yaml
kubectl apply -f infrastructure/k8s/ui/ui.yaml
kubectl apply -f infrastructure/k8s/ingress/ingress.yaml

kubectl get pods -n guitarshop -w
```

### 7. Get the URL

```bash
kubectl get ingress guitarshop-ingress -n guitarshop
```

---

## CI/CD

Every push to `main` triggers a GitHub Actions pipeline that builds all 5 images, pushes to ECR with the commit SHA tag, and deploys to EKS with a zero-downtime rolling update.

Add these secrets to your repository:

| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | IAM user access key |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key |
| `AWS_ACCOUNT_ID` | 12-digit AWS account ID |

---

## Project Structure

```
guitarshop/
├── services/
│   ├── catalog/          # Go — product catalog + MySQL
│   ├── cart/             # Java Spring Boot — cart + Redis
│   ├── checkout/         # Node.js — checkout + PostgreSQL + RabbitMQ
│   ├── orders/           # Java Spring Boot — orders + PostgreSQL + RabbitMQ
│   └── ui/               # Java Spring Boot + Thymeleaf — web frontend
├── infrastructure/
│   └── k8s/
│       ├── namespace/
│       ├── catalog/
│       ├── cart/
│       ├── checkout/
│       ├── orders/
│       ├── messaging/
│       ├── ui/
│       └── ingress/
├── .github/
│   └── workflows/
│       └── deploy.yml
└── docker-compose.yml
```

---

## Tech Stack

- **Go** 1.21 — Catalog service
- **Java 17** + Spring Boot 3.2 — Cart, Orders, UI
- **Node.js 18** + Express — Checkout
- **MySQL 8** — Product catalog
- **PostgreSQL 15** — Checkout and order data
- **Redis 7** — Shopping cart sessions
- **RabbitMQ 3.12** — Async event messaging
- **Docker** + Docker Compose — Local development
- **Kubernetes** + AWS EKS — Production deployment
- **GitHub Actions** — CI/CD pipeline
