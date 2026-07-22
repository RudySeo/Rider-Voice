# Step 0: terraform-data-layer

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`

## 작업
Terraform으로 RDS PostgreSQL, S3 비공개 bucket, KMS, SQS와 DLQ, Secrets Manager를 정의한다. 증빙 bucket public access 차단과 삭제 lifecycle을 코드로 검증한다.

## 인수 기준
```bash
terraform fmt -check
terraform validate
```

## 하지 말 것
- 실제 운영 secret을 repository에 넣지 말 것. 이유: credential 유출 위험이 있다.
