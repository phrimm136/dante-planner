/**
 * k6 Sustained Load Test — LimbusPlanner Backend
 *
 * Purpose: Hold 1000 VUs for 10 minutes to catch slow leaks.
 *          The ramp-up test (load-test.js) showed no saturation at 1000 VUs.
 *          This test answers: does it STAY stable at that level over time?
 *
 * What this catches that ramp-up misses:
 *   - Memory leaks (JVM heap climbing over minutes)
 *   - Connection pool leaks (active count drifting upward)
 *   - GC pressure (latency spikes appearing periodically)
 *   - Thread handle exhaustion
 *
 * Run:
 *   k6 run -e BASE_URL=http://localhost -e AUTH_TOKEN=<jwt> scripts/load-test-sustained.js 2>&1 | tee sustained.txt
 *
 * AUTH_TOKEN (required):
 *   Sent as the accessToken cookie; drives the authenticated-reads group.
 *
 * Monitor during the 10-min hold (separate terminals):
 *   # These should stay FLAT. Any upward drift = leak.
 *   watch -n 2 'curl -s http://localhost:8080/actuator/metrics/hikari_pool_active  | grep value'
 *   watch -n 2 'curl -s http://localhost:8080/actuator/metrics/hikari_pool_pending | grep value'
 *   watch -n 2 'curl -s http://localhost:8080/actuator/metrics/jvm_memory_used_bytes | grep -A1 "area=\"heap\""'
 *   docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" danteplanner-backend danteplanner-mysql
 */

import { setup, runEndpoints, HTTP_THRESHOLDS } from './lib/load-test-shared.js';

export const options = {
  stages: [
    { duration: '60s', target: 1000 }, // ramp to tested peak
    { duration: '10m', target: 1000 }, // hold — watch for drift here
    { duration: '30s', target: 0    }, // cool down
  ],

  thresholds: HTTP_THRESHOLDS,
};

export { setup };
export default runEndpoints;
