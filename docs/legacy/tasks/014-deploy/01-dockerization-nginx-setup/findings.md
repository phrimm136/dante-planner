# Learning Reflection

## What Was Easy

- Multi-stage Docker builds pattern well-established (Maven→JRE, Node→nginx)
- Health checks integration straightforward with Spring Actuator
- Named volumes for MySQL persistence required minimal configuration
- Header forwarding through nginx simple due to existing proxy_set_header patterns
- Environment variable substitution already supported by Spring Boot

## What Was Challenging

- Private IP range detection required understanding RFC 1918 boundary cases
- Rate limit bucket key format collision risk between IP and device ID
- Docker bridge network CIDR (172.18.0.0/16) non-obvious
- Flyway migration charset/collation mismatches only visible at container startup
- OAuth callback URL timing with Google Console

## Key Learnings

- Rate limiting failure modes are silent without device ID fallback
- Trusted proxy CIDR validation requires custom implementation
- Docker network discovery matters - IPs not hardcoded
- Multi-stage Dockerfile layer ordering affects rebuild speed
- Health check dependencies enable graceful orchestration
- Environment variable precedence unclear without explicit documentation

## Spec-Driven Process Feedback

- research.md 95% accurate but missed CIDR math hazards
- Testing guidelines incomplete for OAuth validation
- Code quality review discovered issues after implementation

## Pattern Recommendations

- Add CIDR support pattern to be-security skill docs
- Document health check anti-patterns (TCP socket vs app-specific)
- Establish rate limiting identifier format standard

## Next Time

- Split rate limiting logic from IP resolution earlier
- Test OAuth callback URL changes before Docker setup
- Validate Flyway migrations with Docker charset from the start
