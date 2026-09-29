# 142 one-validation-code-vocabulary-named-problem-first
epic: none · pr: none · supersedes: 139 @planner @floors @codes

## Decisions
- @validation @codes — Both runtimes emit one validation code vocabulary, held in a single
  committed list that the backend enum and the frontend code union are each tested against; a code
  names one rule as `<PROBLEM>_<SUBJECT>[_<DETAIL>]`, the problem drawn from a closed set (UNKNOWN,
  MISSING, INSUFFICIENT, EXCESS, DUPLICATE, OUT_OF_RANGE, OUT_OF_ORDER, MISMATCHED, UNAFFORDABLE,
  INVALID) and the subject a single token naming the entity (THEMEPACK, EGOGIFT, IDENTITY, …). The
  two runtimes named the same rules differently outside floors, and generic codes forced the client
  to re-parse paths and allowed sets to choose a message; sub-codes never reach the wire, so the
  names were free to choose.
  REJECTED: the server's names on both sides — generic codes such as a shared out-of-range code
  cover several rules with different messages.
  REJECTED: the client's names on both sides — subject-first names mix two orders and several
  covered more than one rule.
  REJECTED: a code per message rather than per rule — two rules sharing a message today would have
  to be split again the day their messages diverge.
- @validation @codes @granularity — One code per rule, so the code alone selects the message: the
  floor sequence code splits into a theme-pack order and a difficulty order code, floor difficulty
  splits four ways (out of range on five-floor categories; floors one to five of longer categories,
  which gate entry to the later floors; Hard-only floors six to ten; Extreme-only floors eleven to
  fifteen), every floor present is its own code, an absent value and a wrong-typed value are
  distinct codes, and codes merge only when they name the same rule.
  REJECTED: one out-of-range difficulty code — the client showed the Hard-entry message for floors
  whose rule is Hard-only or Extreme-only.
- @validation @codes @game-data — A known E.G.O whose game data lacks a threadspin ceiling reports
  its own code, distinct from an unknown E.G.O, because the fault is the static data rather than
  the planner.
  REJECTED: reporting it as an unknown E.G.O — tells the user to fix a planner that is correct.
- @validation @codes @boundary — Structural codes stay generic (malformed JSON, unknown field,
  missing required field, invalid field type, empty content, size exceeded), and every other code
  is still masked to the generic validation code on the wire; the one user-facing rule code is
  renamed in a single step to `UNKNOWN_KEYWORD`, with no coexistence window, since no client reads
  it.
  REJECTED: a coexistence window for the keyword code — nothing branches on the old name.

## Takeaway
- takeaway: when a code needs a second input to pick its message, it names more than one rule;
  split it until the code alone decides.
