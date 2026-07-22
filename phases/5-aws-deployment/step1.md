# Step 1: terraform-app-layer

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-aws-deployment/step0.md`

## 작업
ECS Fargate API와 OCR worker, ALB, WAF, IAM 최소 권한, CloudWatch log/metric을 정의한다. API와 worker가 S3·SQS에 필요한 권한만 갖도록 분리한다.

## 인수 기준
```bash
terraform fmt -check
terraform validate
```

## 하지 말 것
- ECS task에 관리자 IAM 권한을 부여하지 말 것. 이유: 서비스 침해 범위를 최소화해야 한다.
