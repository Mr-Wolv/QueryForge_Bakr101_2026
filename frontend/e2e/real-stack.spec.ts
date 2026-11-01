import { test, expect, type BrowserContext, type Page } from '@playwright/test';

/**
 * Real-stack acceptance tests.
 *
 * These are NOT the fixture-backed UI tests in `first-session.spec.ts`. Every
 * assertion here runs against the authenticated Compose stack: nginx, the real
 * Spring Boot backend, PostgreSQL, Keycloak, k6 and the on-disk evidence
 * directories. They are opt-in because that stack must already be running:
 *
 *   QF_SPRING_PROFILE=local-auth \
 *   docker compose --profile app --profile authenticated --profile monitoring up -d
 *   cd frontend && QF_REAL_STACK=1 PLAYWRIGHT_BASE_URL=http://localhost:8080 npx playwright test e2e/real-stack.spec.ts
 */

const REAL_STACK = process.env.QF_REAL_STACK === '1';
const VIEWER = { username: 'viewer', password: 'viewer-local-only' };
const OPERATOR = { username: 'operator', password: 'operator-local-only' };

test.describe('authenticated local stack', () => {
  test.skip(!REAL_STACK, 'Set QF_REAL_STACK=1 with the authenticated Compose stack running.');

  // Security headers are a property of the deployed nginx edge, not of the
  // `ng serve` dev server the fixture suite runs against.
  test('frontend responses include baseline security headers', async ({ request }) => {
    const response = await request.get('/');

    expect(response.status()).toBe(200);
    expect(response.headers()).toMatchObject({
      'x-content-type-options': 'nosniff',
      'x-frame-options': 'DENY',
      'referrer-policy': 'strict-origin-when-cross-origin',
      'permissions-policy': 'camera=(), microphone=(), geolocation=()',
    });
  });

  test('Keycloak shows the branded login and account flows', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.locator('#kc-form-login')).toBeVisible();
    const heading = page.getByRole('heading', { name: 'Sign in to QueryForge' });
    await expect(heading).toBeVisible();
    await expect(heading).toHaveCSS('color', 'rgb(24, 52, 95)');
    await expect(page.getByRole('link', { name: /create an account/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /forgot.*password/i })).toBeVisible();

    await page.getByRole('link', { name: /create an account/i }).click();
    await expect(page.locator('input[name="username"]')).toBeVisible();
    await expect(page.locator('input[name="email"]')).toBeVisible();

    await page.goto('/');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.getByRole('link', { name: /forgot.*password/i }).click();
    await expect(page.locator('form')).toBeVisible();
    await expect(page.locator('input[name="username"]')).toBeVisible();
  });

  test('viewer authenticates, can read, and cannot mutate', async ({ page }) => {
    await signIn(page, VIEWER);

    await expect(page.locator('.identity')).toContainText('Read-only');

    const list = await page.request.get('/api/runs');
    expect(list.status()).toBe(200);

    await page.goto('/experiments');
    await expect(page.getByText('An experiment operator must start runs.').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Start experiment/ })).toHaveCount(0);

    // A valid CSRF token proves the refusal is authorization, not CSRF.
    const csrf = await csrfToken(page.context());
    const refused = await page.request.post('/api/runs', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { templateId: 'QF-001', parameters: { RATE: '5', DUR: '10' } },
    });
    expect(refused.status()).toBe(403);
    expect((await refused.json()).detail).toBe('Operator access required');
  });

  test('a state-changing request without a CSRF token is rejected as such', async ({ page }) => {
    await signIn(page, OPERATOR);

    const refused = await page.request.post('/api/runs', {
      data: { templateId: 'QF-001', parameters: { RATE: '5', DUR: '10' } },
    });
    expect(refused.status()).toBe(403);
    expect((await refused.json()).detail).toBe('CSRF token missing or invalid');
  });

  test('malformed JSON is a client error, not a server error', async ({ page }) => {
    await signIn(page, OPERATOR);
    const csrf = await csrfToken(page.context());

    const response = await page.request.post('/api/runs', {
      headers: { 'X-XSRF-TOKEN': csrf, 'Content-Type': 'application/json' },
      data: '{"templateId": "QF-001", "parameters": ',
    });

    expect(response.status()).toBe(400);
    const problem = await response.json();
    expect(problem.title).toBe('Malformed Request Body');
    expect(problem.detail).not.toContain('Unexpected server error');
  });

  test('operator creates, starts, streams progress for and cancels a run', async ({ page }) => {
    test.setTimeout(2 * 60_000);
    await signIn(page, OPERATOR);
    const csrf = await csrfToken(page.context());

    // Create without starting so the browser is subscribed before the first event.
    const created = await page.request.post('/api/runs', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { templateId: 'QF-001', parameters: { RATE: '5', DUR: '600' } },
    });
    expect(created.status()).toBe(201);
    const run = await created.json();
    expect(run.state).toBe('QUEUED');

    await page.goto(`/runs/${run.id}`);
    await expect(page.getByRole('button', { name: 'Start run' })).toBeVisible();
    await page.getByRole('button', { name: 'Start run' }).click();

    // Named SSE events drive the live panel; without them it never renders.
    await expect(page.locator('.live')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.sse-badge')).toContainText('Live: RUNNING', { timeout: 30_000 });
    await expect(page.locator('.log-message').filter({ hasText: 'k6' }).first()).toBeVisible({ timeout: 30_000 });

    await page.getByRole('button', { name: 'Cancel run' }).click();
    await expect(page.locator('.sse-badge')).toContainText('Live: CANCELLED', { timeout: 60_000 });

    const persisted = await page.request.get(`/api/runs/${run.id}`);
    expect(persisted.status()).toBe(200);
    expect((await persisted.json()).state).toBe('CANCELLED');
  });

  test('operator completes a real run and opens its artifacts', async ({ page }) => {
    test.setTimeout(6 * 60_000);
    await signIn(page, OPERATOR);

    await page.goto('/experiments');
    await page.locator('#qf-001-rate').fill('5');
    await page.locator('#qf-001-dur').fill('10');

    const createResponse = page.waitForResponse(
      response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/runs',
    );
    await page.locator('#qf-001').getByRole('button', { name: /Start experiment/ }).click();
    const create = await createResponse;
    expect(create.status()).toBe(201);
    const runId = (await create.json()).id as string;
    expect(runId).toBeTruthy();

    await expect(page).toHaveURL(new RegExp(`/runs/${escapeRegExp(runId!)}$`));
    await expect(page.locator('.meta')).toContainText('State: SUCCEEDED', { timeout: 4 * 60_000 });
    await expect(page.locator('.artifacts')).toContainText('k6 summary JSON');

    // Real artifact download through the run-scoped route.
    const artifactHref = await page.locator('.artifact').first().getAttribute('href');
    const download = await page.request.get(artifactHref!);
    expect(download.status()).toBe(200);
    expect(download.headers()['content-type']).toContain('application/json');
  });

  test('a historical run opens its explain plan through the run-scoped route', async ({ page }) => {
    await signIn(page, OPERATOR);

    const response = await page.request.get('/api/runs?page=0&size=100');
    expect(response.status()).toBe(200);
    const runs = (await response.json()).content as any[];
    const withPlan = runs.find(run => run.artifacts.some((a: any) => a.kind === 'explain-plan'));
    test.skip(!withPlan, 'No historical run with an explain-plan artifact is present.');

    const artifact = withPlan!.artifacts.find((a: any) => a.kind === 'explain-plan');
    await page.goto(`/plan/${encodeURIComponent(withPlan!.id)}/${encodeURIComponent(artifact.artifactId)}`);

    await expect(page.getByRole('heading', { name: 'Explain plan' })).toBeVisible();
    await expect(page.locator('.plan-explorer')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Raw' })).toBeVisible();
    await expect(page.locator('.error')).toHaveCount(0);
    await expect(page.locator('.meta')).toContainText(/\d+ nodes/);
  });

  test('operator compares two recorded runs', async ({ page }) => {
    await signIn(page, OPERATOR);

    await page.goto('/compare');
    const selects = page.locator('select');
    await expect(selects).toHaveCount(2);

    const options = await selects.first().locator('option').allTextContents();
    const runIds = options.filter(text => text !== 'Select run').slice(0, 2);
    test.skip(runIds.length < 2, 'At least two runs are required for a comparison.');

    await selects.nth(0).selectOption(runIds[0]);
    await selects.nth(1).selectOption(runIds[1]);
    await page.getByRole('button', { name: 'Compare' }).click();

    await expect(page.locator('.result table')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.result table tbody tr').first()).toBeVisible();
    await expect(page.locator('.error')).toHaveCount(0);
  });

  test('sign-out ends the session', async ({ page }) => {
    await signIn(page, OPERATOR);

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();

    const after = await page.request.get('/api/runs');
    expect(after.status()).toBe(401);
    expect((await after.json()).detail).toBe('Authentication required');
  });
});

async function signIn(page: Page, account: { username: string; password: string }): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in' }).click();

  // A live Keycloak SSO session can complete the redirect without showing the
  // credential form; wait for either outcome instead of assuming the form.
  await Promise.race([
    page.locator('#username').waitFor({ state: 'visible', timeout: 60_000 }),
    page.locator('.identity').waitFor({ state: 'visible', timeout: 60_000 }),
  ]);

  if (await page.locator('#username').isVisible()) {
    await page.locator('#username').fill(account.username);
    await page.locator('#password').fill(account.password);
    await page.getByRole('button', { name: /sign in/i }).click();
  }

  // The forced success URL is the SPA root.
  await page.waitForURL(url => !url.pathname.startsWith('/id/'), { timeout: 60_000 });
  await expect(page.locator('.identity')).toContainText(
    account.username === OPERATOR.username ? 'Operator' : 'Read-only',
    { timeout: 30_000 },
  );
}

async function csrfToken(context: BrowserContext): Promise<string> {
  const cookie = (await context.cookies()).find(c => c.name === 'XSRF-TOKEN');
  expect(cookie, 'Spring must set the XSRF-TOKEN cookie for the SPA to echo').toBeTruthy();
  return decodeURIComponent(cookie!.value);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
