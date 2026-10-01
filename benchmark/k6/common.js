import http from 'k6/http';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const jsonParams = {
  headers: { Accept: 'application/json' },
};

export const PAGE_SIZE = 50;

export function getJson(url, tags = {}) {
  return http.get(url, Object.assign({}, jsonParams, { tags }));
}

// Deterministic filter variation so repeated iterations don't hit identical plans
export function filterParams() {
  const category = (__VU % 5) + 1; // VU id cycles the category
  return `category=${category}&status=ACTIVE&minPrice=100&maxPrice=500`;
}

// Writes human summary to stdout and full metrics JSON to benchmark/results/<fileBase>.json
// NOTE: run k6 from the repository root (paths are resolved from the CWD):
//   tools/k6/k6.exe run -e OUT=foo benchmark/k6/search.js
export function handleSummaryWith(data, fileBase) {
  const out = {};
  out['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  out[`benchmark/results/${fileBase}.json`] = JSON.stringify(data, null, 2);
  return out;
}
