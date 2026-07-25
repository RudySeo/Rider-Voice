# Step 3: kakao-local-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- step 1 external provider ports
- application local `.env` configuration

## 작업

- infrastructure adapter에 카카오 keyword search와 address search를 구현한다.
- keyword 결과는 음식점 후보, address 결과는 표준 주소·지번·좌표로 내부 model에 매핑한다.
- timeout, connection, 429, 4xx, 5xx와 손상 JSON을 provider 비종속 failure로 변환한다.
- REST API key는 `KAKAO_LOCAL_REST_API_KEY`, 없으면 `KAKAO_CLIENT_ID` fallback을 사용한다.
- raw provider DTO는 infrastructure 밖으로 노출하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. local HTTP stub으로 keyword/address 성공·빈 결과·오류를 테스트한다.
2. 성공 시 step 3을 `completed`로 기록한다.
3. 실제 Kakao credential을 요구하면 test 설계를 수정하고 credential 없이 계속한다.

## 하지 말 것

- 실제 카카오 네트워크를 test에서 호출하지 말 것.
- provider error body, key 또는 stack trace를 API에 노출하지 말 것.
- client가 Kakao API를 직접 호출하는 계약을 만들지 말 것.
