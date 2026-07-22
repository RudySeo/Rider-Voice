# Step 1: kakao-local-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-restaurant-search/step0.md`

## 작업
카카오 로컬 키워드 검색 port와 infrastructure adapter를 구현한다. API key, timeout, rate limit, 빈 결과를 내부 오류로 변환하고 provider DTO를 adapter 밖으로 노출하지 않는다. HTTP stub 테스트를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- Controller에서 카카오 로컬 API를 직접 호출하지 말 것. 이유: 외부 연동 경계를 유지해야 한다.
