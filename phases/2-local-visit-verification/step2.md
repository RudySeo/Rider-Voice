# Step 2: local-evidence-storage

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/domain/`

## 작업

`visit/application`에 증빙 저장 port를 정의하고 `visit/infrastructure`에 로컬 filesystem adapter를 구현한다. 저장 root는 환경변수로 주입하며 기본값은 gitignored `.local/evidence`다. 서버가 생성한 key만 허용하고 MIME, 최대 크기, 경로 탈출 방지, 짧은 업로드 ticket와 삭제 동작을 검증한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 임시 디렉터리 테스트로 저장·조회·삭제·경로 탈출 거부를 검증한다.
2. index step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- S3 SDK나 Docker object storage를 추가하지 말 것. 이유: 현재는 로컬 filesystem adapter만 사용한다.
- 사용자 입력 파일 경로를 그대로 사용하지 말 것. 이유: 임의 파일 접근 위험이 있다.
- 기존 test를 깨뜨리지 말 것.
