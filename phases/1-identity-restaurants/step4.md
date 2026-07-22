# Step 4: kakao-local-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/step3.md`

## 작업
카카오 로컬 키워드 검색 adapter와 장소 응답 정규화를 구현한다. API key, timeout, rate limit, 빈 결과를 내부 오류로 매핑하고 좌표와 반경 계산 port를 제공한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 카카오 API를 Controller에서 직접 호출하지 말 것. 이유: 외부 provider 경계를 보존해야 한다.
