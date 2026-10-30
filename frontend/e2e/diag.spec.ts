import { test, expect } from '@playwright/test';

/** Temporary diagnostic: exercise run detail repeatedly with request logging. */
test('diag run detail x8', async ({ page }) => {
  page.on('console', m => {
    if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 300));
  });
  page.on('requestfailed', r => {
    if (r.url().includes('/api/')) console.log('[requestfailed]', r.url(), r.failure()?.errorText);
  });
  for (let i = 0; i < 8; i++) {
    const t0 = Date.now();
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    const h1 = page.locator('h1');
    const ok = await h1.waitFor({ state: 'visible', timeout: 8000 }).then(() => true).catch(() => false);
    const artifacts = await page.locator('.artifact').count();
    console.log(`iter ${i}: h1=${ok} (${Date.now() - t0}ms) artifacts=${artifacts}`);
    expect(ok, `iteration ${i} run detail never rendered`).toBe(true);
  }
});
