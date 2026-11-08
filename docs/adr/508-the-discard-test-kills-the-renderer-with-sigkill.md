# 508 the-discard-test-kills-the-renderer-with-sigkill
epic: none · pr: none

## Decisions
- @e2e @durability @mobile — The discard-durability tests end the renderer with an OS `SIGKILL` on its process, in a browser the test launches with exactly one renderer (`--renderer-process-limit=1`, spare renderer off), instead of a CDP `Page.crash`. `Page.crash` raises `SIGTRAP`, so the kernel hands the renderer to the host's core-dump handler, and the browser reports the crash only after the dump is written: measured on a `systemd-coredump` host, about 1.3 s for one crash and 4.6 to 8.2 s with four workers crashing at once, against the test's 5 s wait, which matched CI's intermittent "waiting for event "crash"" timeouts on main. With core dumps disabled the same crash took about 0.1 s, and a `SIGKILL` (which never dumps core) took 34 to 61 ms with four workers. A `SIGKILL` is also how a phone's low-memory killer discards a tab, so the test's shape moves closer to the case it guards. Re-check: `coredumpctl list --since -5min` lists no `chrome-headless-shell` entries after a local run of the discard tests.
  REJECTED: running the browser under `ulimit -c 0` — whether the CI host's handler honours the limit is unverified, and the test would still depend on host crash-handling configuration.
  REJECTED: raising the 5 s wait — it hides a delay that scales with how many renderers crash at once, so the flake returns under more parallelism.
  INVARIANT: the kill targets the only renderer in the test's own browser — enforced by the helper, which fails the test when the browser reports any other number of renderers.

## Takeaway
- takeaway: a test that crashes a process inherits the host's crash handling; kill it with a signal that bypasses that handling, or the test measures the host instead of the app.
