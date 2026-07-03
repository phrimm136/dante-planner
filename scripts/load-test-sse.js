/**
 * k6 SSE Load Test — LimbusPlanner Backend
 *
 * Purpose: Find max concurrent SSE connections before nginx or backend fails.
 *          SSE is the binding DAU constraint — not DB throughput.
 *          Each VU holds exactly one SSE connection for its iteration.
 *
 * How it works:
 *   - Each VU opens one long-lived SSE connection and blocks on it (up to 120s)
 *   - Ramping VUs = ramping concurrent SSE connections
 *   - Failures appear when nginx connection limit or per-planner cap (500) is hit
 *
 * Per-planner cap: PlannerCommentSseService enforces 500 max per planner (FIFO eviction).
 *   setup() fetches multiple planners and VUs distribute randomly across them.
 *   With 3 planners: up to 1500 SSE connections before cap is hit.
 *   If fewer than 3 planners exist, you'll see evictions before the nginx limit.
 *
 * Prerequisites:
 *   - Backend running with loadtest profile (same as load-test.js)
 *   - At least 3 published planners in the DB for full-range testing
 *
 * Run:
 *   k6 run -e BASE_URL=http://localhost --out json=sse.json scripts/load-test-sse.js
 *
 * Monitor (separate terminal):
 *   docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" danteplanner-nginx danteplanner-backend
 *
 * Reading results:
 *   - http_req_connecting climbing → nginx struggling to accept connections
 *   - http_req_waiting climbing  → backend struggling to set up SSE emitters
 *   - http_req_failed rising     → connections being refused or evicted
 *   - The VU count where errors first appear = your concurrent SSE ceiling
 */

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100  }, // baseline — confirm SSE works
    { duration: '60s', target: 500  }, // first per-planner boundary (500)
    { duration: '60s', target: 1000 }, // beyond single-planner cap
    { duration: '60s', target: 1500 }, // near nginx ceiling (~1365 on t3.small)
    { duration: '30s', target: 0    }, // cool down
  ],

  thresholds: {
    // http_req_failed intentionally omitted — SSE timeouts are expected and healthy.
    // Real failures (connection refused/reset) show up as connecting/waiting spikes.
    'http_req_connecting': ['p(95)<2000'], // nginx accepting connections
    'http_req_waiting':    ['p(95)<2000'], // backend setting up SSE emitter
  },
};

/**
 * Fetches published planner IDs to distribute SSE connections across.
 * __ENV read here — not available at module top-level in k6 v1.x.
 */
export function setup() {
  const base = __ENV.BASE_URL || 'http://localhost:8080';

  const res = http.get(`${base}/api/planner/md/published?size=10`);
  if (res.status !== 200) {
    throw new Error('setup: cannot fetch published planners');
  }

  const body  = JSON.parse(res.body);
  const ids   = (body.content || []).map((p) => p.id);

  if (ids.length === 0) {
    throw new Error('setup: no published planners found — need at least one');
  }
  if (ids.length < 3) {
    console.warn(
      `setup: only ${ids.length} published planner(s). ` +
      `Per-planner cap (500) will be hit at ${ids.length * 500} concurrent connections.`
    );
  }

  return { base, ids };
}

/**
 * Each VU opens one SSE connection and holds it.
 * http.get blocks until the response completes or the timeout fires —
 * for SSE that means the connection is held open for up to 120s.
 */
export default function (data) {
  const { base, ids } = data;

  // Distribute across planners to stay under 500/planner limit
  const plannerId = ids[Math.floor(Math.random() * ids.length)];

  const res = http.get(`${base}/api/planner/${plannerId}/comments/events`, {
    timeout: '120s',
    headers: { Accept: 'text/event-stream' },
  });

  // SSE never completes — k6 hits the 120s timeout, which IS the success condition.
  // A connection that was refused or reset would return immediately (duration < 1s).
  check(res, {
    'SSE held open': (r) => r.timings.duration > 100000,
  });
}
