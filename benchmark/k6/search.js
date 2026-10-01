import { check } from 'k6';
import { BASE_URL, getJson, filterParams, PAGE_SIZE, handleSummaryWith } from './common.js';

/**
 * QF-001 workload: filtered product search (Workload B).
 * Dataset size and run tag come from env: DATASET, OUT.
 * Load shape: constant arrival rate (independent of machine speed).
 *   RATE  = requests/second  (default 100)
 *   DUR   = duration          (default 2m)
 */
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    filtered_search: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DUR || '2m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const page = Math.floor(Math.random() * 4); // pages 0..3 within the hot set
  const res = getJson(
    `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=${page}&size=${PAGE_SIZE}`,
    { name: 'GET /api/products [filtered]' }
  );
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return handleSummaryWith(data, __ENV.OUT || 'qf001-search');
}
