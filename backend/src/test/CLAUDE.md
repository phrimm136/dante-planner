# Backend Tests

- No `@WebMvcTest`, `@MockBean`, or `@DataJpaTest` anywhere. Two tiers only:
  - Unit: `@ExtendWith(MockitoExtension.class)`, mock at boundaries (external services), test behavior via the public API.
  - Integration: MySQL/Redis Testcontainers on the `it` profile (`@ActiveProfiles("it")`), tagged `@Tag("containerized")`, run with `-Dgroups=containerized`.
- The `it` profile runs Flyway + `ddl-auto=validate` against the real migrated schema — the authoritative schema guard; never weaken it to `create-drop`.
- Testcontainer tests must register `spring.flyway.url/user/password` (not just `spring.datasource.*`) via `@DynamicPropertySource`, or Flyway connects to the prod-pinned datasource and fails.
- Naming: `methodName_WhenCondition_ExpectedBehavior`; group with `@Nested` + `@DisplayName`.
- Time-dependent JWT tests inject a fixed `Clock` — never 1 ms expiries (see `JwtTokenServiceTest`).
- Deterministic ordering via `entity.setCreatedAt(now.minusSeconds(n))` — never `Thread.sleep()`.
- `@Transactional` test rollback prevents `@TransactionalEventListener(AFTER_COMMIT)` from firing — AFTER_COMMIT paths need a committing (containerized) test.
- Build reusable entities with the `TestDataFactory` helper.
- Gradle does not capture application stdout/logs in this project's test output — localize failing integration tests by assertion-based bisection (probe an observable flag), not log statements.
