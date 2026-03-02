# AWS Load Balancer & Ingress

An Ingress exposes the UI service to the internet. In EKS, this is handled by
the AWS Load Balancer Controller which automatically provisions an Application
Load Balancer (ALB) in AWS when you create a Kubernetes Ingress resource.

---

## I. What is an Ingress

Without Ingress:
```
Internet → ??? → UI pod (no way in)
```

With Ingress:
```
Internet → ALB → Ingress → UI Service → UI pods
```

The ALB has a public DNS name that users hit in their browser. It forwards
traffic into the cluster to the UI service.

---

## II. Install the AWS Load Balancer Controller

The controller watches for Ingress resources and creates ALBs automatically.

### Step 1 — Create an IAM policy for the controller

```bash
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json
```

### Step 2 — Create an IAM role for the controller using eksctl

```bash
eksctl create iamserviceaccount \
  --cluster=guitarshop \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name AmazonEKSLoadBalancerControllerRole \
  --attach-policy-arn=arn:aws:iam::<ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve
```

### Step 3 — Install the controller with Helm

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=guitarshop \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4 — Verify the controller is running

```bash
kubectl get deployment -n kube-system aws-load-balancer-controller
```

---

## III. Create the Ingress Resource

Save as `k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: guitarshop-ingress
  namespace: guitarshop
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

Apply it:

```bash
kubectl apply -f k8s/ingress.yaml
```

---

## IV. Get the ALB DNS Name

```bash
kubectl get ingress -n guitarshop
```

Expected output:

```
NAME                  CLASS   HOSTS   ADDRESS                                       PORTS
guitarshop-ingress    alb     *       k8s-xxxx.us-east-1.elb.amazonaws.com          80
```

The `ADDRESS` column is your public URL. Open it in a browser:

```
http://k8s-xxxx.us-east-1.elb.amazonaws.com
```

> It takes 2-3 minutes for the ALB to provision after applying the Ingress.

---

## V. How Traffic Flows

```
User browser
     │
     ▼
ALB (public DNS: k8s-xxxx.us-east-1.elb.amazonaws.com)
     │
     ▼
Ingress (guitarshop-ingress)
     │
     ▼
UI Service (ClusterIP: ui:8080)
     │
     ▼
UI Pods (Spring Boot / Thymeleaf)
     │
     ├── calls catalog:8080
     ├── calls cart:8080
     ├── calls checkout:8080
     └── calls orders:8080
```

---

## VI. Annotations Explained

| Annotation                                      | Value             | Meaning                                  |
|-------------------------------------------------|-------------------|------------------------------------------|
| `kubernetes.io/ingress.class`                   | `alb`             | Use AWS ALB (not nginx)                  |
| `alb.ingress.kubernetes.io/scheme`              | `internet-facing` | ALB is publicly accessible               |
| `alb.ingress.kubernetes.io/target-type`         | `ip`              | Route directly to pod IPs (not node IPs) |
