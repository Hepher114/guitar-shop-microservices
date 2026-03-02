# Create an EKS Cluster

Amazon EKS (Elastic Kubernetes Service) is AWS's managed Kubernetes service.
AWS handles the control plane (the Kubernetes master nodes) — you only manage
the worker nodes where your containers run.

---

## I. Prerequisites

- AWS CLI installed and configured (`aws configure`)
- IAM user with sufficient permissions
- EC2 instance or local machine to run commands from

---

## II. What is EKS vs Docker Compose

| Docker Compose              | EKS (Kubernetes)                        |
|-----------------------------|-----------------------------------------|
| Runs on one machine         | Runs across multiple machines (nodes)   |
| Manual scaling              | Auto-scaling built in                   |
| No self-healing             | Restarts failed containers automatically|
| `docker-compose.yml`        | Kubernetes manifest files (YAML)        |
| Good for development        | Production-grade                        |

---

## III. Install kubectl

`kubectl` is the CLI tool to interact with any Kubernetes cluster.

```bash
curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/
kubectl version --client
```

---

## IV. Install eksctl

`eksctl` is the official CLI tool for creating and managing EKS clusters.
It provisions all required AWS resources automatically.

```bash
curl --silent --location \
  "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz" \
  | tar xz -C /tmp

sudo mv /tmp/eksctl /usr/local/bin/
eksctl version
```

---

## V. IAM Permissions Required

The IAM user running eksctl needs broad permissions. Attach these managed policies:

| Policy                          | Purpose                              |
|---------------------------------|--------------------------------------|
| `AmazonEKSClusterPolicy`        | Create and manage EKS clusters       |
| `AmazonEKSWorkerNodePolicy`     | Worker nodes join the cluster        |
| `AmazonEC2ContainerRegistryReadOnly` | Nodes pull images from ECR      |
| `AmazonVPCFullAccess`           | Create VPC, subnets, security groups |
| `IAMFullAccess`                 | Create roles for the cluster         |

> For a portfolio/learning environment you can attach `AdministratorAccess`
> to simplify setup. Never do this in production.

---

## VI. Create the EKS Cluster

This single command creates the full cluster — VPC, subnets, node group, and
all required IAM roles. It takes **15-20 minutes**.

```bash
eksctl create cluster \
  --name guitarshop \
  --region us-east-1 \
  --nodegroup-name guitarshop-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

| Flag            | Value              | Purpose                                    |
|-----------------|--------------------|--------------------------------------------|
| `--name`        | `guitarshop`       | Cluster name                               |
| `--region`      | `us-east-1`        | AWS region                                 |
| `--node-type`   | `t3.medium`        | EC2 instance type for worker nodes         |
| `--nodes`       | `2`                | Start with 2 worker nodes                  |
| `--nodes-min`   | `1`                | Auto-scale down to 1 node minimum          |
| `--nodes-max`   | `3`                | Auto-scale up to 3 nodes maximum           |
| `--managed`     |                    | AWS manages node updates and patching      |

---

## VII. What eksctl Creates

```
AWS Resources created automatically:

VPC
├── 2 public subnets
├── 2 private subnets
├── Internet Gateway
└── NAT Gateway

EKS Control Plane (managed by AWS)
└── API server, scheduler, etcd

Node Group (your worker nodes)
├── EC2 instance 1 (t3.medium)
└── EC2 instance 2 (t3.medium)

IAM Roles
├── EKS cluster role
└── Node group role

Security Groups
├── Cluster security group
└── Node security group
```

---

## VIII. Configure kubectl to Connect to the Cluster

After the cluster is created, configure `kubectl` to point to it:

```bash
aws eks update-kubeconfig \
  --region us-east-1 \
  --name guitarshop
```

This updates `~/.kube/config` with the cluster credentials.

---

## IX. Verify the Cluster

Check the cluster is running:

```bash
kubectl cluster-info
```

Check the worker nodes are ready:

```bash
kubectl get nodes
```

Expected output:

```
NAME                          STATUS   ROLES    AGE   VERSION
ip-192-168-x-x.ec2.internal   Ready    <none>   2m    v1.28.x
ip-192-168-x-x.ec2.internal   Ready    <none>   2m    v1.28.x
```

Both nodes should show `Ready`.

---

## X. Allow Nodes to Pull from ECR

Worker nodes need permission to pull images from your ECR repositories.
Attach the ECR read policy to the node group role:

```bash
# Get the node group role name
NODE_ROLE=$(aws eks describe-nodegroup \
  --cluster-name guitarshop \
  --nodegroup-name guitarshop-nodes \
  --query "nodegroup.nodeRole" \
  --output text | awk -F'/' '{print $NF}')

# Attach ECR read policy
aws iam attach-role-policy \
  --role-name $NODE_ROLE \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

---

## XI. Cluster Architecture

```
                        Internet
                           │
                           ▼
                    ┌─────────────┐
                    │     VPC     │
                    │             │
                    │  ┌────────────────────────┐
                    │  │   EKS Control Plane    │  ← managed by AWS
                    │  │  (API server, etcd...) │
                    │  └───────────┬────────────┘
                    │              │
                    │  ┌───────────▼────────────┐
                    │  │      Node Group        │
                    │  │  ┌────────┐ ┌────────┐ │
                    │  │  │ Node 1 │ │ Node 2 │ │  ← your EC2 instances
                    │  │  │ (pods) │ │ (pods) │ │
                    │  │  └────────┘ └────────┘ │
                    │  └────────────────────────┘
                    └─────────────────────────────┘
                                   │
                              ECR (images)
```

---

## XII. Useful kubectl Commands

```bash
# View all running pods
kubectl get pods -A

# View all services
kubectl get services -A

# View cluster nodes
kubectl get nodes

# Describe a node for details
kubectl describe node <node-name>

# View cluster events
kubectl get events -A
```

---

## XIII. Cleanup (when done)

Deletes the cluster and all AWS resources it created:

```bash
eksctl delete cluster --name guitarshop --region us-east-1
```

> This also deletes the VPC, subnets, node group, and IAM roles.
> Your ECR images are NOT deleted — they must be removed separately.
