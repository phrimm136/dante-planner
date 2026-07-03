/**
 * Shared k6 helpers for the HTTP load tests
 * (load-test.js and load-test-sustained.js).
 */

import http from 'k6/http';
import { check, group } from 'k6';

/**
 * Runs once before test starts. Fetches a published planner ID
 * so per-planner endpoints have a valid target.
 * Also reads __ENV here — k6 globals are not available at module top-level.
 */
export function setup() {
  const base  = __ENV.BASE_URL   || 'http://localhost:8080';
  const token = __ENV.AUTH_TOKEN || '';

  const res = http.get(`${base}/api/planner/md/published?size=1`);
  if (res.status !== 200) {
    console.warn('setup: could not fetch published planners — per-planner tests will be skipped');
    return { base, token, publishedId: null };
  }
  const body = JSON.parse(res.body);
  const first = body.content && body.content[0];
  if (!first) {
    console.warn('setup: published list is empty — per-planner tests will be skipped');
  }
  return { base, token, publishedId: first ? first.id : null };
}

/**
 * VU iteration: exercises the public read endpoints, plus the
 * authenticated ones when AUTH_TOKEN is set.
 */
export function runEndpoints(data) {
  const { base, token, publishedId } = data;
  const params = token ? { headers: { Authorization: `Bearer ${token}` } } : {};

  group('public reads', () => {
    // Config — lightweight, likely no heavy query. Baseline reference point.
    check(http.get(`${base}/api/planner/md/config`), {
      'config 200': (r) => r.status === 200,
    });

    // Published list — paginated DB read, likely JOIN for upvote/view counts.
    check(http.get(`${base}/api/planner/md/published?size=20&page=0`), {
      'published list 200': (r) => r.status === 200,
    });

    // Recommended — DB read with sorting/filtering logic.
    check(http.get(`${base}/api/planner/md/recommended?size=20&page=0`), {
      'recommended list 200': (r) => r.status === 200,
    });

    if (publishedId) {
      // Single planner detail — DB read by ID, possible eager load of relations.
      check(http.get(`${base}/api/planner/md/published/${publishedId}`), {
        'published detail 200': (r) => r.status === 200,
      });

      // Comments tree — self-referential query, potentially expensive on large comment sets.
      check(http.get(`${base}/api/planner/${publishedId}/comments`), {
        'comments 200': (r) => r.status === 200,
      });

      // View count — only public write endpoint. Deduped by device/user,
      // so repeated hits from same VU may skip DB after the first. Still worth including.
      check(http.post(`${base}/api/planner/md/${publishedId}/view`, null), {
        'view count 2xx': (r) => r.status < 300,
      });
    }
  });

  // Authenticated reads — only runs if AUTH_TOKEN is set.
  if (token) {
    group('authenticated reads', () => {
      // Epithets — small lookup table, requires auth.
      check(http.get(`${base}/api/user/epithets`, params), {
        'epithets 200': (r) => r.status === 200,
      });

      // Own planners list — DB read scoped to user ID.
      check(http.get(`${base}/api/planner/md`, params), {
        'own planners 200': (r) => r.status === 200,
      });

      // Notification inbox — likely indexed by (user_id, is_read).
      check(http.get(`${base}/api/notifications/inbox?size=20`, params), {
        'inbox 200': (r) => r.status === 200,
      });

      // Unread count — COUNT query, should be fast if indexed.
      check(http.get(`${base}/api/notifications/unread-count`, params), {
        'unread count 200': (r) => r.status === 200,
      });
    });
  }
}
