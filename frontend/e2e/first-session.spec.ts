import { test, expect } from '@playwright/test';

const templates = [{
  id: 'QF-001',
  title: 'Filtered search: baseline vs composite index',
  purpose: 'Compare filtered search before and after a composite index.',
  hypothesis: 'A workload-shaped index should reduce filtered-search latency.',
  parameters: [
    { name: 'RATE', type: 'integer', unit: 'req/s', min: 1, max: 200, allowed: null },
    { name: 'DUR', type: 'duration', unit: 'seconds', min: 10, max: 600, allowed: null },
    { name: 'OUT', type: 'string', unit: 'tag', min: null, max: null, allowed: null },
  ],
  estimatedDuration: '~3m',
  destructive: false,
  outputs: ['k6 summary JSON', 'EXPLAIN plan'],
  reportPath: 'docs/experiments/QF-001-baseline.md',
  planPaths: [],
}];

test.describe('first session', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/**', async route => {
      const request = route.request();
      const url = new URL(request.url());

      if (request.method() === 'GET' && url.pathname === '/api/auth/session') {
        await route.fulfill({ json: {
          authEnabled: false,
          authenticated: false,
          operator: true,
          displayName: '',
        } });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/runs') {
        await route.fulfill({ json: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false } });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/lab/readiness') {
        await route.fulfill({ json: {
          api: { status: 'UP' },
          database: { status: 'UP', version: '17', rowCount: 1000000 },
          k6: { status: 'UP', path: 'k6' },
          fingerprint: { appCommit: 'test', javaVersion: '25', postgresVersion: '17', checkedAt: '2026-01-01T00:00:00Z' },
        } });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/experiments') {
        await route.fulfill({ json: templates });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/runs/qf001-first-session') {
        await route.fulfill({ json: {
          id: 'qf001-first-session',
          templateId: 'QF-001',
          state: 'RUNNING',
          startedAt: '2026-01-01T00:00:00Z',
          durationMs: 0,
          labels: {},
          parameters: { RATE: '25', DUR: '45' },
          metrics: { testRunDurationMs: 0, iterationPercentiles: {}, httpPercentiles: {}, iterationCount: 0, rps: 0 },
          artifacts: [],
        } });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/runs/qf001-first-session/artifacts') {
        await route.fulfill({ json: [] });
        return;
      }
      if (request.method() === 'GET' && url.pathname === '/api/runs/qf001-first-session/hints') {
        await route.fulfill({ json: [] });
        return;
      }
      if (request.method() === 'POST' && url.pathname === '/api/runs') {
        await route.fulfill({ status: 201, json: { id: 'qf001-first-session', state: 'QUEUED' } });
        return;
      }
      if (request.method() === 'POST' && url.pathname === '/api/runs/qf001-first-session/start') {
        await route.fulfill({ status: 202, json: { id: 'qf001-first-session', state: 'RUNNING' } });
        return;
      }
      await route.continue();
    });
  });

  test('empty home guides the user to a real experiment question', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Start with a question. Leave with evidence.' })).toBeVisible();
    await expect(page.getByRole('link', { name: /QF-001[\s\S]*What does a workload-shaped index buy/ })).toHaveAttribute('href', /\/experiments#qf-001/);
    await expect(page.getByText('Environment · API UP · PostgreSQL 17 · k6 UP')).toBeVisible();

    await page.getByRole('link', { name: 'Explore experiments' }).click();
    await expect(page).toHaveURL(/\/experiments$/);
    await expect(page.locator('#qf-001')).toBeVisible();
  });

  test('brand link returns to the homepage from a deep route', async ({ page }) => {
    await page.goto('/experiments');
    await page.getByRole('banner').getByRole('link', { name: 'QueryForge' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Start with a question. Leave with evidence.' })).toBeVisible();
  });

  test('workload inputs update the preview and start the configured run', async ({ page }) => {
    await page.goto('/experiments');
    const experiment = page.locator('#qf-001');
    const rate = experiment.locator('#qf-001-rate');
    const duration = experiment.locator('#qf-001-dur');
    await rate.fill('25');
    await duration.fill('45');
    await expect(experiment.locator('.run-preview')).toContainText('RATE=25 req/s');
    await expect(experiment.locator('.run-preview')).toContainText('DUR=45 seconds');

    const createRequest = page.waitForRequest(request =>
      request.method() === 'POST' && new URL(request.url()).pathname === '/api/runs'
    );
    const startRequest = page.waitForRequest(request =>
      request.method() === 'POST' && new URL(request.url()).pathname === '/api/runs/qf001-first-session/start'
    );
    await experiment.getByRole('button', { name: 'Start experiment' }).click();
    const create = await createRequest;
    await expect(create.postDataJSON()).toMatchObject({ templateId: 'QF-001', parameters: { RATE: '25', DUR: '45' } });
    await startRequest;
    await expect(page).toHaveURL(/\/runs\/qf001-first-session$/);
  });
});
