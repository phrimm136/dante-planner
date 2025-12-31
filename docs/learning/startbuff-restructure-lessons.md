# StartBuff Restructure: 속터진 순간들과 배운 점

## 요약

StartBuff View+Edit Pane 구현에서 **"추상화 제거"를 "코드 복사"로 오해**하여 DRY 원칙 위반 발생.
Spec에서 "thin wrapper"라고 했는데 실제로는 22줄 grid 렌더링 로직을 복사함.

---

## 속터진 순간들

### 1. Grid+Hook 삭제 → 같은 코드 2곳에 복사됨

**상황**:
```
Before (과도한 추상화):
  useStartBuffGrid.ts → StartBuffGrid.tsx → Section/EditPane

After (잘못된 수정):
  StartBuffSection.tsx (grid 로직 22줄)
  StartBuffEditPane.tsx (grid 로직 22줄) ← 똑같이 복사됨
```

**왜 화났나**:
- "과도한 추상화 제거"라는 좋은 의도로 시작했는데 결과는 DRY 위반
- Spec에 "thin wrapper"라고 분명히 적었는데 무시하고 복사함
- CLAUDE.md Rule 6 "ALWAYS extract duplicates" 위반

### 2. Spec 문구 무시

**Spec 원문**: "reuse StartBuffSection", "thin wrapper"

**실제 구현**: EditPane이 Section을 감싸지 않고 grid를 직접 렌더링

**왜 화났나**:
- Research 단계에서 명확히 정의했는데 구현 단계에서 drift 발생
- 코드 리뷰가 아니었으면 그냥 넘어갈 뻔함

### 3. 조기 추상화 (useStartBuffSelection)

**상황**: 2곳에서만 쓰이는 hook을 만들어서 indirection 추가

**왜 화났나**:
- Hook 삭제한다면서 새 hook 만듦 (뭐하는 짓인지)
- 3곳 이상에서 쓰일 때까지 기다렸어야 함

---

## 배운 점

### 1. 추상화 제거 ≠ 코드 복사

**핵심**: 추상화가 과도하면 제거해야 하지만, 삭제 후 **공유 로직이 어디로 가는지** 확인 필수

| 사용처 | 해결책 |
|--------|--------|
| 1곳 | 인라인 OK |
| 2곳+ | 새로운 적절한 추상화 필요 |

### 2. Spec 문구는 제약조건이다

"thin wrapper", "reuse component" 같은 문구는 **제안이 아니라 검증해야 할 제약조건**

**체크리스트 추가**:
- [ ] EditPane에 Section과 동일한 렌더링 로직이 있는가?
- [ ] 있다면 Section을 wrap하는 것으로 변경

### 3. Hook 추출 기준: 3+ callers

| Callers | Action |
|---------|--------|
| 1 | 인라인 |
| 2 | 인라인 (공통 로직은 utility function으로) |
| 3+ | Hook 추출 고려 |

### 4. Code Review가 잡아낸 것

| 검사 항목 | 발견 |
|-----------|------|
| DRY | Section:23-34 vs EditPane:28-39 동일 |
| SOLID (SRP) | EditPane이 dialog + grid 두 책임 |
| Spec compliance | "thin wrapper" 위반 |

**교훈**: Adversarial mindset ("코드가 틀렸다고 가정")으로 리뷰해야 잡힘

---

## 설계 관점에서 생각할 점

### 1. Composition vs Duplication 결정 기준

**질문**: "이 컴포넌트가 다른 컴포넌트를 **감싸야** 하나, **복사해야** 하나?"

| 상황 | 선택 | 이유 |
|------|------|------|
| 동일한 UI + 다른 context | Composition (wrap) | 렌더링 로직 한 곳 유지 |
| 유사하지만 다른 UI | Duplication OK | 각각 독립적 진화 가능 |
| 공통 로직 + 다른 wrapper | Extract shared, wrap differently | 로직은 공유, UI는 분리 |

**StartBuff 케이스**: Section과 EditPane은 **동일한 grid UI**를 보여줌 → Composition이 정답

### 2. View/Edit 모드 설계 패턴

**패턴 A: Props로 분기** (선택됨)
```
<StartBuffCard viewMode={true/false} />
```
- 장점: 단일 컴포넌트, 조건부 렌더링
- 단점: props 폭발 위험, 복잡도 증가

**패턴 B: 별도 컴포넌트**
```
<StartBuffCardView />
<StartBuffCardEdit />
```
- 장점: 책임 분리, 테스트 용이
- 단점: 중복 코드 위험

**판단 기준**: 공유 로직이 70% 이상이면 Props 분기, 미만이면 분리

### 3. Wrapper 컴포넌트의 책임 범위

**Thin Wrapper가 해야 할 것**:
- Container 제공 (Dialog, Modal, Panel)
- Context 주입 (Provider)
- Event 연결 (onClose, onSubmit)

**Thin Wrapper가 하면 안 되는 것**:
- 내부 컴포넌트의 렌더링 로직 복사
- 데이터 가공 로직 중복
- 스타일링 로직 중복

### 4. 추상화 레벨 결정 프레임워크

```
Level 0: 인라인 코드
Level 1: 같은 파일 내 함수
Level 2: Utility 함수 (별도 파일)
Level 3: Custom Hook
Level 4: 별도 컴포넌트
Level 5: 별도 패키지/모듈
```

**레벨 선택 기준**:
- 재사용 횟수: 1→L0, 2→L1-2, 3+→L3-4
- 복잡도: 낮음→L0-2, 높음→L3-5
- 테스트 필요성: 낮음→L0-1, 높음→L2+

### 5. 리팩토링 시 확인 질문

1. **삭제 후 이동**: "이 로직이 삭제되면 어디로 가나?"
2. **중복 체크**: "동일한 로직이 2곳 이상에 생기나?"
3. **책임 확인**: "이 컴포넌트의 책임이 2개 이상인가?"
4. **Spec 준수**: "Spec에서 말한 패턴을 따르고 있나?"

---

## 올바른 해결책

```
StartBuffEditPane.tsx:
  <Dialog>
    <StartBuffSection viewMode={false} onSelect={...} />  ← Section을 wrap
  </Dialog>

StartBuffSection.tsx:
  grid 로직 (유일한 소스)
```

**원칙**: Wrapper는 감싸기만 하고, 렌더링 로직은 한 곳에만 존재

---

## 다음에 적용할 것

1. **Phase 2 리뷰 시**: "EditPane이 duplicate grid 렌더링하고 있나?" 체크
2. **Hook 추출 전**: "3곳 이상에서 쓰이나?" 확인
3. **Spec 문구**: "thin wrapper", "reuse" 같은 단어를 제약조건으로 취급
4. **리팩토링 후**: 삭제한 코드가 어디로 갔는지 추적
5. **설계 결정 전**: Composition vs Duplication 표 참고

---

## 관련 파일

| File | Content |
|------|---------|
| `docs/09-planner-restructure/01-startbuff/review.md` | Code review 결과 |
| `docs/09-planner-restructure/01-startbuff/findings.md` | Learning reflection |
| `docs/learning/dry-violation-startbuff-refactor.md` | DRY 위반 케이스 스터디 |
