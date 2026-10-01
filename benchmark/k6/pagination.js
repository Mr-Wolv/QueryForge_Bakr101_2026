import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import encoding from 'k6/encoding';
import { BASE_URL, getJson, filterParams, PAGE_SIZE, handleSummaryWith } from './common.js';

/**
 * QF-002 workload: OFFSET pagination vs KEYSET pagination at controlled depths.
 *
 * Each iteration performs ONE page request at a randomly chosen depth
 * (rows skipped: 0 / 1,000 / 10,000 / 50,000). Latency per depth is tracked
 * with dedicated Trend metrics so the summary JSON has a per-depth breakdown.
 *
 * The keyset cursor for each depth is derived once in setup() from the offset
 * endpoint (page = depth/size, last row), so both strategies are measured at
 * exactly the same logical position in the result set.
 */
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    pagination: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 50),
      timeUnit: '1s',
      duration: __ENV.DUR || '90s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const SIZE = PAGE_SIZE;
const DEPTHS = [0, 1000, 10000, 50000];

const trends = {};
for (const d of DEPTHS) {
  trends[`offset_${d}`] = new Trend(`offset_d${d}`);
  trends[`keyset_${d}`] = new Trend(`keyset_d${d}`);
}

const MODE = __ENV.MODE || 'offset';
const cursors = {}; // depth -> encoded cursor

export function setup() {
  // Derive a keyset cursor at each depth from the offset endpoint.
  for (const rows of DEPTHS) {
    const page = Math.floor(rows / SIZE);
    const res = getJson(
      `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=${page}&size=${SIZE}`
    );
    if (res.status !== 200) {
      throw new Error(`setup failed for depth ${rows}: HTTP ${res.status}`);
    }
    const body = res.json();
    const last = body.content[body.content.length - 1];
    cursors[rows] = last
      ? encoding.b64encode(`${Date.parse(last.createdAt)}:${last.id}`, 'urlraw')
      : null;
  }
  return { cursors };
}

export default function (data) {
  const depth = DEPTHS[Math.floor(Math.random() * DEPTHS.length)];
  let url;
  let trend;

  if (MODE === 'keyset') {
    const cursor = data.cursors[depth];
    url = `${BASE_URL}/api/products/keyset?${filterParams()}&sort=createdAt&dir=desc&size=${SIZE}` +
      (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
    trend = trends[`keyset_${depth}`];
  } else {
    const page = Math.floor(depth / SIZE);
    url = `${BASE_URL}/api/products?${filterParams()}&sort=createdAt&dir=desc&page=${page}&size=${SIZE}`;
    trend = trends[`offset_${depth}`];
  }

  const res = getJson(url);
  check(res, { 'status 200': (r) => r.status === 200 });
  if (trend) trend.add(res.timings.duration);
  sleep(0.05);
}

export function handleSummary(data) {
  return handleSummaryWith(data, __ENV.OUT || `qf002-${MODE}`);
}
