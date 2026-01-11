# API Migration Guide: Immutable Voting System

**Version**: 2026-01-10
**Breaking Changes**: Vote API contract, database schema
**Affected Endpoints**: `POST /api/planner/{id}/vote`, `POST /api/comment/{id}/vote`

---

## Summary

The voting system has been changed from **mutable** (toggle-based) to **immutable** (vote-once-only) to prevent voting manipulation and abuse. This is a **breaking change** that requires client-side updates.

---

## Breaking Changes

### 1. Vote API Contract Change

**Before (v1 - Deprecated)**:
```json
POST /api/planner/{id}/vote
{
  "voteType": "UP" | "DOWN" | null
}
```
- `voteType: null` → Remove existing vote (toggle behavior)
- `voteType: "UP"` → Add upvote or change to upvote
- `voteType: "DOWN"` → Add downvote or change to downvote

**After (v2 - Current)**:
```json
POST /api/planner/{id}/vote
{
  "voteType": "UP" | "DOWN"  // null no longer accepted
}
```
- `voteType: null` → **400 Bad Request** (validation error)
- `voteType: "UP"` → Add upvote (first vote only)
- `voteType: "DOWN"` → Add downvote (first vote only)
- Attempting to vote again → **409 Conflict** (VoteAlreadyExistsException)

---

### 2. Error Responses

#### New Error: 409 Conflict (Duplicate Vote)

**Response when user attempts to vote again**:
```json
HTTP 409 Conflict
{
  "errorCode": "VOTE_ALREADY_EXISTS",
  "message": "User has already voted on this planner",
  "timestamp": "2026-01-10T12:00:00Z"
}
```

**Error scenarios**:
- User voted UP, tries to vote UP again → 409
- User voted UP, tries to vote DOWN → 409
- User voted DOWN, tries to vote UP → 409
- User voted DOWN, tries to remove vote (null) → 400 (validation error)

#### Existing Error: 400 Bad Request (Validation)

**Response when voteType is null**:
```json
HTTP 400 Bad Request
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": {
    "voteType": "must not be null"
  },
  "timestamp": "2026-01-10T12:00:00Z"
}
```

---

## Migration Steps

### For Web Clients (React/Vue/Angular)

1. **Remove Vote Toggle Logic**:
   ```javascript
   // ❌ OLD (v1 - Remove this)
   function handleVote(direction) {
     const newVote = userVote === direction ? null : direction;
     api.post(`/api/planner/${id}/vote`, { voteType: newVote });
   }

   // ✅ NEW (v2 - Use this)
   function handleVote(direction) {
     // No toggle - vote once only
     api.post(`/api/planner/${id}/vote`, { voteType: direction });
   }
   ```

2. **Disable Vote Buttons After Voting**:
   ```javascript
   // ❌ OLD (v1)
   <button onClick={() => handleVote('UP')}>
     {userVote === 'UP' ? 'Remove Upvote' : 'Upvote'}
   </button>

   // ✅ NEW (v2)
   <button
     onClick={() => handleVote('UP')}
     disabled={userVote !== null}  // Disable if already voted
   >
     {userVote === 'UP' ? '✓ Upvoted' : userVote ? 'Already Voted' : 'Upvote'}
   </button>
   ```

3. **Handle 409 Conflict Error**:
   ```javascript
   // ✅ Add error handling for duplicate votes
   try {
     await api.post(`/api/planner/${id}/vote`, { voteType: 'UP' });
   } catch (error) {
     if (error.response?.status === 409) {
       showToast('You have already voted on this planner', 'error');
     }
   }
   ```

4. **Add Pre-Vote Warning (Recommended)**:
   ```javascript
   // ✅ Warn users before voting
   function handleVote(direction) {
     if (userVote === null) {
       showConfirmDialog(
         'Votes are permanent and cannot be changed. Continue?',
         () => api.post(`/api/planner/${id}/vote`, { voteType: direction })
       );
     }
   }
   ```

---

### For Mobile Clients (iOS/Android)

1. **Update Vote Type Enum**:
   ```swift
   // ❌ OLD (v1)
   enum VoteType: String, Codable {
       case up = "UP"
       case down = "DOWN"
       case none = null  // Remove this
   }

   // ✅ NEW (v2)
   enum VoteType: String, Codable {
       case up = "UP"
       case down = "DOWN"
       // 'none' is represented by absence of vote, not a value
   }
   ```

2. **Update Vote Request**:
   ```swift
   // ❌ OLD (v1)
   struct VoteRequest: Codable {
       let voteType: VoteType?  // Nullable
   }

   // ✅ NEW (v2)
   struct VoteRequest: Codable {
       let voteType: VoteType  // Non-nullable
   }
   ```

