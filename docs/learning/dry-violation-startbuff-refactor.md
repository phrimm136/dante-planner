# DRY Violation: StartBuff Refactor Case Study

## What Happened

Grid+Hook 과도한 추상화 제거 → 로직을 Section/EditPane에 인라인 → **동일 로직 2곳에 복사됨**

```
Before (과도한 추상화):
  useStartBuffGrid.ts (1곳) → StartBuffGrid.tsx → Section/EditPane

After (잘못된 수정):
  StartBuffSection.tsx (deriveEnhancements, displayBuffs, handleSelect)
  StartBuffEditPane.tsx (deriveEnhancements, displayBuffs, handleSelect) ← 복사됨
```

## Why This Happened

1. **문제 해결에 집중** - "Grid+Hook 삭제"라는 목표만 보고, 삭제 후 로직이 어디로 가는지 고려 안 함
2. **패턴 맹목적 적용** - ThemePackSelectorPane이 "직접 렌더링"이라서 그대로 복사
3. **YAGNI 오해** - "Hook이 한 곳에서만 쓰임 → 삭제" 했지만, 삭제 후 **두 곳에서 쓰이게 됨**

## Why It's a Principle Violation

| Principle | Violation |
|-----------|-----------|
| **DRY** | 44줄 동일 코드 2곳 존재 |
| **CLAUDE.md Rule 6** | "ALWAYS extract duplicates" 위반 |
| **Maintenance** | 버프 로직 변경 시 2파일 동기화 필요 |

## Why Code Review Caught It

1. **Adversarial mindset** - "코드가 틀렸다고 가정"하고 시작
2. **Industrial standards checklist** - SOLID/DRY/KISS/YAGNI 순회 검사
3. **Line-by-line comparison** - Section:23-34 vs EditPane:28-39 직접 비교
4. **Permanent debt 관점** - "이거 안 고치면 영원히 남는다"

## Correct Solution

```
useStartBuffSelection.ts (shared hook)
  ├── deriveEnhancements()  ← StartBuffTypes.ts로 이동
  ├── displayBuffs 계산
  └── handleSelect 로직

StartBuffSection.tsx → useStartBuffSelection() 호출
StartBuffEditPane.tsx → useStartBuffSelection() 호출
```

## Lesson

**추상화 제거 ≠ 코드 복사**

추상화가 과도하면 제거해야 하지만, 제거 후 **공유 로직이 어디로 가는지** 반드시 확인:
- 1곳에서만 사용 → 인라인 OK
- 2곳 이상에서 사용 → 새로운 적절한 추상화 필요 (utility 함수 또는 hook)
