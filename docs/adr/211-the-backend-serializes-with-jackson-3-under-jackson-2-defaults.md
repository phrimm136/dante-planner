# 211 the-backend-serializes-with-jackson-3-under-jackson-2-defaults
epic: none · pr: none · supersedes: 210 @backend @framework @upgrade

## Decisions
- @backend @json — The backend runs Spring Boot 4.1 and serializes with Jackson 3 under `spring.jackson.use-jackson2-defaults=true`, with UTF-8 surrogate pairs kept escaped (`spring.jackson.json.write.combine-unicode-surrogates-in-utf8=false`); `spring-boot-jackson2` and the application's direct Jackson 2 dependencies are gone. Every external and stored JSON format was captured byte for byte before the move and matches after it.
  REJECTED: Jackson 3's own defaults — they sort properties alphabetically and write emoji unescaped, which changes the wire bytes of responses and stored content.
  INVARIANT: the JSON wire format is unchanged by the library move — enforced by the controller and contract tests, which pass unmodified.
  NON-GOAL: Jackson 3 `JsonNode` conversion semantics in readers that relied on Jackson 2 returning defaults — those readers keep the old answers explicitly; new code gets Jackson 3's stricter conversions.
- @backend @json @schema — Planner content reaches the JSON schema validator as text, because the validator release on the classpath binds to Jackson 2 and its Jackson 3 line requires a newer Jackson than Spring Boot manages; application code imports no Jackson 2 type.
  REJECTED: upgrading the validator now — its API was rewritten and it would pull Jackson 3.2 against Boot's managed 3.1, two Jackson 3 minors on one classpath.
  INVARIANT: the text bridge leaves when Spring Boot manages Jackson 3.2 or later — unenforced; the condition is a dependency-version check.

## Takeaway
- takeaway: a serialization library move is safe to land in one step only when every byte stream it touches was captured first; compare captures, not test results.