3. **Handle Errors**:
   ```swift
   // ✅ Handle 409 Conflict
   func vote(plannerId: String, voteType: VoteType) {
       apiClient.post("/api/planner/\(plannerId)/vote",
                      body: VoteRequest(voteType: voteType)) { result in
           switch result {
           case .success:
               // Update UI - disable vote buttons
               self.userVote = voteType
           case .failure(let error):
               if error.statusCode == 409 {
                   showAlert("Already Voted", "You have already voted on this planner")
               } else if error.statusCode == 400 {
                   showAlert("Invalid Request", "Vote type is required")
               }
           }
       }
   }
   ```

---

### For Third-Party Integrations (API Clients)

1. **Update Vote Logic**:
   - Remove all logic that sends `voteType: null`
   - Implement local tracking of user's vote state
   - Prevent duplicate vote attempts client-side (before API call)

2. **Backward Compatibility Strategy** (Temporary):
   ```javascript
   // ✅ Graceful degradation for v1 clients
   async function voteWithFallback(plannerId, voteType) {
     try {
       // Try v2 API (immutable voting)
       await api.post(`/api/planner/${plannerId}/vote`, { voteType });
     } catch (error) {
       if (error.response?.status === 400 && voteType === null) {
         // v2 API detected - null vote not allowed
         // Fallback: Just don't send the request
         console.warn('Vote removal no longer supported (v2 API)');
       } else if (error.response?.status === 409) {
         // User already voted - update local state
         console.warn('Duplicate vote attempt - already voted');
       } else {
         throw error;  // Unknown error
       }
     }
   }
   ```

---

## Database Schema Changes

**For self-hosted deployments running Flyway migrations**:

### Migration V018: Remove Vote Soft-Delete
- **CRITICAL**: This migration **permanently deletes** soft-deleted votes
- Soft-deleted votes represent "removed" votes under the old toggle system
- **Action Required**: Backup database before running migration

```sql
-- Migration V018__make_votes_immutable.sql
DELETE FROM planner_votes WHERE deleted_at IS NOT NULL;
DELETE FROM planner_comment_votes WHERE deleted_at IS NOT NULL;

ALTER TABLE planner_votes DROP COLUMN deleted_at;
ALTER TABLE planner_votes DROP COLUMN updated_at;
ALTER TABLE planner_comment_votes DROP COLUMN deleted_at;
ALTER TABLE planner_comment_votes DROP COLUMN updated_at;
```

### New Migrations
- **V019**: Create notifications table with UNIQUE constraint
- **V020**: Add moderation fields to planners
- **V021**: Add atomic notification flags (optimistic locking, recommended flag)

**Rollback Strategy**:
- Migrations are **immutable** - cannot be reversed via Flyway
- Rollback requires database restore from backup (pre-migration state)

---

## Testing Checklist

After migrating your client:

- [ ] Upvote button works on new planner (first vote)
- [ ] Downvote button works on new planner (first vote)
- [ ] Both vote buttons disabled after voting
- [ ] Clicking vote button again shows error message (not silent failure)
- [ ] Vote counter increments correctly (no longer decrements on toggle)
- [ ] 409 Conflict error handled gracefully (toast/alert, not crash)
- [ ] 400 Bad Request error handled (if accidentally sending null)
- [ ] Pre-vote warning displayed (if implemented)
- [ ] Vote state persists after page refresh

---

## Timeline

- **2026-01-10**: v2 API deployed (breaking change)
- **2026-01-24** (2 weeks): Deprecation warnings for v1 clients (if applicable)
- **2026-02-10** (1 month): v1 API endpoints removed (if separate versioning implemented)

---

## Support

For migration assistance:
- GitHub Issues: https://github.com/your-repo/issues
- Discord: #api-support channel
- Email: api-support@example.com

---

## Frequently Asked Questions

### Q: Can users correct accidental votes?
**A**: No, votes are permanent once cast. Pre-vote warning modal mitigates accidental votes.

### Q: What happens to existing votes when the system is upgraded?
**A**: Active votes are preserved. Soft-deleted votes (from old toggle system) are permanently deleted.

### Q: Can I implement a "grace period" for vote changes?
**A**: Not currently supported. Future enhancement: 5-minute undo window (tracked in issue #123).

### Q: Why this breaking change?
**A**: To prevent vote manipulation (bots toggling votes rapidly to inflate counters). Immutable voting ensures vote counts reflect unique user opinions.

### Q: How do I detect if the API is v1 or v2?
**A**: Send a test vote with `voteType: null`. If 400 Bad Request → v2 API. If success → v1 API (deprecated).

---

## Appendix: Full Error Code Reference

| HTTP Status | Error Code | Meaning | Client Action |
|-------------|-----------|---------|---------------|
| 400 | VALIDATION_ERROR | voteType is null or missing | Fix request payload |
| 401 | UNAUTHORIZED | User not authenticated | Redirect to login |
| 403 | FORBIDDEN | User banned or role insufficient | Show error message |
| 404 | NOT_FOUND | Planner deleted or unpublished | Remove from UI |
| 409 | VOTE_ALREADY_EXISTS | User already voted on this planner | Disable vote buttons, show "Already voted" |
| 500 | INTERNAL_SERVER_ERROR | Server error | Retry with exponential backoff |
