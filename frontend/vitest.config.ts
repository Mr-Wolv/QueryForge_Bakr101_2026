import { defineConfig } from 'vitest/config';

// The Angular unit-test builder runs Vitest, but its default worker pool is
// unbounded. On this checkout the workers exhausted the V8 heap and the whole
// run aborted with "FATAL ERROR: Zone Allocation failed - process out of memory"
// (exit 134 / 0xC0000409) instead of reporting results. Bounding the fork pool
// keeps `npm test` reproducible without requiring NODE_OPTIONS.
export default defineConfig({
  test: {
    pool: 'forks',
    poolOptions: {
      forks: {
        minForks: 1,
        maxForks: 2,
        execArgv: ['--max-old-space-size=4096'],
      },
    },
  },
});
