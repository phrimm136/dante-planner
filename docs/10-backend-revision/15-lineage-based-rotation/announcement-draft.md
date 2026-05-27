# Re-login Announcement Draft

Posted at rollout step 3 (before flipping `jwt.rotation.lineage-enabled=true` in production).
Use the `announcement` skill to publish across all 5 JSON files with auto-translation.
Keep tone consistent with existing announcements.

## EN (authoritative)

**Title:** Security update — you may need to sign in again

**Body:** We've upgraded our login system to better protect your account. After this
update, you may be signed out once and asked to log in again. This is a one-time step —
your planners and data are unaffected.

## Notes for translation
- "signed out once" → emphasize one-time, not recurring
- "planners and data are unaffected" → reassure no data loss
- Match the calmer/formal register used in prior KR/JP/CN announcements

## Timing
- Post at rollout step 3, before the production flag flip
- Keep `jwt.rotation.legacy-admit-enabled=true` for 7 days after the flip so existing
  sessions migrate without a forced logout; the announcement covers the residual cases
  (server restart, expired legacy window)
