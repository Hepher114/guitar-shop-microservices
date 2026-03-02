# Kubernetes Manifests

Kubernetes manifests are YAML files that describe what you want running in the cluster.
They are the Kubernetes equivalent of `docker-compose.yml` — but split into separate
files by resource type and service.

---

## I. Manifest Types Used in This Project

| Kind         | Purpose                                                   |
|--------------|-----------------------------------------------------------|
| `Namespace`  | Isolate all guitarshop resources in their own space       |
| `ConfigMap`  | Store non-sensitive config (URLs, ports, DB names)        |
| `Secret`     | Store sensitive config (passwords, credentials)           |
| `Deployment` | Define pods and how many replicas to run                  |
| `Service`    | Give pods a stable internal network name and IP           |
| `Ingress`    | Expose the UI to the internet via the ALB                 |

---

## II. Project File Structure

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── catalog/
│   ├── deployment.yaml
│   └── service.yaml
├── cart/
│   ├── deployment.yaml
│   └── service.yaml
├── checkout/
│   ├── deployment.yaml
│   └── service.yaml
├── orders/
│   ├── deployment.yaml
│   └── service.yaml
├── ui/
│   ├── deployment.yaml
│   └── service.yaml
└── ingress.yaml
```

---

## III. Namespace

Groups all guitarshop resources together so they don't mix with other workloads.

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: guitarshop
```

```bash
kubectl apply -f k8s/namespace.yaml
```

---

## IV. Deployment — catalog

```yaml
# k8s/catalog/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: catalog
  namespace: guitarshop
spec:
  replicas: 1
  selector:
    matchLabels:
      app: catalog
  template:
    metadata:
      labels:
        app: catalog
    spec:
      containers:
        - name: catalog
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/catalog:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: guitarshop-config
            - secretRef:
                name: guitarshop-secret
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

---

## V. Service — catalog

```yaml
# k8s/catalog/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: catalog
  namespace: guitarshop
spec:
  selector:
    app: catalog
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

> `ClusterIP` means internal-only — not accessible from outside the cluster.
> Only the UI pod can call `http://catalog:8080`.

---

## VI. Deployment — cart

```yaml
# k8s/cart/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cart
  namespace: guitarshop
spec:
  replicas: 1
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
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/cart:latest
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

---

## VII. Deployment — checkout

```yaml
# k8s/checkout/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: checkout
  namespace: guitarshop
spec:
  replicas: 1
  selector:
    matchLabels:
      app: checkout
  template:
    metadata:
      labels:
        app: checkout
    spec:
      containers:
        - name: checkout
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/checkout:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: guitarshop-config
            - secretRef:
                name: guitarshop-secret
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
```

---

## VIII. Deployment — orders

```yaml
# k8s/orders/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders
  namespace: guitarshop
spec:
  replicas: 1
  selector:
    matchLabels:
      app: orders
  template:
    metadata:
      labels:
        app: orders
    spec:
      containers:
        - name: orders
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/orders:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: guitarshop-config
            - secretRef:
                name: guitarshop-secret
          livenessProbe:
            httpGet:
              path: /orders/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /orders/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

---

## IX. Deployment — ui

```yaml
# k8s/ui/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ui
  namespace: guitarshop
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ui
  template:
    metadata:
      labels:
        app: ui
    spec:
      containers:
        - name: ui
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/ui:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: guitarshop-config
            - secretRef:
                name: guitarshop-secret
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

---

## X. Service — ui

```yaml
# k8s/ui/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: ui
  namespace: guitarshop
spec:
  selector:
    app: ui
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

> The UI service is `ClusterIP` — the ALB Ingress controller routes traffic
> to it directly by pod IP (`target-type: ip`).

---

## XI. Key Concepts

### Labels and Selectors
```yaml
# Deployment labels its pods:
template:
  metadata:
    labels:
      app: catalog   ← pod gets this label

# Service finds pods by this label:
spec:
  selector:
    app: catalog     ← matches pods with this label
```

### Health Probes
| Probe            | Purpose                                                      |
|------------------|--------------------------------------------------------------|
| `livenessProbe`  | Is the container alive? If not, Kubernetes restarts it       |
| `readinessProbe` | Is the container ready for traffic? If not, removed from LB  |

### envFrom
Instead of listing every env var individually, `envFrom` loads all values
from a ConfigMap and Secret at once — keeping manifests clean and DRY.
