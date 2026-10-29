# 210 the-backend-runs-spring-boot-4-with-jackson-2-kept
epic: none · pr: none

## Decisions
- @backend @framework @upgrade — The backend runs Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Flyway 12) and keeps Jackson 2 through Boot's deprecated `spring-boot-jackson2` module, with `spring.http.converters.preferred-json-mapper=jackson2`. Spring Boot 3.5 is past its OSS support, and its Flyway 11.7 logs an upgrade warning on every start against MySQL 8.4; about 158 source files import Jackson 2, so the framework move and the JSON library move ship as two units.
  REJECTED: one unit moving Boot and Jackson together — the Jackson move touches every serialization path and would hide framework regressions inside a wire-format diff.
  REJECTED: pinning Flyway on Boot 3.5 — it silences the warning and leaves the backend on an unsupported framework, a stopgap removed by the very upgrade it defers.
  INVARIANT: the JSON wire format is unchanged by the framework move — enforced by the controller and contract tests, which pass unmodified.
  INVARIANT: `spring-boot-jackson2` leaves the build when the Jackson 3 migration lands — unenforced; the deprecation is the reminder.
  NON-GOAL: byte-identical `Set-Cookie` headers — a deleting cookie from Tomcat 11 carries only the past `Expires`, without `Max-Age=0`; the past `Expires` still deletes it, so no custom cookie processor keeps the old bytes.

## Takeaway
- takeaway: when a major framework upgrade and a data-format library change arrive together, split them so each has a single observable to prove it unchanged.
