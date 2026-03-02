# Helm Chart Structure

This section creates the Helm chart for the guitarshop application.
The chart packages all 5 microservices into a single deployable unit.

---

## I. Create the Chart

```bash
helm create guitarshop
```

This generates a boilerplate structure. Clean it and replace with our own:

```bash
rm -rf guitarshop/templates/*
rm guitarshop/templates/NOTES.txt
```

---

## II. Final Chart Structure

```
guitarshop/
├── Chart.yaml
├── values.yaml
└── templates/
    ├── namespace.yaml
    ├── configmap.yaml
    ├── secret.yaml
    ├── catalog-deployment.yaml
    ├── catalog-service.yaml
    ├── cart-deployment.yaml
    ├── cart-service.yaml
    ├── checkout-deployment.yaml
    ├── checkout-service.yaml
    ├── orders-deployment.yaml
    ├── orders-service.yaml
    ├── ui-deployment.yaml
    ├── ui-service.yaml
    └── ingress.yaml
```

---

## III. Chart.yaml

Metadata about the chart:

```yaml
# guitarshop/Chart.yaml
apiVersion: v2
name: guitarshop
description: GuitarShop microservices application
type: application
version: 1.0.0
appVersion: "1.0.0"
```

| Field        | Purpose                                      |
|--------------|----------------------------------------------|
| `version`    | Version of the chart itself                  |
| `appVersion` | Version of the application inside the chart  |

---

## IV. Template Syntax

Helm templates are standard Kubernetes YAML with `{{ }}` placeholders:

```yaml
# Raw Kubernetes manifest
image: 123456.dkr.ecr.us-east-1.amazonaws.com/guitarshop/cart:latest
replicas: 1

# Helm template
image: {{ .Values.registry }}/guitarshop/cart:{{ .Values.cart.tag }}
replicas: {{ .Values.cart.replicas }}
```

At deploy time, Helm replaces `{{ }}` with values from `values.yaml`.

---

## V. templates/namespace.yaml

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: {{ .Values.namespace }}
```

---

## VI. templates/configmap.yaml

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: guitarshop-config
  namespace: {{ .Values.namespace }}
data:
  DB_HOST:             "catalog-db"
  DB_PORT:             "3306"
  DB_NAME:             "guitarshop_catalog"
  DB_USER:             "guitarshop"
  REDIS_HOST:          "cart-redis"
  REDIS_PORT:          "6379"
  RABBITMQ_URL:        "amqp://guitarshop:guitarshop123@rabbitmq:5672"
  RABBITMQ_HOST:       "rabbitmq"
  RABBITMQ_PORT:       "5672"
  RABBITMQ_USER:       "guitarshop"
  CATALOG_SERVICE_URL:  "http://catalog:8080"
  CART_SERVICE_URL:     "http://cart:8080"
  CHECKOUT_SERVICE_URL: "http://checkout:8080"
  ORDERS_SERVICE_URL:   "http://orders:8080"
```

---

## VII. templates/secret.yaml

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: guitarshop-secret
  namespace: {{ .Values.namespace }}
type: Opaque
data:
  DB_PASSWORD:       {{ .Values.db.password | b64enc | quote }}
  RABBITMQ_PASSWORD: {{ .Values.rabbitmq.password | b64enc | quote }}
```

> `b64enc` is a Helm function that base64-encodes the value automatically.
> You write plain text in `values.yaml` — Helm handles the encoding.

---

## VIII. templates/cart-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cart
  namespace: {{ .Values.namespace }}
spec:
  replicas: {{ .Values.cart.replicas }}
  selector:
    matchLabels:
      app: cart
  template:
    metadata:
      labels:
        app: cart
    spec:
      containers:
        - name: cart
          image: {{ .Values.registry }}/guitarshop/cart:{{ .Values.cart.tag }}
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: guitarshop-config
            - secretRef:
                name: guitarshop-secret
          livenessProbe:
            httpGet:
              path: /cart/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /cart/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

> The same pattern (`{{ .Values.service.replicas }}`, `{{ .Values.service.tag }}`)
> applies to all 5 service deployment templates.

---

## IX. templates/ingress.yaml

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: guitarshop-ingress
  namespace: {{ .Values.namespace }}
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: ui
                port:
                  number: 8080
```

---

## X. Preview Generated Manifests

Before deploying, you can see exactly what Helm will apply:

```bash
helm template guitarshop ./guitarshop -f values.yaml
```

This renders all templates with values substituted — useful for debugging.
