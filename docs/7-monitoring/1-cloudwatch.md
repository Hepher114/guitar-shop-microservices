# Monitoring with CloudWatch Container Insights

CloudWatch Container Insights collects metrics and logs from your EKS cluster
and displays them in the AWS Console. It gives you visibility into CPU, memory,
pod health, and application logs without running extra infrastructure.

---

## I. What Container Insights Provides

| Feature          | What you see                                          |
|------------------|-------------------------------------------------------|
| Cluster metrics  | CPU and memory usage across all nodes                 |
| Pod metrics      | CPU, memory, and restart count per pod                |
| Container logs   | stdout/stderr from every container                    |
| Node health      | Disk, network, and instance-level metrics             |
| Alarms           | Get notified when CPU spikes or pods crash            |

---

## II. Install the CloudWatch Agent on EKS

### Step 1 — Create the namespace

```bash
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/cloudwatch-namespace.yaml
```

### Step 2 — Create a service account with IAM permissions

```bash
eksctl create iamserviceaccount \
  --name cloudwatch-agent \
  --namespace amazon-cloudwatch \
  --cluster guitarshop \
  --attach-policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy \
  --approve \
  --override-existing-serviceaccounts
```

### Step 3 — Deploy the CloudWatch agent (DaemonSet)

A DaemonSet runs one pod on every node automatically.

```bash
ClusterName=guitarshop
RegionName=us-east-1
FluentBitHttpPort='2020'
FluentBitReadFromHead='Off'

curl https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml \
  | sed "s/{{cluster_name}}/${ClusterName}/;s/{{region_name}}/${RegionName}/;s/{{http_server_port}}/${FluentBitHttpPort}/;s/{{read_from_head}}/${FluentBitReadFromHead}/" \
  | kubectl apply -f -
```

### Step 4 — Verify agents are running

```bash
kubectl get pods -n amazon-cloudwatch
```

You should see one `cloudwatch-agent` pod and one `fluent-bit` pod per node.

---

## III. View Metrics in AWS Console

```
AWS Console → CloudWatch → Container Insights → Performance monitoring
```

Select:
- **EKS Clusters** — cluster-level overview
- **EKS Nodes** — per-node CPU and memory
- **EKS Pods** — per-pod metrics (filter by `namespace: guitarshop`)

---

## IV. View Logs in CloudWatch

All container logs are automatically sent to CloudWatch Log Groups:

| Log Group                                       | Contains                        |
|-------------------------------------------------|---------------------------------|
| `/aws/containerinsights/guitarshop/application` | App logs from all pods          |
| `/aws/containerinsights/guitarshop/host`        | Node-level system logs          |
| `/aws/containerinsights/guitarshop/dataplane`   | Kubernetes control plane logs   |

View in console:

```
AWS Console → CloudWatch → Log groups → /aws/containerinsights/guitarshop/application
```

Filter logs for a specific service:

```
# In CloudWatch Logs Insights
fields @timestamp, @message
| filter kubernetes.container_name = "cart"
| sort @timestamp desc
| limit 50
```

---

## V. Create a CloudWatch Alarm

Get notified when a pod's CPU is too high.

### Using AWS Console

```
CloudWatch → Alarms → Create alarm
→ Select metric → Container Insights → PodName → cpu_utilization
→ Condition: Greater than 80%
→ Action: Send notification to SNS email
```

### Using AWS CLI

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name "guitarshop-cart-cpu-high" \
  --alarm-description "Cart service CPU above 80%" \
  --metric-name pod_cpu_utilization \
  --namespace ContainerInsights \
  --dimensions Name=PodName,Value=cart Name=ClusterName,Value=guitarshop \
  --period 60 \
  --evaluation-periods 3 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --statistic Average
```

---

## VI. Key Metrics to Watch

| Metric                  | What it tells you                          | Alert threshold  |
|-------------------------|--------------------------------------------|------------------|
| `pod_cpu_utilization`   | CPU usage per pod                          | > 80%            |
| `pod_memory_utilization`| Memory usage per pod                       | > 85%            |
| `pod_number_of_running_containers` | Number of running containers  | < expected count |
| `node_cpu_utilization`  | Node-level CPU                             | > 75%            |
| `node_memory_utilization`| Node-level memory                         | > 80%            |

---

## VII. Monitoring Dashboard

Create a CloudWatch dashboard to see all services at once:

```
CloudWatch → Dashboards → Create dashboard → guitarshop
```

Add widgets for:
- CPU per pod (cart, catalog, checkout, orders, ui)
- Memory per pod
- Pod restart count (detects crash loops)
- ALB request count and latency (from ALB metrics)

---

## VIII. Full Observability Stack

```
Application logs (stdout)
        │
        ▼
Fluent Bit (DaemonSet on each node)
        │
        ▼
CloudWatch Logs
        │
        ├── Log Insights (query and filter logs)
        └── Metric Filters (create metrics from log patterns)

Node & Pod metrics
        │
        ▼
CloudWatch Agent (DaemonSet on each node)
        │
        ▼
CloudWatch Metrics → Container Insights Dashboard
        │
        └── CloudWatch Alarms → SNS → Email / Slack
```
