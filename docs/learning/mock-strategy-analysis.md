# Mock Strategy Analysis Report

## PlannerContentValidatorTest Mock 구조 분석

**작성일:** 2024-12-30
**대상 파일:** `PlannerContentValidatorTest.java`
**관련 이슈:** UnnecessaryStubbingException 해결 및 Mock 계층화

---

## 1. 배경

### 1.1 문제 상황
`PlannerContentValidator`에 Start Gift 검증 로직을 추가한 후, 기존 테스트에서 `UnnecessaryStubbingException` 발생.

```
Unnecessary stubbings detected.
Following stubbings are unnecessary:
  1. -> setupStartGiftMocks (line 88)
  2. -> setupStartGiftMocks (line 89)
```

### 1.2 근본 원인
Mockito의 **Strict Stubbing** 정책: 설정된 mock이 테스트 중 호출되지 않으면 에러 발생.

---

## 2. Mock이 필요한 이유

### 2.1 단위 테스트의 격리 원칙

```
┌─────────────────────────────────────────────────────────────┐
│                    PlannerContentValidator                   │
│                                                             │
│  ┌─────────────┐    ┌───────────────────┐                  │
│  │   validate  │───▶│  GameDataRegistry │ ◀── 외부 의존성   │
│  │   method    │    │  (파일 시스템)     │                  │
│  └─────────────┘    └───────────────────┘                  │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────────┐                                       │
│  │ SinnerIdValidator│ ◀── 외부 의존성                       │
│  └─────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

**GameDataRegistry의 의존성:**
- `../static/data/` 경로에서 JSON 파일 로드
- Identity, EGO, EgoGift, ThemePack, StartBuff, StartGiftPool 데이터 필요
- 파일 시스템 접근 = 느리고 불안정한 테스트

**Mock 사용 이유:**
1. **속도**: 파일 I/O 없이 즉시 응답
2. **격리**: 실제 데이터 변경에 영향받지 않음
3. **제어**: 특정 시나리오(존재하지 않는 ID 등) 테스트 가능

### 2.2 검증 순서와 Mock 호출 관계

`PlannerContentValidator.validate()` 메서드의 검증 순서:

```java
// 1. 구조 검증
validateContentSize(content);
validateRequiredFields(root);
validateFieldTypes(root);
validateUnknownFields(root);
// ...

// 2. ID 존재 검증 (Mock 필요)
validateEquipmentIds(root);      // hasIdentity, hasEgo, sinnerIdValidator
validateGiftIds(root);           // hasEgoGift
validateFloorSelectionIds(root); // hasThemePack, hasEgoGift
validateStartBuffIds(root);      // hasStartBuff
validateStartGiftIds(root);      // hasStartGiftKeyword, getStartGiftPool
```

**핵심 통찰:**
- 앞선 검증에서 실패하면 이후 검증에 도달하지 않음
- 도달하지 않은 검증의 Mock은 "불필요한 stubbing"이 됨

---

## 3. Mock 헬퍼 계층 구조

### 3.1 설계 원칙

```
검증 실패 시점에 따른 Mock 선택
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

실패 시점              필요한 Mock                    헬퍼 메서드
─────────────────────────────────────────────────────────────
Equipment 검증     →   identity, ego, sinnerMatch   →  (없음, 거의 사용 안함)
Gift 타입 에러     →   + 없음 (hasEgoGift 호출 전)  →  setupMocksForGiftTypeFailure
Gift 중복 에러     →   + hasEgoGift                 →  setupMocksForGiftDuplicateFailure
Floor 검증 실패    →   + themePack                  →  setupMocksForValidIdsWithoutBuffsAndGifts
Buff 검증 실패     →   + hasStartBuff               →  setupMocksForValidIdsWithoutGifts
Gift Pool 실패     →   + startGiftKeyword, pool     →  setupMocksForValidIdsWithoutBuffs
전체 통과          →   모든 Mock                    →  setupMocksForValidIds
```

### 3.2 구현된 헬퍼 메서드

```java
// 기본 Mock (Equipment + Floor 검증용)
private void setupBaseMocks() {
    when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
    when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
    when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
    when(gameDataRegistry.hasThemePack(anyString())).thenReturn(true);
    when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
}

