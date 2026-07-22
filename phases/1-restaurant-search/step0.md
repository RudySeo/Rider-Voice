# Step 0: restaurant-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/common/persistence/BaseEntity.kt`

## 작업
카카오 장소 ID를 기준으로 Restaurant domain과 JPA persistence, Flyway migration을 구현한다. 이름·주소·좌표·파일럿 포함 여부를 저장하고 장소 ID 중복을 막는다.

## 인수 기준
```bash
./gradlew compileKotlin compileTestKotlin
./gradlew test
```

## 하지 말 것
- OCR 문자열만으로 Restaurant를 식별하지 말 것. 이유: 동명 지점 연결 오류를 막아야 한다.
