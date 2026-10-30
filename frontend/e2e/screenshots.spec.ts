import { test, expect } from '@playwright/test';

test.describe('Local UI screenshots', () => {
  test('capture overview page', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(500); // let fonts/render settle
    await page.screenshot({ path: 'docs/screenshots/overview.png', fullPage: true });
  });

  test('capture catalog page', async ({ page }) => {
    await page.goto('/experiments');
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'docs/screenshots/catalog.png', fullPage: true });
  });

  test('capture run detail page', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'docs/screenshots/run-detail.png', fullPage: true });
  });

  test('capture compare page', async ({ page }) => {
    await page.goto('/compare');
    await page.waitForTimeout(500);
    await page.locator('select[name="a"] option').nth(1).waitFor({ state: 'attached' });
    await page.locator('select[name="b"] option').nth(1).waitFor({ state: 'attached' });
    const optionsA = await page.locator('select[name="a"] option').all();
    const valA = await optionsA[1].getAttribute('value');
    const valB = optionsA.length > 2 ? await optionsA[2].getAttribute('value') : valA;
    await page.locator('select[name="a"]').selectOption(valA!);
    await page.locator('select[name="b"]').selectOption(valB!);
    await page.locator('button[type="submit"]').click();
    await page.waitForTimeout(1000);
    await page.screenshot({ path: 'docs/screenshots/compare.png', fullPage: true });
  });
});
