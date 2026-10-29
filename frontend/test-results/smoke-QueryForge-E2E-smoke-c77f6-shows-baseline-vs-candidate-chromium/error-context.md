# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: smoke.spec.ts >> QueryForge E2E smoke >> compare page loads and shows baseline vs candidate
- Location: e2e\smoke.spec.ts:35:7

# Error details

```
Error: page.goto: Target page, context or browser has been closed
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | test.describe('QueryForge E2E smoke', () => {
  4  |   test.beforeEach(async ({ page }) => {
> 5  |     await page.goto('/');
     |                ^ Error: page.goto: Target page, context or browser has been closed
  6  |   });
  7  | 
  8  |   test('homepage loads with brand and nav', async ({ page }) => {
  9  |     await expect(page.locator('.brand')).toContainText('QueryForge');
  10 |     await expect(page.locator('nav a:has-text("Catalog")')).toBeVisible();
  11 |   });
  12 | 
  13 |   test('catalog lists experiment templates', async ({ page }) => {
  14 |     await page.goto('/experiments');
  15 |     await expect(page.locator('h1')).toContainText('Experiment catalog');
  16 |     // At least one card should be visible (templates are hardcoded)
  17 |     await expect(page.locator('.card').first()).toBeVisible();
  18 |     // Each card has a title like "QF-001 — ..."
  19 |     await expect(page.locator('.card h2').first()).toHaveText(/^QF-\d+/);
  20 |   });
  21 | 
  22 |   test('run history loads existing benchmark runs', async ({ page }) => {
  23 |     await page.goto('/runs');
  24 |     await expect(page.locator('h1')).toContainText('Run history');
  25 |     await expect(page.locator('.row').first()).toBeVisible();
  26 |   });
  27 | 
  28 |   test('run detail shows metrics and artifacts for existing run', async ({ page }) => {
  29 |     await page.goto('/runs/qf001-baseline-search-1M-r10');
  30 |     await expect(page.locator('h1')).toContainText('qf001-baseline-search-1M-r10');
  31 |     await expect(page.locator('text=Artifacts')).toBeVisible();
  32 |     await expect(page.locator('.artifact').first()).toBeVisible();
  33 |   });
  34 | 
  35 |   test('compare page loads and shows baseline vs candidate', async ({ page }) => {
  36 |     await page.goto('/compare');
  37 |     await expect(page.locator('h1')).toContainText('Compare runs');
  38 |     // Compare component uses ngModel with name attributes
  39 |     await expect(page.locator('select[name="a"]')).toBeVisible();
  40 |     await expect(page.locator('select[name="b"]')).toBeVisible();
  41 |     // Wait for async run loading to populate options
  42 |     await page.locator('select[name="a"] option').nth(1).waitFor({ state: 'attached' });
  43 |     await page.locator('select[name="b"] option').nth(1).waitFor({ state: 'attached' });
  44 |     // Select first available non-empty option from each dropdown, ensuring they differ
  45 |     const optionsA = await page.locator('select[name="a"] option').all();
  46 |     const optionsB = await page.locator('select[name="b"] option').all();
  47 |     const valA = await optionsA[1].getAttribute('value');
  48 |     const valB = optionsA.length > 2 ? await optionsA[2].getAttribute('value') : valA;
  49 |     await page.locator('select[name="a"]').selectOption(valA!);
  50 |     await page.locator('select[name="b"]').selectOption(valB!);
  51 |     await page.locator('button[type="submit"]').click();
  52 |     await expect(page.locator('.result')).toBeVisible({ timeout: 10000 });
  53 |     await expect(page.locator('.result h2')).toContainText('vs');
  54 |   });
  55 | 
  56 |   test('artifact download returns content', async ({ page }) => {
  57 |     await page.goto('/runs/qf001-baseline-search-1M-r10');
  58 |     const firstArtifact = page.locator('.artifact').first();
  59 |     await expect(firstArtifact).toBeVisible();
  60 |     // The href points to /api/artifacts/...
  61 |     const href = await firstArtifact.getAttribute('href');
  62 |     expect(href).toBeTruthy();
  63 |     // Verify the artifact endpoint returns content
  64 |     const resp = await page.request.get(href!);
  65 |     expect(resp.ok()).toBe(true);
  66 |   });
  67 | 
  68 |   test('navigation is keyboard accessible', async ({ page }) => {
  69 |     await page.goto('/experiments');
  70 |     // Tab to the first launch button
  71 |     await page.keyboard.press('Tab');
  72 |     // We should be able to tab through interactive elements
  73 |     const focused = await page.evaluate(() => {
  74 |       const el = document.activeElement;
  75 |       return el ? (el as HTMLElement).tagName.toLowerCase() : null;
  76 |     });
  77 |     expect(['a', 'button', 'select', 'input']).toContain(focused);
  78 |   });
  79 | });
  80 | 
```