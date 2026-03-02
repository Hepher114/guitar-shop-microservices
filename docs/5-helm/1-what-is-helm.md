# What is Helm

Helm is the **package manager for Kubernetes**. Just like `apt` installs software
on Ubuntu or `npm` installs packages in Node.js, Helm installs and manages
applications on Kubernetes.

---

## I. The Problem Helm Solves

Without Helm, deploying guitarshop to Kubernetes means managing many separate files:

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── catalog/deployment.yaml
├── catalog/service.yaml
├── cart/deployment.yaml
├── cart/service.yaml
... 15+ files
```

Problems with raw manifests:
- Hard to deploy to different environments (dev, staging, prod)
- No easy way to version the deployment
- No rollback mechanism at the application level
- Values like image tags are hardcoded in multiple places

---

## II. What Helm Provides

| Feature            | Without Helm                        | With Helm                             |
|--------------------|-------------------------------------|---------------------------------------|
| Deployment         | `kubectl apply -f k8s/`             | `helm install guitarshop ./chart`     |
| Update             | Edit YAML files + kubectl apply     | `helm upgrade guitarshop ./chart`     |
| Rollback           | Manually revert YAML + kubectl apply| `helm rollback guitarshop 1`          |
| Environments       | Duplicate files per environment     | One chart + one `values.yaml` per env |
| Versioning         | No built-in versioning              | Every install is a tracked release    |

---

## III. Core Concepts

### Chart
A **chart** is a Helm package — a folder containing all the Kubernetes manifests
for an application, but with variables instead of hardcoded values.

```
guitarshop/              ← this is a chart
├── Chart.yaml           ← chart metadata (name, version)
├── values.yaml          ← default variable values
└── templates/           ← manifest templates with {{ variables }}
    ├── deployment.yaml
    ├── service.yaml
    └── ...
```

### Values
**Values** are the variables that get injected into the templates at deploy time.
You can override them per environment without changing the templates.

```yaml
# values.yaml (defaults)
catalog:
  image: guitarshop/catalog
  tag: latest
  replicas: 1

# values-prod.yaml (production overrides)
catalog:
  tag: v1.2.0
  replicas: 3
```

### Release
A **release** is a specific instance of a chart installed in the cluster.
You can install the same chart multiple times with different names and values.

```bash
helm install guitarshop-dev  ./chart -f values-dev.yaml
helm install guitarshop-prod ./chart -f values-prod.yaml
```

### Repository
A **repository** is a collection of charts — like npm registry but for Helm charts.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install my-redis bitnami/redis
```

---

## IV. Install Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
helm version
```

---

## V. Helm vs Raw Kubernetes Manifests

```
Raw Manifests                    Helm
─────────────────────────────    ─────────────────────────────
kubectl apply -f k8s/            helm install guitarshop ./chart
kubectl apply -f k8s/            helm upgrade guitarshop ./chart
manually edit YAML files         helm rollback guitarshop 1
copy files per environment       helm install -f values-prod.yaml
no release history               helm history guitarshop
```

---

## VI. Common Helm Commands

```bash
# Install a chart
helm install <release-name> <chart-path>

# Upgrade an existing release
helm upgrade <release-name> <chart-path>

# Install or upgrade (whichever is needed)
helm upgrade --install <release-name> <chart-path>

# List all releases
helm list

# View release history
helm history <release-name>

# Roll back to a previous version
helm rollback <release-name> <revision>

# Uninstall a release
helm uninstall <release-name>

# Preview what will be deployed without applying
helm template <release-name> <chart-path>
helm install --dry-run <release-name> <chart-path>
```