// Start Buff 검증용
private void setupStartBuffMocks() {
    when(gameDataRegistry.hasStartBuff(anyString())).thenReturn(true);
}

// Start Gift Pool 검증용
private void setupStartGiftMocks() {
    when(gameDataRegistry.hasStartGiftKeyword(anyString())).thenReturn(true);
    when(gameDataRegistry.getStartGiftPool(anyString())).thenReturn(Set.of("9001", "9009", "9103"));
}

// Gift 타입 에러용 (hasEgoGift 호출 전 실패)
private void setupMocksForGiftTypeFailure() {
    when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
    when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
    when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    // hasEgoGift 제외 - 타입 체크에서 먼저 실패
}

// Gift 중복 에러용 (첫 번째 ID 검증 후 중복 감지)
private void setupMocksForGiftDuplicateFailure() {
    when(gameDataRegistry.hasIdentity(anyString())).thenReturn(true);
    when(gameDataRegistry.hasEgo(anyString())).thenReturn(true);
    when(gameDataRegistry.hasEgoGift(anyString())).thenReturn(true);
    when(sinnerIdValidator.validateMatch(anyString(), anyString())).thenReturn(true);
    // hasThemePack 제외 - floor 검증까지 도달 안함
}
```

### 3.3 조합 헬퍼

```java
// 전체 검증 통과
private void setupMocksForValidIds() {
    setupBaseMocks();
    setupStartBuffMocks();
    setupStartGiftMocks();
}

// Buff 검증 건너뛰기 (selectedBuffIds 비어있음)
private void setupMocksForValidIdsWithoutBuffs() {
    setupBaseMocks();
    setupStartGiftMocks();
}

// Gift Pool 검증 건너뛰기 (Buff에서 실패)
private void setupMocksForValidIdsWithoutGifts() {
    setupBaseMocks();
    setupStartBuffMocks();
}

// Buff + Gift Pool 모두 건너뛰기 (Floor에서 실패)
private void setupMocksForValidIdsWithoutBuffsAndGifts() {
    setupBaseMocks();
}
```

---

## 4. 테스트 케이스별 Mock 선택 가이드

### 4.1 의사결정 플로우차트

```
테스트가 어디서 실패하는가?
│
├─ 구조 검증 (필드 누락, 타입 오류 등)
│   └─ Mock 불필요 (JSON 파싱만 수행)
│
├─ Equipment ID 검증
│   └─ setupBaseMocks() 중 일부만 설정
│
├─ Gift ID 타입 에러
│   └─ setupMocksForGiftTypeFailure()
│
├─ Gift ID 중복 에러
│   └─ setupMocksForGiftDuplicateFailure()
│
├─ Gift ID 존재하지 않음
│   └─ setupMocksForGiftDuplicateFailure() + hasEgoGift("invalid").thenReturn(false)
│
├─ Floor 검증 실패
│   └─ setupMocksForValidIdsWithoutBuffsAndGifts()
│
├─ Start Buff 검증 실패
│   └─ setupMocksForValidIdsWithoutGifts()
│
├─ Start Gift Pool 검증 실패
│   └─ setupMocksForValidIdsWithoutBuffs() 또는 개별 설정
│
└─ 전체 통과
    └─ setupMocksForValidIds()
```

### 4.2 실제 적용 예시

```java
// 예시 1: Gift ID 타입 에러 테스트
@Test
void validate_NonStringGiftId_ThrowsException() {
    // 타입 체크는 hasEgoGift 호출 전에 발생
    setupMocksForGiftTypeFailure();  // hasEgoGift 제외

    String content = createValidContent().replace(
        "\"selectedGiftIds\": [\"9001\"],",
        "\"selectedGiftIds\": [9001],"  // 정수 → 타입 에러
    );
    assertThrows(PlannerValidationException.class, () -> validator.validate(content));
}

