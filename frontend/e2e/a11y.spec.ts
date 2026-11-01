import { test, expect, type Route } from '@playwright/test';

import AxeBuilder from '@axe-core/playwright';

const runs = ['a11y-run', 'a11y-candidate'].map((id, index) => ({
  id,
  templateId: 'QF-001',
  state: 'SUCCEEDED',
  startedAt: '2026-10-31T12:00:00Z',
  durationMs: 1200,
  labels: {},
  parameters: { RATE: '5', DUR: '10' },
  metrics: {
    testRunDurationMs: 10000,
    iterationPercentiles: { p50: 10, p95: 25, p99: 40 },
    httpPercentiles: { p50: 12, p95: 30, p99: 50 },
    iterationCount: 10,
    rps: 5,
  },
  artifacts: [{
    artifactId: 'explain-plan',
    kind: 'explain-plan',
    contentType: 'application/json',
    description: 'Explain plan',
  }],
  index: index,
}));

const template = {
  id: 'QF-001',
  title: 'Filtered search',
  purpose: 'Measure filtered search.',
  hypothesis: 'A workload-shaping change affects latency.',
  parameters: [
    { name: 'RATE', type: 'integer', unit: 'req/s', min: 1, max: 10, allowed: ['5', '10'] },
    { name: 'OUT', type: 'string', unit: '', allowed: [] },
  ],
  estimatedDuration: '10 seconds',
  destructive: false,
  outputs: [],
  reportPath: '',
  planPaths: [],
};

const plan = {
  type: 'root',
  properties: {},
  children: [{
    type: 'Seq Scan',
    properties: { 'Plan Cost End': '20', 'Relation Name': 'products' },
    children: [],
    raw: 'Seq Scan on products',
  }],
  raw: 'Seq Scan on products',
};

test.describe('WCAG 2.2 AA accessibility checks', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/**', accessibilityApi);
  });

  test('axe-core finds no tagged WCAG A/AA violations on every application route', async ({ page }) => {
    const routes = [
      '/',
      '/experiments',
      '/runs',
      '/runs/a11y-run',
      '/plan/a11y-run/explain-plan',
      '/compare',
    ];

    for (const route of routes) {
      await page.goto(route);
      await expect(page.locator('main, [role="main"]')).toBeVisible();
      await expect(page.locator('main')).toHaveCSS('opacity', '1');
      await page.mouse.move(0, 0);
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
        .analyze();

      expect(results.violations, `Accessibility violations at ${route}`).toEqual([]);
    }
  });

  test('axe-core finds no tagged WCAG A/AA violations on every application route in dark mode', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('main')).toHaveCSS('opacity', '1');
    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    for (const route of ['/', '/experiments', '/runs', '/runs/a11y-run', '/plan/a11y-run/explain-plan', '/compare']) {
      await page.goto(route);
      await expect(page.locator('main, [role="main"]')).toBeVisible();
      await expect(page.locator('main')).toHaveCSS('opacity', '1');
      await page.mouse.move(0, 0);
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
        .analyze();

      expect(results.violations, `Accessibility violations at ${route} in dark mode`).toEqual([]);
    }
  });

  test('visible form fields have programmatic labels on catalog, history, run detail, plan, and compare', async ({ page }) => {
    const routes = [
      '/experiments',
      '/runs',
      '/runs/a11y-run',
      '/plan/a11y-run/explain-plan',
      '/compare',
    ];

    for (const route of routes) {
      await page.goto(route);
      const unlabeled = await page.locator('input:not([type="hidden"]), select, textarea').evaluateAll(elements =>
        elements
          .filter(element => {
            const control = element as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
            const labelledBy = control.getAttribute('aria-labelledby');
            return !control.labels?.length
              && !control.getAttribute('aria-label')
              && !(labelledBy && document.getElementById(labelledBy));
          })
          .map(element => `${element.tagName.toLowerCase()}${element.id ? `#${element.id}` : ''}`),
      );

      expect(unlabeled, `Unlabeled form fields at ${route}`).toEqual([]);
    }
  });

  test('keyboard focus starts at the visible skip link and retains a visible focus indicator', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');

    const skipLink = page.locator('.skip-link');
    await expect(skipLink).toBeFocused();
    const outlineStyle = await skipLink.evaluate(element => getComputedStyle(element).outlineStyle);
    expect(outlineStyle).not.toBe('none');
  });

  test('toast dismiss control has an accessible name', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.goto('/runs/a11y-run');
    await page.getByRole('button', { name: 'Copy run ID' }).click();

    const dismiss = page.getByRole('button', { name: 'Dismiss notification' });
    await expect(dismiss).toBeVisible();
    await dismiss.click();
    await expect(page.locator('.toast')).toHaveCount(0);
  });

  test('all images have alternative text or are explicitly decorative', async ({ page }) => {
    await page.goto('/');
    const imagesWithoutAlt = await page.locator('img').evaluateAll(images =>
      images.filter(image => image.getAttribute('alt') === null).length,
    );
    expect(imagesWithoutAlt).toBe(0);
  });
});

async function accessibilityApi(route: Route): Promise<void> {
  const path = new URL(route.request().url()).pathname;
  let payload: unknown;

  if (path === '/api/auth/session') {
    payload = { authEnabled: false, authenticated: false, operator: false, displayName: '' };
  } else if (path === '/api/lab/readiness') {
    payload = {
      api: { status: 'UP' },
      database: { status: 'UP', version: '17', rowCount: 100 },
      k6: { status: 'UP', path: 'k6' },
      fingerprint: { appCommit: 'test', javaVersion: '25', postgresVersion: '17', checkedAt: '2026-10-31T12:00:00Z' },
    };
  } else if (path === '/api/experiments') {
    payload = [template];
  } else if (path === '/api/runs') {
    payload = { content: runs, page: 0, size: 20, totalElements: runs.length, totalPages: 1, hasNext: false };
  } else if (path.endsWith('/hints')) {
    payload = [];
  } else if (path.endsWith('/plan')) {
    payload = plan;
  } else if (path === '/api/comparisons') {
    payload = [];
  } else if (path.startsWith('/api/runs/')) {
    const runId = path.split('/')[3];
    payload = runs.find(run => run.id === runId) ?? runs[0];
  } else {
    payload = {};
  }

  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) });
}
