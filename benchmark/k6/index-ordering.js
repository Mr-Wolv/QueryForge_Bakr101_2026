import { check } from 'k6';
import encoding from 'k6/encoding';
import { BASE_URL, getJson, filterParams, PAGE_SIZE, handleSummaryWith } from './common.js';

/**
 * QF-004 workload: index column ordering.
 *
 * The index state is applied outside the request path (scripts/apply-index.sh or
 * the operator's documented candidate step). INDEX is therefore a label that
 * must match the state actually present in the target database; the manifest's
 * captured pg_indexes list is the authoritative evidence.
 *
 *   INDEX = filter-first-only | both-indexes
 *   MODE  = keyset | search
 *   RATE  = requests/second (default 10)
 */
const MODE = __ENV.MODE || 'keyset';
const INDEX = __ENV.INDEX || 'filter-first-only';
const DEPTH = 50000;
const SIZE = PAGE_SIZE;

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    index_ordering: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DUR || '60s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (MODE !== 'keyset') return { cursor: null };
  const page = Math.floor(DEPTH / SIZE);
  const res = getJson(
    `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=${page}&size=${SIZE}`
  );
  if (res.status !== 200) {
    throw new Error(`setup failed for depth ${DEPTH}: HTTP ${res.status}`);
  }
  const body = res.json();
  const last = body.content[body.content.length - 1];
  return {
    cursor: last
      ? encoding.b64encode(`${Date.parse(last.createdAt)}:${last.id}`, 'urlraw')
      : null,
  };
}

export default function (data) {
  const url = MODE === 'keyset'
    ? `${BASE_URL}/api/products/keyset?${filterParams()}&sort=createdAt&dir=desc&size=${SIZE}` +
      (data.cursor ? `&cursor=${encodeURIComponent(data.cursor)}` : '')
    : `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=0&size=${SIZE}`;

  const res = getJson(url, { name: `GET /api/products [QF-004 ${MODE}/${INDEX}]` });
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return handleSummaryWith(data, __ENV.OUT || `qf004-${MODE}-${INDEX}`);
}
