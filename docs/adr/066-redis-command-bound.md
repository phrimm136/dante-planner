# 066 redis-command-bound

## Decisions

- @redis @degradation — Every Redis role — rate limit, SSE fan-out, auth blacklist,
  cross-region — takes the same three-second command bound, applied where the connection
  factory and the raw client are constructed rather than where they are used. Lettuce leaves
  an unconfigured connection on a minute-long command timeout, both on the Spring Data client
  configuration and on a `RedisURI` parsed from a `redis://` string, so a bound is the
  difference between a slow call and a held thread.
  REJECTED: bounding only the cross-region role — a caller that degrades gracefully degrades
  no faster than its slowest command, so an unreachable local Redis holds request threads for
  the minute default and exhausts the pool on exactly the dependency the degradation ladder
  promises to survive.
  REJECTED: configuring the bound at each call site — a bound every construction site must
  remember is one the next construction site forgets, and its absence is invisible until the
  dependency is unreachable.
- @redis @config — A role that needs a different bound changes the shared construction
  helper, which is the accepted cost of having one place a bound can be set.

## Takeaway

- takeaway: a default that only hurts when a dependency is already failing is a default no
  test and no review will catch; the cheap fix is to make the safe value unavoidable by
  having exactly one place the object can be built.
