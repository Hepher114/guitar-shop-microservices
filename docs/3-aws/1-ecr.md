# Push Images to Amazon ECR

Amazon ECR (Elastic Container Registry) is AWS's private Docker image registry.
Instead of building images on every server, you build once, push to ECR, and
pull from anywhere — EC2, EKS, or any other AWS service.

---

## I. Prerequisites

- AWS account
- EC2 instance running with Docker installed
- AWS CLI installed on the EC2 instance
- IAM user or role with ECR permissions
- All 5 service images built locally

---

## II. Install AWS CLI (if not already installed)

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws --version
```

---

## III. Configure AWS CLI

```bash
aws configure
```

You will be prompted for:

| Field                  | Value                              |
|------------------------|------------------------------------|
| AWS Access Key ID      | Your IAM user access key           |
| AWS Secret Access Key  | Your IAM user secret key           |
| Default region name    | `us-east-1` (or your region)       |
| Default output format  | `json`                             |

> To find your Access Key: AWS Console → IAM → Users → Your user → Security credentials → Create access key

---

## IV. Create ECR Repositories

One repository per service — 5 total.

```bash
aws ecr create-repository --repository-name guitarshop/catalog  --region us-east-1
aws ecr create-repository --repository-name guitarshop/cart     --region us-east-1
aws ecr create-repository --repository-name guitarshop/checkout --region us-east-1
aws ecr create-repository --repository-name guitarshop/orders   --region us-east-1
aws ecr create-repository --repository-name guitarshop/ui       --region us-east-1
```

Each command returns a JSON response with the repository URI. Save the base URI:

```bash
# Your account ID
aws sts get-caller-identity --query Account --output text
```

Your ECR base URI will be:
```
<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
```

---

## V. Authenticate Docker to ECR

Docker needs permission to push to your private registry. Run this once:

```bash
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin \
  <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
```

Expected output: `Login Succeeded`

> This token expires after 12 hours. Re-run if you get an authentication error.

---

## VI. Build the Images

Navigate to the project root and build all 5 images:

```bash
cd guitar-shop-microservices
```

```bash
docker build -t guitarshop/catalog  ./microservices/catalog
docker build -t guitarshop/cart     ./microservices/cart
docker build -t guitarshop/checkout ./microservices/checkout
docker build -t guitarshop/orders   ./microservices/orders
docker build -t guitarshop/ui       ./microservices/ui
```

---

## VII. Tag the Images for ECR

Docker needs the full ECR URI as the image tag before it can push.
Replace `<ACCOUNT_ID>` with your AWS account ID:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
ECR=$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com

docker tag guitarshop/catalog  $ECR/guitarshop/catalog:latest
docker tag guitarshop/cart     $ECR/guitarshop/cart:latest
docker tag guitarshop/checkout $ECR/guitarshop/checkout:latest
docker tag guitarshop/orders   $ECR/guitarshop/orders:latest
docker tag guitarshop/ui       $ECR/guitarshop/ui:latest
```

**What tagging does:**

```
Before:  guitarshop/cart:latest
After:   123456789.dkr.ecr.us-east-1.amazonaws.com/guitarshop/cart:latest
          └── ECR registry ──┘ └── repository ──┘ └── tag ──┘
```

---

## VIII. Push the Images to ECR

```bash
docker push $ECR/guitarshop/catalog
docker push $ECR/guitarshop/cart
docker push $ECR/guitarshop/checkout
docker push $ECR/guitarshop/orders
docker push $ECR/guitarshop/ui
```

---

## IX. Verify

Check that all images are in ECR:

```bash
aws ecr list-images --repository-name guitarshop/catalog  --region us-east-1
aws ecr list-images --repository-name guitarshop/cart     --region us-east-1
aws ecr list-images --repository-name guitarshop/checkout --region us-east-1
aws ecr list-images --repository-name guitarshop/orders   --region us-east-1
aws ecr list-images --repository-name guitarshop/ui       --region us-east-1
```

Or view them in the AWS Console:
```
AWS Console → ECR → Repositories → guitarshop/cart → Images
```

---

## X. IAM Permissions Required

The IAM user or role running these commands needs the following policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:CreateRepository",
        "ecr:DescribeRepositories",
        "ecr:ListImages"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## XI. How ECR Fits Into the Full Pipeline

```
Before ECR:
  EC2 → docker build → docker run (same machine)

After ECR:
  EC2 / GitHub Actions → docker build → docker push → ECR
                                                         │
                                          ┌──────────────┤
                                          ▼              ▼
                                    EC2 (pull)     EKS (pull)
```

Every environment (EC2, EKS, CI/CD) pulls the same image from ECR —
no rebuilding on every server.
