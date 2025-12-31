# Jakarta Validation vs Bean Validation

## Summary

Jakarta Validation **is** Bean Validation - it's a rename, not a replacement. The shift happened due to Oracle's trademark restrictions when Java EE moved to the Eclipse Foundation.

---

## The Timeline

| Year | Event |
|------|-------|
| 2009 | Bean Validation 1.0 (JSR-303) released under `javax.validation` |
| 2017 | Oracle donates Java EE to Eclipse Foundation |
| 2019 | Eclipse can't use "javax" namespace (trademark) → renames to "jakarta" |
| 2020 | Jakarta EE 9+ uses `jakarta.validation` |

---

## What Changed

| Aspect | Before | After |
|--------|--------|-------|
| Package | `javax.validation.*` | `jakarta.validation.*` |
| Artifact | `javax.validation:validation-api` | `jakarta.validation:jakarta.validation-api` |
| Annotations | `@javax.validation.constraints.NotNull` | `@jakarta.validation.constraints.NotNull` |

**The API itself is identical** - same annotations, same behavior.

---

## Spring Boot Versions

| Spring Boot | Default Namespace |
|-------------|-------------------|
| 2.x | `javax.validation` |
| 3.x | `jakarta.validation` (required) |

Spring Boot 3.0+ **requires** Jakarta EE 9+ (and Java 17+), so there's no choice - you must use `jakarta.*`.

---

## Why This Project Uses Jakarta

This project runs on Spring Boot 3.x, which mandates Jakarta. Using `javax.validation` would fail at compile time.

---

## Key Takeaways

- This isn't a technical improvement - it's a legal/organizational necessity
- The "Bean Validation" spec name still exists (now at version 3.0)
- If migrating from Spring Boot 2→3, a simple find/replace of `javax.` → `jakarta.` handles most cases

---

## Migration Checklist (Spring Boot 2 → 3)

1. Update all `javax.validation` imports to `jakarta.validation`
2. Update all `javax.persistence` imports to `jakarta.persistence`
3. Update all `javax.servlet` imports to `jakarta.servlet`
4. Ensure Java 17+ is being used
5. Update any third-party libraries that depend on `javax.*` packages
