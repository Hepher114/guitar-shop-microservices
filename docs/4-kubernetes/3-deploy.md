# Deploy to EKS

With the cluster running, images in ECR, and manifests written — this section
walks through deploying the full guitarshop application to Kubernetes.

---

## I. Prerequisites

- EKS cluster running (`kubectl get nodes` shows Ready)
- All 5 images pushed to ECR
- `kubectl` configured to point to the cluster
- Manifests written (namespace, configmap, secret, deployments, services, ingress)

---

## II. Deployment Order

Deploy in this order to respect dependencies:

```
1. Namespace
2. ConfigMap + Secret
3. Infrastructure (databases, Redis, RabbitMQ)
4. Microservices (catalog, cart, checkout, orders)
5. UI (last — depends on all services)
6. Ingress (exposes UI to internet)
```

---

## III. Step 1 — Create the Namespace

```bash
kubectl apply -f k8s/namespace.yaml
kubectl get namespace guitarshop
```

---

## IV. Step 2 — Apply ConfigMap and Secret

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
```

Verify:

```bash
kubectl get configmap -n guitarshop
kubectl get secret -n guitarshop
```

---

## V. Step 3 — Deploy Infrastructure

For this project, the databases, Redis, and RabbitMQ run as Kubernetes Deployments.

> In production you would replace these with AWS managed services:
> - MySQL → Amazon RDS
> - PostgreSQL → Amazon RDS
> - Redis → Amazon ElastiCache
> - RabbitMQ → Amazon MQ

```bash
kubectl apply -f k8s/catalog-db/
kubectl apply -f k8s/checkout-db/
kubectl apply -f k8s/orders-db/
kubectl apply -f k8s/redis/
kubectl apply -f k8s/rabbitmq/
```

Wait for all infrastructure pods to be ready:

```bash
kubectl wait --for=condition=ready pod -l app=catalog-db -n guitarshop --timeout=120s
kubectl wait --for=condition=ready pod -l app=cart-redis -n guitarshop --timeout=120s
kubectl wait --for=condition=ready pod -l app=rabbitmq   -n guitarshop --timeout=120s
```

---

## VI. Step 4 — Deploy the Microservices

```bash
kubectl apply -f k8s/catalog/
kubectl apply -f k8s/cart/
kubectl apply -f k8s/checkout/
kubectl apply -f k8s/orders/
```

Wait for all microservices to be ready:

```bash
kubectl wait --for=condition=ready pod -l app=catalog  -n guitarshop --timeout=120s
kubectl wait --for=condition=ready pod -l app=cart     -n guitarshop --timeout=120s
kubectl wait --for=condition=ready pod -l app=checkout -n guitarshop --timeout=120s
kubectl wait --for=condition=ready pod -l app=orders   -n guitarshop --timeout=120s
```

---

## VII. Step 5 — Deploy the UI

```bash
kubectl apply -f k8s/ui/
kubectl wait --for=condition=ready pod -l app=ui -n guitarshop --timeout=120s
```

---

## VIII. Step 6 — Apply the Ingress

```bash
kubectl apply -f k8s/ingress.yaml
```

Get the ALB public URL (takes 2-3 minutes to provision):

```bash
kubectl get ingress -n guitarshop
```

---

## IX. Apply Everything at Once

Once you're confident in the order, apply all manifests together:

```bash
kubectl apply -f k8s/
```

---

## X. Verify the Full Deployment

```bash
# All pods running
kubectl get pods -n guitarshop

# All services created
kubectl get services -n guitarshop

# Ingress with ALB address
kubectl get ingress -n guitarshop
```

Expected pod output:

```
NAME                        READY   STATUS    RESTARTS   AGE
catalog-xxx                 1/1     Running   0          2m
cart-xxx                    1/1     Running   0          2m
checkout-xxx                1/1     Running   0          2m
orders-xxx                  1/1     Running   0          2m
ui-xxx                      1/1     Running   0          1m
catalog-db-xxx              1/1     Running   0          5m
checkout-db-xxx             1/1     Running   0          5m
orders-db-xxx               1/1     Running   0          5m
cart-redis-xxx              1/1     Running   0          5m
rabbitmq-xxx                1/1     Running   0          5m
```

---

## XI. Useful Debug Commands

```bash
# View logs for a pod
kubectl logs -f deployment/cart -n guitarshop

# Describe a pod (shows events and errors)
kubectl describe pod <pod-name> -n guitarshop

# Open a shell inside a running pod
kubectl exec -it deployment/cart -n guitarshop -- sh

# View recent events in the namespace
kubectl get events -n guitarshop --sort-by='.lastTimestamp'
```

---

## XII. Update a Deployment (rolling update)

When you push a new image to ECR and want to deploy it:

```bash
# Update the image tag
kubectl set image deployment/cart \
  cart=<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/guitarshop/cart:v2 \
  -n guitarshop

# Watch the rolling update
kubectl rollout status deployment/cart -n guitarshop

# Roll back if something goes wrong
kubectl rollout undo deployment/cart -n guitarshop
```

Kubernetes performs a **rolling update** by default — it starts new pods before
stopping old ones, so the service stays available during the update.

---

## XIII. Cleanup

```bash
# Delete all guitarshop resources
kubectl delete namespace guitarshop

# This deletes everything in the namespace:
# pods, services, deployments, configmaps, secrets, ingress
```
