function origin(configured: string | undefined, fallback: string): string {
  return (configured ?? fallback).replace(/\/+$/, '')
}

// Defaults address the local stack only. The deployed hostnames are deliberately absent from
// this repository: it is public, and the environment they name authenticates through a stub IdP
// that mints a token for any address it is handed. Naming them here would publish the way in.
// Supply them from the environment (scripts/ops/access/staging-env.sh reads them from Secrets
// Manager) when running against the edge.
export const APP_URL = origin(process.env.E2E_APP_URL, 'http://localhost')
export const API_URL = origin(process.env.E2E_API_URL, 'http://localhost')

// Oregon serves reads from the pool it writes to; Seoul serves them from an asynchronously
// replicated copy and is therefore the only region that registers the GTID gate and the
// replica-miss re-check (docs/multi-region-request-paths.md §3, §5). The local two-region rig
// publishes them on these ports (docker-compose.multiregion.yml).
export const OREGON_API = origin(process.env.E2E_API_OREGON_URL, 'http://localhost:8081')
export const SEOUL_API = origin(process.env.E2E_API_SEOUL_URL, 'http://localhost:8082')

export const IDP_URL = origin(process.env.E2E_IDP_URL, 'http://localhost:3000')

/**
 * Cloudflare Access sits in front of every deployed hostname, so a request that does not carry a
 * service token is challenged before it reaches the origin. Absent locally, where there is no
 * Access in the path.
 */
export const ACCESS_HEADERS: Record<string, string> =
  process.env.CF_ACCESS_CLIENT_ID && process.env.CF_ACCESS_CLIENT_SECRET
    ? {
        'CF-Access-Client-Id': process.env.CF_ACCESS_CLIENT_ID,
        'CF-Access-Client-Secret': process.env.CF_ACCESS_CLIENT_SECRET,
      }
    : {}

/** How long a safety property is re-asserted for, spanning the replication window it must survive. */
export const SAFETY_WINDOW_MS = Number(process.env.E2E_SAFETY_WINDOW_MS ?? 5_000)
export const SAFETY_PROBE_INTERVAL_MS = Number(process.env.E2E_SAFETY_PROBE_INTERVAL_MS ?? 150)

/** How long a liveness property may take to become true before the suite calls it broken. */
export const LIVENESS_DEADLINE_MS = Number(process.env.E2E_LIVENESS_DEADLINE_MS ?? 60_000)
export const LIVENESS_POLL_INTERVAL_MS = Number(process.env.E2E_LIVENESS_POLL_INTERVAL_MS ?? 250)

/**
 * A request's own GTIDs span the handful of transactions it commits; the fallback capture is the
 * primary's whole executed set. The cap separates the two without depending on an exact count.
 */
export const OWN_GTID_TRANSACTION_CAP = 16

/** Above this share of fallback captures the read gate is wide enough to cost every read a WAN hop. */
export const MAX_GTID_FALLBACK_RATIO = 0.05
