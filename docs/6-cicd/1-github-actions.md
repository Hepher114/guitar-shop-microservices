# CI/CD Pipeline with GitHub Actions

GitHub Actions automates the entire build and deploy process. Every time code
is pushed to the `main` branch, the pipeline builds all 5 images, pushes them
to ECR, and deploys to EKS using Helm — with no manual steps.

---

## I. What the Pipeline Does

```
Push to main branch
        │
        ▼
┌───────────────────┐
│   CI — Build      │  Run tests (if any)
│                   │  Build 5 Docker images
│                   │  Push to ECR with git SHA tag
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│   CD — Deploy     │  Configure kubectl
│                   │  helm upgrade --install
│                   │  Verify rollout
└───────────────────┘
```

---

## II. GitHub Secrets Required

In your GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**

| Secret Name             | Value                                         |
|-------------------------|-----------------------------------------------|
| `AWS_ACCESS_KEY_ID`     | IAM user access key                           |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key                           |
| `AWS_ACCOUNT_ID`        | Your 12-digit AWS account ID                  |
| `AWS_REGION`            | `us-east-1`                                   |
| `EKS_CLUSTER_NAME`      | `guitarshop`                                  |

---

## III. IAM Permissions for the Pipeline User

The IAM user whose credentials are in GitHub Secrets needs:

| Permission              | Purpose                          |
|-------------------------|----------------------------------|
| `AmazonECR*`            | Push images to ECR               |
| `eks:DescribeCluster`   | Connect to EKS                   |
| `eks:ListClusters`      | List clusters                    |

---

## IV. Pipeline File

Save as `.github/workflows/deploy.yml` in the root of your repository:

```yaml
name: Build and Deploy

on:
  push:
    branches:
      - main

env:
  AWS_REGION:       ${{ secrets.AWS_REGION }}
  ECR_REGISTRY:     ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ secrets.AWS_REGION }}.amazonaws.com
  EKS_CLUSTER_NAME: ${{ secrets.EKS_CLUSTER_NAME }}
  IMAGE_TAG:        ${{ github.sha }}

jobs:

  # ── Build & Push ────────────────────────────────────────────────────────────
  build:
    name: Build and Push Images
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - name: Login to Amazon ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push catalog
        run: |
          docker build -t $ECR_REGISTRY/guitarshop/catalog:$IMAGE_TAG \
            ./microservices/catalog
          docker push $ECR_REGISTRY/guitarshop/catalog:$IMAGE_TAG

      - name: Build and push cart
        run: |
          docker build -t $ECR_REGISTRY/guitarshop/cart:$IMAGE_TAG \
            ./microservices/cart
          docker push $ECR_REGISTRY/guitarshop/cart:$IMAGE_TAG

      - name: Build and push checkout
        run: |
          docker build -t $ECR_REGISTRY/guitarshop/checkout:$IMAGE_TAG \
            ./microservices/checkout
          docker push $ECR_REGISTRY/guitarshop/checkout:$IMAGE_TAG

      - name: Build and push orders
        run: |
          docker build -t $ECR_REGISTRY/guitarshop/orders:$IMAGE_TAG \
            ./microservices/orders
          docker push $ECR_REGISTRY/guitarshop/orders:$IMAGE_TAG

      - name: Build and push ui
        run: |
          docker build -t $ECR_REGISTRY/guitarshop/ui:$IMAGE_TAG \
            ./microservices/ui
          docker push $ECR_REGISTRY/guitarshop/ui:$IMAGE_TAG

  # ── Deploy ──────────────────────────────────────────────────────────────────
  deploy:
    name: Deploy to EKS
    runs-on: ubuntu-latest
    needs: build

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id:     ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region:            ${{ secrets.AWS_REGION }}

      - name: Configure kubectl
        run: |
          aws eks update-kubeconfig \
            --region $AWS_REGION \
            --name $EKS_CLUSTER_NAME

      - name: Install Helm
        uses: azure/setup-helm@v4

      - name: Deploy with Helm
        run: |
          helm upgrade --install guitarshop ./helm/guitarshop \
            -f ./helm/guitarshop/values.yaml \
            --namespace guitarshop \
            --create-namespace \
            --set catalog.tag=$IMAGE_TAG \
            --set cart.tag=$IMAGE_TAG \
            --set checkout.tag=$IMAGE_TAG \
            --set orders.tag=$IMAGE_TAG \
            --set ui.tag=$IMAGE_TAG \
            --set registry=$ECR_REGISTRY

      - name: Verify rollout
        run: |
          kubectl rollout status deployment/catalog  -n guitarshop --timeout=120s
          kubectl rollout status deployment/cart     -n guitarshop --timeout=120s
          kubectl rollout status deployment/checkout -n guitarshop --timeout=120s
          kubectl rollout status deployment/orders   -n guitarshop --timeout=120s
          kubectl rollout status deployment/ui       -n guitarshop --timeout=120s
```

---

## V. Pipeline Breakdown

### Trigger
```yaml
on:
  push:
    branches:
      - main
```
Runs only when code is pushed to `main`. Pull requests do not trigger a deploy.

### IMAGE_TAG
```yaml
IMAGE_TAG: ${{ github.sha }}
```
Every build is tagged with the Git commit SHA (e.g. `abc1234`). This makes
every deployment fully traceable — you always know exactly what code is running.

### needs: build
```yaml
deploy:
  needs: build
```
The deploy job only runs if the build job succeeds. If any image fails to build
or push, the deploy never happens.

### helm upgrade --install
```yaml
helm upgrade --install guitarshop ./helm/guitarshop \
  --set catalog.tag=$IMAGE_TAG
```
Installs on first run, upgrades on subsequent runs. Passes the new image tag
dynamically — no manual file edits needed.

---

## VI. What Happens on Each Push

```
git push origin main
        │
        ▼
GitHub Actions triggers
        │
        ▼
Job 1: build (parallel image builds)
  ├── checkout code
  ├── authenticate to ECR
  ├── build catalog image → push as :abc1234
  ├── build cart image    → push as :abc1234
  ├── build checkout image → push as :abc1234
  ├── build orders image  → push as :abc1234
  └── build ui image      → push as :abc1234
        │
        ▼ (only if build succeeds)
Job 2: deploy
  ├── configure kubectl → connect to EKS
  ├── helm upgrade --install --set *.tag=abc1234
  ├── Kubernetes performs rolling update
  └── verify all deployments are healthy
```

---

## VII. Monitoring the Pipeline

In your GitHub repository:

```
Repository → Actions → Build and Deploy → latest run
```

Each step shows logs in real time. If a step fails, the pipeline stops and
you see exactly which command failed and why.

---

## VIII. Roll Back a Bad Deploy

If the deployment causes issues, roll back using Helm:

```bash
# On your local machine or EC2
helm rollback guitarshop -n guitarshop
```

Or trigger a new deploy by reverting the commit:

```bash
git revert HEAD
git push origin main
# Pipeline runs again with the reverted code
```
