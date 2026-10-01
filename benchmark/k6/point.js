import { check } from 'k6';
import { BASE_URL, getJson, handleSummaryWith } from './common.js';

/**
 * Workload A — point lookup: GET /api/products/{id}.
 * Ids are drawn from the whole keyspace so the PK index is exercised cold-ish,
 * including absent ids (404 path) occasionally. 404 counts as an expected response,
 * not an error, so the check accepts 200 or 404.
 */
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    point_lookup: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DUR || '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
};

const MAX_ID = Number(__ENV.MAX_ID || 1000000);

export default function () {
  const id = 1 + Math.floor(Math.random() * MAX_ID);
  const res = getJson(`${BASE_URL}/api/products/${id}`, { name: 'GET /api/products/{id}' });
  check(res, {
    'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
}

export function handleSummary(data) {
  return handleSummaryWith(data, __ENV.OUT || 'qf000a-point-lookup-1M');
}
