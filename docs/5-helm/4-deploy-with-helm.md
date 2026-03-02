# Deploy with Helm

With the chart and values ready, this section covers deploying, upgrading,
rolling back, and managing the guitarshop application using Helm.

---

## I. Prerequisites

- EKS cluster running
- `kubectl` configured
- `helm` installed
- Images pushed to ECR
- Chart created (`guitarshop/` folder with Chart.yaml, values.yaml, templates/)

---

## II. Lint the Chart

Check the chart for errors before deploying:

```bash
helm lint ./guitarshop
```

Expected output: `1 chart(s) linted, 0 chart(s) failed`

---

## III. Preview Before Deploying

Render all templates and print to stdout without applying anything:

```bash
helm template guitarshop ./guitarshop -f guitarshop/values.yaml
```

---

## IV. Install (First Deploy)

```bash
helm upgrade --install guitarshop ./guitarshop \
  -f guitarshop/values.yaml \
  --namespace guitarshop \
  --create-namespace
```

| Flag                | Purpose                                            |
|---------------------|----------------------------------------------------|
| `upgrade --install` | Install if not exists, upgrade if it does          |
| `-f values.yaml`    | Use these values                                   |
| `--namespace`       | Deploy into this namespace                         |
| `--create-namespace`| Create the namespace if it doesn't exist           |

---

## V. Verify the Deployment

```bash
# Check Helm release status
helm list -n guitarshop

# Check all pods are running
kubectl get pods -n guitarshop

# Check ingress has an ALB address
kubectl get ingress -n guitarshop
```

Expected `helm list` output:

```
NAME        NAMESPACE   REVISION  STATUS    CHART             APP VERSION
guitarshop  guitarshop  1         deployed  guitarshop-1.0.0  1.0.0
```

---

## VI. Upgrade (New Image or Config Change)

When you push new images to ECR and want to deploy:

```bash
helm upgrade guitarshop ./guitarshop \
  -f guitarshop/values.yaml \
  --set cart.tag=v1.1.0 \
  --set ui.tag=v1.1.0 \
  --namespace guitarshop
```

Helm performs a rolling update — new pods start before old ones stop.
Watch the rollout:

```bash
kubectl rollout status deployment/cart -n guitarshop
```

---

## VII. View Release History

```bash
helm history guitarshop -n guitarshop
```

Output:

```
REVISION  STATUS      CHART              DESCRIPTION
1         superseded  guitarshop-1.0.0   Install complete
2         deployed    guitarshop-1.0.0   Upgrade complete
```

---

## VIII. Roll Back

If something breaks after an upgrade:

```bash
# Roll back to the previous revision
helm rollback guitarshop -n guitarshop

# Roll back to a specific revision
helm rollback guitarshop 1 -n guitarshop
```

Kubernetes will roll back all deployments to the state of that revision.

---

## IX. Deploy to Different Environments

```bash
# Development
helm upgrade --install guitarshop-dev ./guitarshop \
  -f guitarshop/values-dev.yaml \
  --namespace guitarshop-dev \
  --create-namespace

# Production
helm upgrade --install guitarshop-prod ./guitarshop \
  -f guitarshop/values-prod.yaml \
  --namespace guitarshop-prod \
  --create-namespace
```

Both run in the same cluster, isolated in separate namespaces.

---

## X. Full Deployment Flow

```
Developer pushes code
        │
        ▼
GitHub Actions builds images
        │
        ▼
Images pushed to ECR with new tag (git SHA)
        │
        ▼
helm upgrade --install guitarshop ./guitarshop
  --set cart.tag=abc123
  --set ui.tag=abc123
        │
        ▼
Kubernetes performs rolling update
        │
        ▼
New pods replace old pods (zero downtime)
        │
        ▼
helm history shows new revision
```

---

## XI. Uninstall

Remove all Helm-managed resources from the cluster:

```bash
helm uninstall guitarshop -n guitarshop
```

> This removes all Kubernetes resources created by the chart.
> The namespace itself is not deleted — remove it manually if needed:

```bash
kubectl delete namespace guitarshop
```
