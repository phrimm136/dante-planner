/**
 * k6 Load Test — LimbusPlanner Backend
 *
 * Purpose: Find the DB connection pool saturation point.
 *          Replaces config-based guesses with real throughput numbers.
 *
 * Prerequisites:
 *   1. Install k6:  https://k6.io/docs/get-started/installation/
 *   2. Start backend with load-test profile:
 *        java -jar backend/build/libs/*.jar --spring.profiles.active=dev,loadtest
 *      OR add to docker-compose backend service temporarily:
 *        environment: - SPRING_PROFILES_ACTIVE=dev,loadtest
 *   3. At least one published planner must exist in the DB
 *      (otherwise only /config and /epithets run — still valid but less useful)
 *
 * Run:
 *   k6 run scripts/load-test.js
 *   k6 run -e BASE_URL=http://localhost:8080 scripts/load-test.js
 *   k6 run -e BASE_URL=http://localhost:8080 -e AUTH_TOKEN=<jwt> scripts/load-test.js
 *
 * AUTH_TOKEN (optional):
 *   Enables authenticated endpoint tests. Get from browser:
 *     DevTools > Application > Cookies > accessToken value
 *   Note: single token = single user. Fine for pool saturation testing.
 *
 * Reading k6 output:
 *   - p95 latency climbing while VU count is still low → pool is the wall
 *   - p95 stable then sudden spike at a stage → hard limit hit
 *   - http_req_failed rate rising → server rejecting (pool exhaustion or OOM)
 *
 * Monitor in a separate terminal during the test:
 *   watch -n 1 'curl -s http://localhost:8080/actuator/metrics/hikari_pool_pending | grep value'
 *   watch -n 1 'curl -s http://localhost:8080/actuator/metrics/hikari_pool_active  | grep value'
 *   docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" backend mysql
 */

import { setup, runEndpoints } from './lib/load-test-shared.js';

export const options = {
    stages: [
      { duration: '15s', target: 10  },  // warm up — JVM + pool init
      { duration: '30s', target: 50  },  // light  — baseline latency
      { duration: '60s', target: 200 },  // moderate — watch p95 here
      { duration: '60s', target: 500 },  // heavy — pool saturation expected
      { duration: '60s', target: 1000 }, // find the hard ceiling
      { duration: '15s', target: 0    }, // cool down
    ],

  thresholds: {
    'http_req_duration':                          ['p(95)<2000', 'p(99)<5000'],
    'http_req_duration{group:::public reads}':    ['p(95)<1500'],
    'http_req_duration{group:::authenticated reads}': ['p(95)<2000'],
    'http_req_failed':                            ['rate<0.05'],
  },
};

export { setup };
export default runEndpoints;
