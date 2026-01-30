# Learning Reflection: User Moderation System

## What Was Easy

- Pattern cloning: Ban system copied timeout architecture seamlessly (UserRole hierarchy, exception structure, enforcement checks)
- Audit trail separation: Separate moderation_actions table avoided polluting User entity, batch-fetch optimization came naturally
- Frontend SSE integration: Account suspension event fit existing useSseConnection listener architecture, toast + profile refresh reused
- Database migrations: Adding nullable columns with no data migration risk, V033/V034/V035 strategy remained simple

## What Was Challenging

- CommentService enforcement gap: Existing code had zero timeout checks in comment posting (security bug), retrofitting required tracing all call paths
- Privacy hardening scope creep: Initial spec used internal IDs everywhere, discovered mid-implementation API exposed internals, required removing all IDs
- Role hierarchy consistency: Moderators timeout but can't ban, reconciling asymmetry across methods required careful authorization logic
- Reason dialog requirement: Spec marked reason as optional but audit trail integrity demands capturing intent, frontend evolved to require textareas
- Idempotent comment delete: Deleting already-deleted comments needed audit log entry, required logging before checking deletion status (inverse flow)

## Key Learnings

- Lazy enforcement validates better: Checking restrictions at action time reduced complexity, timeout auto-expiry works without background jobs
- Audit table design matters: Joining moderation_actions for reason lookup required batch-fetch optimization, indexed target_id + created_at DESC became essential
- SSE broadcasting to multiple connections is transparent: Broadcasting to 10 tabs completed in acceptable overhead, no connection state management needed
- Privacy-first architecture compounds during implementation: Removing internal IDs after building with them forced refactor of 5+ DTOs
- Exception hierarchy mirrors domain semantics: UserBannedException and UserTimedOutException both map to 403 but carry different error codes for frontend parsing
- Test mutation reveals hidden dependencies: Renaming timeout method signatures broke 13 test files, made scope of enforcement truly visible
- Spec ambiguities hide in operational semantics: "Reason optional" created API vs audit trail conflict, resolved by enforcing validation

## Spec-Driven Process Feedback

- Research.md gap analysis accurate but incomplete: Identified CommentService missing timeout checks, missed ban checks entirely
- Instructions.md phases well-ordered but underestimated frontend complexity: Database→Backend→API→Frontend worked, but frontend integration surfaced backend DTO privacy issues
- Plan.md lacked deployment sequencing: Didn't specify rollout order for new code vs schema, nullable column strategy sound but multi-region implications not addressed
- Code review caught issues research missed: Rate limiting, CSRF protection, migration foreign key constraints didn't appear in initial spec

## Pattern Recommendations

- Add enforcement consistency checklist: Document all service methods that mutate state, verify auth/restrictions/audit logging applied consistently
- Codify privacy boundary decision: Establish early whether entity fields expose internal IDs or public aliases, audit all response DTOs
- Document lazy enforcement trade-offs: Explain banned users discover ban on next write not login, SSE broadcast supplements but isn't guaranteed
- Create idempotent operation template: When operations must log audit trail regardless of state, log first then check state

## Next Time

- Read all service layer enforcement locations before implementation: Map all write operations early to surface gaps
- Require privacy audit before DTOs exist: Decide usernameSuffix vs userId before writing any DTO, prevents refactoring phase
- Use feature flags for enforcement changes: Adding checkUserNotBanned to critical paths created high-risk changes, gradual rollout would allow testing
- Distinguish "optional in spec" from "optional in implementation": Clarify intent of optional fields to prevent validation debates
