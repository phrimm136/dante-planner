/** Mirrors shared/exception/DegradationErrorConstants — the codes a 503 degradation body carries. */
export const DEGRADATION_CODES = {
  write: 'WRITE_TEMPORARILY_UNAVAILABLE',
  auth: 'AUTH_TEMPORARILY_UNAVAILABLE',
  rateLimit: 'RATE_LIMIT_TEMPORARILY_UNAVAILABLE',
  // What a client actually receives for any backend 5xx: the edge runs
  // proxy_intercept_errors and rewrites the body, so the typed codes above are
  // observable only from inside the cluster.
  edgeRewritten: 'BACKEND_UNAVAILABLE',
} as const

/**
 * One value per row of the degradation ladder (docs/multi-region-request-paths.md §10). The runner
 * injects exactly one failure at a time over SSM and names it in E2E_CHAOS; every other scenario in
 * the ladder skips, because an assertion about an injection that is not active proves nothing.
 */
export const CHAOS_MODES = {
  primaryBlackholed: 'primary',
  poolExhausted: 'pool',
  bulkheadSaturated: 'bulkhead',
  authRedisDown: 'auth-redis',
  rateLimitRedisDown: 'ratelimit-redis',
  flywayMigrating: 'flyway',
} as const

export type ChaosMode = (typeof CHAOS_MODES)[keyof typeof CHAOS_MODES]

export function chaosMode(): string | undefined {
  return process.env.E2E_CHAOS
}

export function chaosActive(mode: ChaosMode): boolean {
  return chaosMode() === mode
}

export interface DegradationBody {
  code?: string
  message?: string
}

export const BLACKLIST_SKIP_METRIC = 'blacklist_check_skipped'
