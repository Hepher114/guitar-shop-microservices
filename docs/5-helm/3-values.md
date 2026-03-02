# Helm Values

`values.yaml` is the single source of truth for all configurable settings in
the chart. Every environment-specific value lives here — nothing is hardcoded
in the templates.

---

## I. Default values.yaml

```yaml
# guitarshop/values.yaml

namespace: guitarshop

# ECR registry base URL
registry: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# ── Catalog ───────────────────────────────────────────────────────────────────
catalog:
  tag: latest
  replicas: 1

# ── Cart ──────────────────────────────────────────────────────────────────────
cart:
  tag: latest
  replicas: 1

# ── Checkout ──────────────────────────────────────────────────────────────────
checkout:
  tag: latest
  replicas: 1

# ── Orders ────────────────────────────────────────────────────────────────────
orders:
  tag: latest
  replicas: 1

# ── UI ────────────────────────────────────────────────────────────────────────
ui:
  tag: latest
  replicas: 1

# ── Database ──────────────────────────────────────────────────────────────────
db:
  password: guitarshop123

# ── RabbitMQ ──────────────────────────────────────────────────────────────────
rabbitmq:
  password: guitarshop123
```

---

## II. Environment-Specific Overrides

Create a separate values file for each environment. Only override what changes:

### values-dev.yaml
```yaml
# Dev — single replica, latest tag
catalog:
  replicas: 1
  tag: latest

cart:
  replicas: 1
  tag: latest
```

### values-prod.yaml
```yaml
# Prod — more replicas, pinned image tags
catalog:
  replicas: 3
  tag: v1.2.0

cart:
  replicas: 3
  tag: v1.2.0

checkout:
  replicas: 2
  tag: v1.2.0

orders:
  replicas: 2
  tag: v1.2.0

ui:
  replicas: 2
  tag: v1.2.0

db:
  password: <strong-prod-password>

rabbitmq:
  password: <strong-prod-password>
```

---

## III. How Values Map to Templates

```
values.yaml                  template
───────────────────────────  ─────────────────────────────────────────────
cart:                        image: {{ .Values.registry }}/guitarshop/cart
  tag: v1.2.0          →           :{{ .Values.cart.tag }}
  replicas: 3          →     replicas: {{ .Values.cart.replicas }}

db:
  password: secret123  →     {{ .Values.db.password | b64enc | quote }}
```

---

## IV. Override Values at Deploy Time

You can also override individual values on the command line without a file:

```bash
# Override a single value
helm upgrade --install guitarshop ./guitarshop \
  --set cart.tag=v1.3.0

# Override multiple values
helm upgrade --install guitarshop ./guitarshop \
  --set cart.tag=v1.3.0 \
  --set cart.replicas=2 \
  --set ui.replicas=2
```

---

## V. Value Precedence (highest to lowest)

```
1. --set flags on command line          (highest priority)
2. -f values-prod.yaml                 (environment file)
3. values.yaml in the chart            (defaults)
```

---

## VI. CI/CD Integration

In a CI/CD pipeline, the image tag is passed dynamically using `--set`:

```bash
# GitHub Actions — set tag to the Git commit SHA
helm upgrade --install guitarshop ./guitarshop \
  -f values-prod.yaml \
  --set catalog.tag=${{ github.sha }} \
  --set cart.tag=${{ github.sha }} \
  --set checkout.tag=${{ github.sha }} \
  --set orders.tag=${{ github.sha }} \
  --set ui.tag=${{ github.sha }}
```

Every deployment is pinned to a specific commit — fully traceable and rollbackable.
