# Project Spec Conventions

Additional sections required in every `/spec` for this project.

## Data-Driven Features

Features that consume raw game data files MUST include:

### 1. Data Model Catalog

Scan raw data and list every distinct value for enum-like fields with occurrence counts:

```
resultCondition: Prob_1 (89), Prob_0.5 (12), MpAverage_Under0 (2), ProbTimesRepeatCount_0.125 (1)
cantSelectInThisCase: None (majority), HasNotEgoGift_{id} (8), HasNotEnoughCost_{n} (3)
target: EveryAlly (45), RandomAlly_1 (6), Donquixote (2)
```

Without this catalog, each new pattern is discovered mid-implementation, causing architecture pivots.

### 2. Normalization Layer

Explicitly state where each data cleanup happens:

| Cleanup | Where | Why |
|---------|-------|-----|
| Casing normalization | Pipeline (Python) | Build-time, all consumers benefit |
| Suffix stripping | Pipeline (Python) | Data should be lookup-ready |
| Tag sanitization | Pipeline (Python) | Frontend receives clean text |
| Display formatting | Frontend (TypeScript) | Rendering concern only |

Rule: Frontend should receive clean, display-ready data. No runtime mapping tables.

### 3. Rendering Mode Enumeration

List every distinct way the data can display, with an example ID per mode:

```
1. Single result + direct effects (901001)
2. Probability branches with shared narrative (971003)
3. Conditional branches based on game state (901010)
4. Coin toss with sub-conditions per outcome (901020)
5. Cumulative probability with retry (901023)
```

### 4. Reference Per Mode

For each rendering mode, provide a screenshot or text description of the expected output. Eliminates ambiguity about what "correct" looks like.

### 5. Implementation Order

Always: data audit → pipeline → schema → rendering. Never interleave pipeline and frontend work.
