# Step 1: s3-upload-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step0.md`

## 작업
비공개 S3 presigned upload URL 발급 port와 adapter를 구현한다. MIME, 크기, key prefix, 만료 시간을 서버에서 강제하고 client-provided key를 신뢰하지 않는다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- public bucket이나 영구 public URL을 만들지 말 것. 이유: 증빙 개인정보가 노출된다.
