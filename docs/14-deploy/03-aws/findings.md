# AWS Deployment Learning Reflection

## What Was Easy
- SSM replaced SSH deployment cleanly - improved security with no port exposure
- Docker Compose CloudWatch logging driver integration was straightforward
- Sentry Spring Boot starter (v7.3.0 Jakarta) matched Spring Boot 3.4.1 seamlessly
- ECR rollback via `.previous_image_tag` file provided simple, reliable fallback

## What Was Challenging
- Health checks via CloudFlare proxy hit bot protection (521/403) - required SSM localhost workaround
- Dual secrets management: SSM Parameter Store (encrypted) + .env file (runtime) diverged from spec
- Cron idempotence via grep-and-replace is fragile across multiple deployments
- BuildKit 0.17 conflicts forced `DOCKER_BUILDKIT=0` fallback, losing cache efficiency

## Key Learnings
- SSM Parameter Store + .env hybrid removes credential disk exposure while maintaining runtime flexibility
- Dual-stage deploy (nginx → backend) minimizes downtime - nginx serves 503 during backend restart
- Docker cleanup policy (`until=720h`) prevents EC2 disk exhaustion - not obvious until production
- CloudWatch metric filters on nginx logs provide zero-latency alerting at infrastructure layer
- Cron management via shell string manipulation is antipattern - use structured tools instead
- SSM command output requires jq parsing - adds safety but increases script verbosity

## Spec-Driven Process Feedback
- Research.md 90% accurate but underestimated SSH→SSM migration complexity
- Plan.md order (Sentry → CloudWatch → CI/CD) validated and worked well
- Flyway failure recovery documented but never tested in phase 5 - false confidence
- Sentry scope reduced from 21 handlers to 6 (401/500 only) - spec overcounted

## Pattern Recommendations
- Add "SSM deployment pattern" to be-async skill (command construction, output parsing)
- Document "dual-source secrets" (SSM + .env) tradeoff in be-config skill
- Create "CloudWatch metric filter patterns" reference (nginx log syntax is non-obvious)
- Add antipattern: grep-based cron editing → recommend structured tools instead

## Next Time
- Test Flyway failure recovery as part of verification phase
- Scope Sentry integration upfront - selective (401/500) reduces noise
- Consider AWS Secrets Manager over Parameter Store for rotation support
- Implement exponential backoff health checks from start (not fixed 5s intervals)
- Document Docker cleanup strategy in plan.md explicitly
