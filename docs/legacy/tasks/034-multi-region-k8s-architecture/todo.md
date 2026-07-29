1. The tombstone write sits inside deletePlanner's @Transactional, before the MySQL commit. The phase contract
  ("synchronously before the HTTP response") is satisfied — but this ordering introduces a new fail-closed mode:
  if the DB commit rolls back after the Redis write, a live entity gets 404'd for up to 1h (until TTL). Writing
  the tombstone after commit instead (via afterCommit synchronization or in the controller) would keep every
  failure mode fail-open — its only risk is the already-accepted ~ms ghost-read window that Phase-3's primary
  re-check backstops.
