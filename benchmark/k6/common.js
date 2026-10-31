import http from 'k6/http';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const jsonParams = {
  headers: {
    Accept: 'application/json',
    // The orchestrator injects a process-local token so the bundled runner can
    // read the benchmark target while the API stays authenticated.
    ...(__ENV.QF_INTERNAL_TOKEN ? { 'X-QueryForge-Internal-Token': __ENV.QF_INTERNAL_TOKEN } : {}),
  },
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

// Writes human summary to stdout and full metrics JSON to SUMMARY_PATH when the
// orchestrator provides one, otherwise benchmark/results/<fileBase>.json.
// NOTE: run k6 from the repository root when invoking it manually:
//   tools/k6/k6.exe run -e OUT=foo benchmark/k6/search.js
export function handleSummaryWith(data, fileBase) {
  const out = {};
  out['stdout'] = textSummary(data, { indent: ' ', enableColors: true });
  const summaryPath = __ENV.SUMMARY_PATH || `benchmark/results/${fileBase}.json`;
  out[summaryPath] = JSON.stringify(data, null, 2);
  return out;
}
