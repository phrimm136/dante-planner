# CRLF Log Injection: Attack Vector and Defense

> **Date**: 2026-02-20
> **Discovered by**: Adversarial code review of MDC request correlation feature
> **Affected file**: `MdcLoggingFilter.java` — `request.getRequestURI()` written to MDC, rendered by `PatternLayout`
> **Fix**: `.replaceAll("[\r\n]", "_")` before `MDC.put("path", ...)`
> **Scope**: Log injection, audit trail forgery, CloudWatch alarm manipulation

---

## Table of Contents

1. [What CRLF Means](#1-what-crlf-means)
2. [Why Logs Are Vulnerable](#2-why-logs-are-vulnerable)
3. [The Vulnerable Code Path](#3-the-vulnerable-code-path)
4. [Attack Walkthrough](#4-attack-walkthrough)
5. [What an Attacker Can Do](#5-what-an-attacker-can-do)
6. [Why the JSON Appender Was Safe](#6-why-the-json-appender-was-safe)
7. [The Fix](#7-the-fix)
8. [General Defense Patterns](#8-general-defense-patterns)
9. [Lessons Learned](#9-lessons-learned)

---

## 1. What CRLF Means

`CR` = Carriage Return (`\r`, hex `0x0D`), `LF` = Line Feed (`\n`, hex `0x0A`).

In HTTP, `\r\n` is the line delimiter for headers. In log files, `\n` (or `\r\n` on Windows) is the record delimiter — each log entry is one line. If you can inject these characters into a log entry, you can manufacture new lines that look like legitimate log records.

Percent-encoding in URLs: `%0d` = `\r`, `%0a` = `\n`. The servlet container decodes percent sequences before `getRequestURI()` is called, so these arrive as raw bytes in the returned string.

---

## 2. Why Logs Are Vulnerable

A log file has no schema. It is text, delimited by newlines. Parsers — human eyes, CloudWatch Logs Insights, Kibana, Datadog — treat each line as an independent record. There is no checksum, no record boundary marker, no sequence number. The only trust signal is "it came from the log file."

If an untrusted value (user input, HTTP headers, request paths) reaches a log line without sanitization, an attacker controls part of the record — and by injecting `\n`, they can close the current record and open a new one.

---

## 3. The Vulnerable Code Path

The MDC logging feature introduced this path:

```
HTTP request URI (attacker-controlled)
    │
    ▼
request.getRequestURI()        ← servlet decodes %0d%0a to \r\n here
    │
    ▼
MDC.put("path", uri)           ← raw value stored in thread-local MDC map
    │
    ▼
PatternLayout: %X{path}        ← value written verbatim into log line string
    │
    ▼
Console stdout                 ← CloudWatch ingests via awslogs driver
```

The problem is at the `PatternLayout` step. `%X{path}` is a simple string substitution — it does not escape, quote, or sanitize. Whatever is in MDC under the key `path` is copied character-for-character into the output line.

---

## 4. Attack Walkthrough

### Crafted Request

```http
GET /api/planner/md/%0d%0aERROR%202026-02-20%2012:00:00.000%20%5Bfake-id%2Fadmin%5D%20%5Bhttp-nio%5D%20SecurityConfig%20-%20Admin%20password%20changed%20by%20userId%3D1 HTTP/1.1
Host: danteplanner.com
```

After percent-decoding, `getRequestURI()` returns:

```
/api/planner/md/\r\nERROR 2026-02-20 12:00:00.000 [fake-id/admin] [http-nio] SecurityConfig - Admin password changed by userId=1
```

### What gets written to stdout (and into CloudWatch)

```
2026-02-20 12:00:00.123  INFO [abc123-...-def/guest] [http-nio-8080-exec-1] MdcLoggingFilter - Request received
ERROR 2026-02-20 12:00:00.000 [fake-id/admin] [http-nio] SecurityConfig - Admin password changed by userId=1
```

Two lines. The second line looks exactly like a real log entry:
- Correct timestamp format
- Valid log level
- Real class name from the codebase (`SecurityConfig`)
- A realistic, alarming message

No parser, human or automated, can distinguish the injected line from a genuine one without inspecting the raw bytes.

---

## 5. What an Attacker Can Do

| Attack Goal | Injected Content | Effect |
|-------------|-----------------|--------|
| **Audit trail forgery** | Fake `userId=1` (admin) performing an action | Frame a specific user; frames them in incident review |
| **Incident noise** | Fake `NullPointerException at PlannerService:142` | Team spends hours chasing a non-existent bug |
| **Alarm flooding** | Inject lines matching the metric filter `[status=5*, ...]` | Trigger CloudWatch `DantePlanner-HTTP5xx` alarm repeatedly → alarm fatigue → real alerts ignored |
| **Evade detection** | Inject a fake "success" log after a real attack attempt | Misleads automated intrusion detection |
| **Security event forgery** | Fake `WARN Security event: TOKEN_REVOKED - IP: ...` | Creates false impression of an ongoing attack from a specific IP |
| **Cover tracks** | Inject benign-looking entries after a real malicious request | Buries the attacker's actual request ID in noise |

The CloudWatch dimension is particularly dangerous here because the infrastructure was specifically being built around log-based alerting. Injecting content that matches metric filter patterns bypasses the security value of the entire alarm system.

---

## 6. Why the JSON Appender Was Safe

Both appenders in `logback-spring.xml` read from the same MDC map. The vulnerability was **encoder-specific**, not data-specific.

### `PatternLayout` (console) — Vulnerable

```xml
<pattern>%d{...} %5level [%X{requestId}/%X{path}] ...</pattern>
```

`PatternLayout` is a string formatter. `%X{path}` performs a direct string substitution — identical to `String.format("... %s ...", mdc.get("path"))`. No escaping. `\n` in the value becomes `\n` in the output.

### `LogstashEncoder` (file) — Safe

`LogstashEncoder` serializes the entire log event as a JSON object:

```json
{
  "path": "/api/planner/md/\r\nERROR 2026-02-20..."
}
```

Jackson's `ObjectMapper` escapes `\r` → `\\r` and `\n` → `\\n` — they become the two-character sequences `\r` and `\n` inside a JSON string value. The injected newline is captured inside a quoted string. The JSON record remains a single line. No injection possible.

```
┌──────────────────────────────────────────────────────────┐
│  SAME MDC VALUE, TWO ENCODERS, DIFFERENT OUTCOMES        │
│                                                          │
│  MDC["path"] = "/api/\r\nFAKE LINE"                      │
│                                                          │
│  PatternLayout ──────► /api/                             │
│                        FAKE LINE         ← injection     │
│                                                          │
│  LogstashEncoder ────► {"path":"/api/\\r\\nFAKE LINE"}  │
│                                          ← safe (escaped)│
└──────────────────────────────────────────────────────────┘
```

This asymmetry is the core of the vulnerability. The file appender's safety did not protect the console appender.

---

## 7. The Fix

```java
// Before (vulnerable)
MDC.put("path", request.getRequestURI());

// After (safe)
MDC.put("path", request.getRequestURI().replaceAll("[\r\n]", "_"));
```

The malicious URI becomes `/api/planner/md/__ERROR 2026-02-20 ...` — all on one line, clearly a malformed path, unmistakably a probe attempt.

### Why only `\r` and `\n`?

- These are the only characters that terminate a log line in any logging backend (including CloudWatch).
- `%0d` and `%0a` are the only percent-encoded forms that decode to newline characters — there are no alternate encodings.
- Unicode line separators (`\u2028`, `\u2029`) function as newlines in JavaScript but not in Java's log file parsers. They are not a practical threat here.

### Why not sanitize all percent-encoded characters?

Percent-decoding has already happened by the time `getRequestURI()` is called. There is no raw `%` sequence left to decode. The injected bytes arrive as decoded characters, so only the decoded characters need to be checked.

### Complementary defense: logback default values

Even if MDC is empty (e.g., actuator health checks bypass the filter), the console pattern should not render as `[/]`:

```xml
<!-- Before -->
[%X{requestId}/%X{userId}]

<!-- After -->
[%X{requestId:-no-id}/%X{userId:-guest}]
```

The `:-default` syntax is Logback's MDC default value notation. It does not affect injection safety (empty MDC means no user input) but prevents confusing output for pre-filter requests.

---

## 8. General Defense Patterns

**Rule**: Any value originating from user input that reaches a log statement must be sanitized before logging, not after.

| Input source | Risk characters | Sanitization |
|---|---|---|
| HTTP request URI | `\r`, `\n` | Strip or replace |
| HTTP headers (`User-Agent`, `Referer`) | `\r`, `\n` | Strip or replace |
| Query parameters | `\r`, `\n` | Strip or replace |
| Request body fields | `\r`, `\n` | Strip or replace |
| Error messages from external APIs | `\r`, `\n`, ANSI escape codes | Strip |

**Structured logging eliminates the problem entirely.** If all appenders use `LogstashEncoder` (or equivalent JSON encoder), there is no `PatternLayout` to exploit. The JSON encoder escapes everything by design. The residual risk from `PatternLayout` is why mixing structured and unstructured appenders (as in this production configuration) requires explicit sanitization in the MDC population code.

**Do not sanitize in the appender config.** Some Logback configurations attempt to sanitize via custom `Converter` classes in the pattern. This is fragile — it only protects the specific pattern it is applied to, not MDC values used in other patterns or other appenders. Sanitize at the source (when writing to MDC), not at the sink.

---

## 9. Lessons Learned

1. **User input in MDC is not different from user input in SQL.** Both require sanitization before reaching a structured context (a log line, a query). MDC is not a safe channel — it is a passthrough.

2. **Encoder safety is not symmetric.** A JSON appender and a PatternLayout appender reading the same MDC key have opposite vulnerability profiles. Adding a console appender to a system that previously only used `LogstashEncoder` introduces injection risk that did not previously exist.

3. **Code review found this; tests did not.** The test suite for `MdcLoggingFilter` verified correct MDC keys and cleanup. It did not test what happens when a `\n` appears in the path — because testing the filter in isolation does not exercise the rendering step (PatternLayout). Injection vulnerabilities often live at the seam between components, not inside any single component.

4. **The attack surface is proportional to the logging feature's ambition.** Adding MDC correlation improves observability — and simultaneously creates a new injection surface, because MDC values now come from user-controlled data. Every observability feature that ingests request context must treat that context as untrusted input.
