# Backend Development Guidelines

**Spring Boot · Java 17 · Microservices**

## Purpose

Establish consistency and best practices across backend microservices (e.g. `blog-service`, `auth-service`, `notification-service`) using **Spring Boot**, **Java 17**, and modern enterprise patterns.

This document defines **architecture**, **layer responsibilities**, **error handling**, **validation**, **configuration**, **observability**, and **testing standards**.

---

## When to Use This Specification

Automatically applies when working on:

* REST APIs (controllers, endpoints)
* Business logic (services)
* Persistence logic (repositories, JPA/QueryDSL)
* Middleware (filters, interceptors)
* Validation (Jakarta Validation / custom validators)
* Configuration management
* Error handling & observability
* Refactoring legacy Spring applications
* Writing unit / integration tests

---

## Quick Start

### New Backend Feature Checklist

* [ ] **Controller**: REST endpoint only
* [ ] **DTO**: Request / Response separation
* [ ] **Service**: Business logic
* [ ] **Repository**: Data access abstraction
* [ ] **Validation**: Jakarta Validation annotations
* [ ] **Exception Handling**: Global exception handler
* [ ] **Observability**: Logging + error tracking
* [ ] **Tests**: Unit + integration tests
* [ ] **Config**: `@ConfigurationProperties`

### New Microservice Checklist

* [ ] Spring Boot 3.x + Java 17
* [ ] Layered package structure
* [ ] Global exception handler
* [ ] Validation setup
* [ ] Security filter chain (if needed)
* [ ] Observability setup
* [ ] Actuator enabled
* [ ] Test framework configured

---

## Architecture Overview

### Layered Architecture

```
HTTP Request
    ↓
Controller (REST API)
    ↓
Service (Business Logic)
    ↓
Repository (Persistence)
    ↓
Database (JPA / SQL)
```

**Key Principle:**
Each layer has **one responsibility** and must not leak concerns to other layers.

---

## Directory Structure

```
service/src/main/java/com/example/service/
├── config/              # Configuration classes
├── controller/          # REST controllers
├── service/             # Business logic
├── repository/          # JPA repositories
├── domain/              # Entities / domain models
├── dto/                 # Request / Response DTOs
├── exception/           # Custom exceptions
├── validation/          # Custom validators
├── security/            # Security configuration
├── util/                # Utilities
└── Application.java     # Spring Boot entry
```

### Naming Conventions

* Controllers: `*Controller`
* Services: `*Service`
* Repositories: `*Repository`
* DTOs: `*Request`, `*Response`
* Exceptions: `*Exception`

---

## Core Principles (7 Key Rules)

### 1. Controllers Handle HTTP Only

* No business logic
* No persistence logic
* Delegate immediately to services

---

### 2. Services Own Business Logic

* Implement all domain rules
* Define transaction boundaries
* No HTTP or serialization concerns

---

### 3. Repositories Handle Data Access Only

* Use Spring Data JPA or QueryDSL
* Return domain entities
* No business logic

---

### 4. Centralized Exception Handling

* Use `@RestControllerAdvice`
* Map exceptions to HTTP responses
* Log all unexpected errors

---

### 5. Validate All Input Explicitly

* Use Jakarta Validation annotations
* Trigger validation with `@Valid`
* Implement custom validators when needed

---

### 6. Configuration via Typed Properties

* Use `@ConfigurationProperties`
* No direct environment variable access
* Fail fast on missing configuration

---

### 7. Mandatory Testing

* Unit tests for services
* Integration tests for repositories
* API tests for controllers

---

## Observability & Error Tracking

### Logging

* Use SLF4J
* Prefer structured logging
* No `System.out.println`

### Error Tracking

* Capture uncaught exceptions
* Include request context
* Use correlation IDs where applicable

---

## HTTP Status Code Guidelines

| Code | Usage                        |
| ---- | ---------------------------- |
| 200  | Successful request           |
| 201  | Resource created             |
| 204  | No content                   |
| 400  | Validation or business error |
| 401  | Authentication required      |
| 403  | Authorization failure        |
| 404  | Resource not found           |
| 500  | Unexpected server error      |

---

## Anti-Patterns to Avoid

* Business logic in controllers
* Exposing entities directly via APIs
* Swallowing exceptions
* Missing validation
* Overusing `@Transactional`
* Reading configuration via `System.getenv`

---

## Testing Strategy

### Unit Tests

* Services
* Utilities
* Validators

### Integration Tests

* Repositories
* Database interactions

### API Tests

* Controllers
* Serialization and validation

---

## Migration Notes (Legacy → Modern Spring)

* Move logic from controllers to services
* Replace field injection with constructor injection
* Introduce DTOs instead of entities
* Add global exception handling
* Add validation annotations
* Standardize configuration management

---

## Navigation Guide

| Need to... | Read this |
|------------|-----------|
| Understand architecture | [architecture-overview.md](architecture-overview.md) |
| Create routes/controllers | [routing-and-controllers.md](routing-and-controllers.md) |
| Organize business logic | [services-and-repositories.md](services-and-repositories.md) |
| Validate input | [validation-patterns.md](validation-patterns.md) |
| Add error tracking | [sentry-and-monitoring.md](sentry-and-monitoring.md) |
| Create middleware | [middleware-guide.md](middleware-guide.md) |
| Database access | [database-patterns.md](database-patterns.md) |
| Manage config | [configuration.md](configuration.md) |
| Handle async/errors | [async-and-errors.md](async-and-errors.md) |
| Write tests | [testing-guide.md](testing-guide.md) |
| See examples | [complete-examples.md](complete-examples.md) |

---

## Resource Files

### [architecture-overview.md](architecture-overview.md)
Layered architecture, request lifecycle, separation of concerns

### [routing-and-controllers.md](routing-and-controllers.md)
Route definitions, BaseController, error handling, examples

### [services-and-repositories.md](services-and-repositories.md)
Service patterns, DI, repository pattern, caching

### [validation-patterns.md](validation-patterns.md)
Zod schemas, validation, DTO pattern

### [sentry-and-monitoring.md](sentry-and-monitoring.md)
Sentry init, error capture, performance monitoring

### [middleware-guide.md](middleware-guide.md)
Auth, audit, error boundaries, AsyncLocalStorage

### [database-patterns.md](database-patterns.md)
PrismaService, repositories, transactions, optimization

### [configuration.md](configuration.md)
UnifiedConfig, environment configs, secrets

### [async-and-errors.md](async-and-errors.md)
Async patterns, custom errors, asyncErrorWrapper

### [testing-guide.md](testing-guide.md)
Unit/integration tests, mocking, coverage

### [complete-examples.md](complete-examples.md)
Full examples, refactoring guide

---

## Related Skills

- **database-verification** - Verify column names and schema consistency
- **error-tracking** - Sentry integration patterns
- **skill-developer** - Meta-skill for creating and managing skills

---

**Skill Status**: COMPLETE ✅
**Line Count**: < 500 ✅
**Progressive Disclosure**: 11 resource files ✅
