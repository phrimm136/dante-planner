# Task: Create Spring Boot Project

## Description
Generate Spring Boot starter project with Web, JPA, MySQL, Redis, Security, WebSocket dependencies.

## Research
- Use Spring Initialzr
- Choose appropriate Java version (17+)

## Scope
- Online only

## Target Code Areas
backend/
  pom.xml
  src/main/java/com/example/app/
  src/main/resources/application.yml

## Testing Guidelines
- Run ./mvnw spring-boot:run → verify server starts.
- Check /actuator/health returns UP.