# ConfigMaps and Secrets

ConfigMaps and Secrets are how Kubernetes injects configuration into containers —
the equivalent of the `-e` flags in `docker run` or the `environment:` block in
`docker-compose.yml`.

---

## I. ConfigMap vs Secret

| Feature         | ConfigMap                          | Secret                              |
|-----------------|------------------------------------|-------------------------------------|
| Purpose         | Non-sensitive config               | Sensitive config                    |
| Storage         | Plain text in etcd                 | Base64-encoded in etcd              |
| Examples        | URLs, ports, DB names              | Passwords, API keys, tokens         |
| In manifests    | `configMapRef`                     | `secretRef`                         |

> Base64 is **not encryption** — it is just encoding. Secrets should be managed
> with tools like AWS Secrets Manager or Sealed Secrets in production.

---

## II. ConfigMap — Non-Sensitive Config

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: guitarshop-config
  namespace: guitarshop
data:
  # Catalog
  DB_HOST: "catalog-db"
  DB_PORT: "3306"
  DB_NAME: "guitarshop_catalog"
  DB_USER: "guitarshop"

  # Cart
  REDIS_HOST: "cart-redis"
  REDIS_PORT: "6379"

  # Checkout
  RABBITMQ_URL: "amqp://guitarshop:guitarshop123@rabbitmq:5672"

  # Orders
  RABBITMQ_HOST: "rabbitmq"
  RABBITMQ_PORT: "5672"
  RABBITMQ_USER: "guitarshop"

  # UI — internal service URLs
  CATALOG_SERVICE_URL:  "http://catalog:8080"
  CART_SERVICE_URL:     "http://cart:8080"
  CHECKOUT_SERVICE_URL: "http://checkout:8080"
  ORDERS_SERVICE_URL:   "http://orders:8080"
```

Apply it:

```bash
kubectl apply -f k8s/configmap.yaml
```

---

## III. Secret — Sensitive Config

Kubernetes requires Secret values to be base64-encoded.

### Encode your values

```bash
echo -n "guitarshop123" | base64
# Output: Z3VpdGFyc2hvcDEyMw==

echo -n "rootpassword" | base64
# Output: cm9vdHBhc3N3b3Jk
```

```yaml
# k8s/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: guitarshop-secret
  namespace: guitarshop
type: Opaque
data:
  DB_PASSWORD:       Z3VpdGFyc2hvcDEyMw==   # guitarshop123
  RABBITMQ_PASSWORD: Z3VpdGFyc2hvcDEyMw==   # guitarshop123
```

Apply it:

```bash
kubectl apply -f k8s/secret.yaml
```

---

## IV. How Pods Consume Them

In each Deployment, `envFrom` loads all ConfigMap and Secret values as
environment variables — just like `-e` flags in Docker:

```yaml
containers:
  - name: cart
    envFrom:
      - configMapRef:
          name: guitarshop-config   # loads all keys from ConfigMap
      - secretRef:
          name: guitarshop-secret   # loads all keys from Secret
```

Inside the running container, these become standard env vars:

```bash
# Inside the cart pod:
echo $REDIS_HOST      # cart-redis
echo $REDIS_PORT      # 6379
echo $DB_PASSWORD     # guitarshop123
```

---

## V. How This Maps to docker-compose.yml

```yaml
# docker-compose.yml                    # Kubernetes equivalent
cart:
  environment:
    REDIS_HOST: cart-redis        →     ConfigMap: REDIS_HOST: "cart-redis"
    REDIS_PORT: "6379"            →     ConfigMap: REDIS_PORT: "6379"
    DB_PASSWORD: guitarshop123    →     Secret:    DB_PASSWORD: Z3VpdGFyc2hvcDEyMw==
```

---

## VI. Verify

```bash
# View ConfigMap contents
kubectl get configmap guitarshop-config -n guitarshop -o yaml

# View Secret (values will be base64-encoded)
kubectl get secret guitarshop-secret -n guitarshop -o yaml

# Decode a secret value
kubectl get secret guitarshop-secret -n guitarshop \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 --decode
```

---

## VII. Production Note — AWS Secrets Manager

For a real production environment, avoid storing secrets in Kubernetes YAML files.
Use the AWS Secrets Manager CSI driver to inject secrets directly from AWS:

```
AWS Secrets Manager → CSI Driver → mounted as env vars in pod
```

This keeps secrets out of your Git repository entirely.