// 예시 2: Gift ID 중복 테스트
@Test
void validate_DuplicateGiftIds_ThrowsException() {
    // 첫 번째 ID 검증 후 중복 감지
    setupMocksForGiftDuplicateFailure();  // hasEgoGift 포함

    String content = createValidContent().replace(
        "\"selectedGiftIds\": [\"9001\"],",
        "\"selectedGiftIds\": [\"9001\", \"9001\"],"  // 중복
    );
    assertThrows(PlannerValidationException.class, () -> validator.validate(content));
}

// 예시 3: 전체 통과 테스트
@Test
void validate_ValidContent_Passes() {
    setupMocksForValidIds();  // 모든 Mock
    assertDoesNotThrow(() -> validator.validate(createValidContent()));
}
```

---

## 5. 교훈 및 베스트 프랙티스

### 5.1 Mockito Strict Stubbing 이해

| 모드 | 동작 | 권장 사용 |
|------|------|----------|
| STRICT_STUBS (기본) | 미사용 stub → 에러 | 대부분의 경우 |
| LENIENT | 미사용 stub 허용 | 레거시 코드 마이그레이션 |

**STRICT_STUBS를 유지해야 하는 이유:**
- 불필요한 코드 감지
- 테스트 의도 명확화
- 리팩토링 시 안전망

### 5.2 Mock 설계 원칙

1. **최소 필요 원칙**: 테스트에 필요한 Mock만 설정
2. **계층화**: 검증 순서에 따른 Mock 그룹 분리
3. **명확한 네이밍**: 용도가 드러나는 헬퍼 메서드 이름
4. **문서화**: 각 헬퍼의 사용 시점 주석으로 명시

### 5.3 새 검증 로직 추가 시 체크리스트

- [ ] 검증 순서 확인 (어느 위치에서 호출되는가?)
- [ ] 필요한 Mock 식별 (어떤 외부 의존성 사용?)
- [ ] 기존 헬퍼로 충분한지 확인
- [ ] 불충분하면 새 헬퍼 추가
- [ ] 기존 테스트의 Mock 설정 영향 분석
- [ ] UnnecessaryStubbingException 발생 여부 테스트

---

## 6. 결론

Mock 전략의 핵심은 **검증 순서와 실패 시점의 이해**입니다.

Mockito의 Strict Stubbing은 제약이 아닌 **테스트 품질 향상 도구**로, 불필요한 코드를 제거하고 테스트 의도를 명확히 합니다.

계층화된 Mock 헬퍼 구조를 통해:
- 테스트 코드 중복 제거
- 새로운 테스트 작성 용이
- 유지보수성 향상

---

## 부록: Mock 헬퍼 요약표

| 헬퍼 메서드 | 포함 Mock | 사용 시점 |
|------------|----------|----------|
| `setupBaseMocks()` | identity, ego, egoGift, themePack, sinnerMatch | 기본 ID 검증 |
| `setupStartBuffMocks()` | startBuff | Buff 검증 |
| `setupStartGiftMocks()` | startGiftKeyword, startGiftPool | Gift Pool 검증 |
| `setupMocksForGiftTypeFailure()` | identity, ego, sinnerMatch | Gift 타입 에러 |
| `setupMocksForGiftDuplicateFailure()` | identity, ego, egoGift, sinnerMatch | Gift 중복/존재 에러 |
| `setupMocksForValidIds()` | 전체 | 모든 검증 통과 |
| `setupMocksForValidIdsWithoutBuffs()` | base + gift | Buff 비어있음 |
| `setupMocksForValidIdsWithoutGifts()` | base + buff | Buff에서 실패 |
| `setupMocksForValidIdsWithoutBuffsAndGifts()` | base | Floor에서 실패 |
