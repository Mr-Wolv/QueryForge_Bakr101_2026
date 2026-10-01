import { check } from 'k6';
import { BASE_URL, getJson, filterParams, PAGE_SIZE, handleSummaryWith } from './common.js';

/**
 * QF-003 workload: concurrency ladder (Phase 7).
 * Closed-model load: constant VUs (true concurrent users), one level per run.
 *   VUS=10 ./k6 run concurrency.js   -> 10 concurrent users, DUR seconds
 * Repeat with VUS=25, 50, 100, 200. Throughput emerges from latency.
 */
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    fixed_users: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DUR || '45s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = getJson(
    `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=0&size=${PAGE_SIZE}`,
    { name: 'GET /api/products [filtered]' }
  );
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return handleSummaryWith(data, __ENV.OUT || `qf003-vus${__ENV.VUS || 10}`);
}
