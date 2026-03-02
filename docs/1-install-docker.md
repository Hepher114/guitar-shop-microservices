# Create EC2 Instance and Install Docker

Launch an AWS EC2 instance with Amazon Linux 2023 and install Docker to run all demos without Docker Desktop.

---

## I. EC2 Instance Configuration

| Setting        | Value                        |
|----------------|------------------------------|
| AMI            | Amazon Linux 2023            |
| Instance Type  | t3.large                     |
| Storage        | 30 GB                        |
| Security Group | SSH (22), TCP (80, 8080)     |

---

## II. Connect via SSH

```bash
ssh -i your-key.pem ec2-user@<your-ec2-public-ip>
```

---

## III. Install Docker

```bash
sudo dnf update -y
sudo dnf install docker -y
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user
```

> Logout and reconnect to apply group permissions.

---

## IV. Verify Installation

```bash
docker version
docker run hello-world
docker images
docker ps -a
```

---

## V. Cleanup

```bash
docker rm $(docker ps -aq)
docker rmi hello-world
```

> Stop or terminate the EC2 instance when not in use to avoid AWS charges.