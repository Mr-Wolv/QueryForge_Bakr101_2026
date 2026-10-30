import { test, expect, Page } from '@playwright/test';

/**
 * Routing coverage: every affordance that looks clickable must actually navigate.
 *
 * This suite exists because a standalone Angular component silently ignores
 * `routerLink` when `RouterLink` is missing from its `imports`, which renders
 * nav items as plain, inert text. Every test below asserts a URL change (or a
 * real `href`), never just that text is visible.
 */

async function expectRoute(page: Page, path: string) {
  await page.waitForURL(u => u.pathname + u.search === path, { timeout: 10000 });
}

test.describe('navigation', () => {
  test('no anchor is inert: every visible link has a usable href', async ({ page }) => {
    for (const path of ['/', '/experiments', '/runs', '/compare']) {
      await page.goto(path);
      const inert = await page.locator('a:visible').evaluateAll(anchors =>
        anchors
          .filter(a => !a.getAttribute('href') || a.getAttribute('href') === '#')
          .map(a => (a.textContent || '').trim())
      );
      expect(inert, `inert anchors on ${path}`).toEqual([]);
    }
  });

  test('shell links navigate without a full page reload', async ({ page }) => {
    await page.goto('/');
    let reloads = 0;
    page.on('load', () => reloads++);
    await page.getByRole('link', { name: 'Runs', exact: true }).click();
    await expectRoute(page, '/runs');
    expect(reloads, 'SPA navigation must not reload the document').toBe(0);
  });

  test('top nav routes to every top-level page', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: 'Catalog', exact: true }).click();
    await expectRoute(page, '/experiments');
    await expect(page.locator('h1')).toContainText('Choose a question worth measuring.');

    await page.getByRole('link', { name: 'Runs', exact: true }).click();
    await expectRoute(page, '/runs');
    await expect(page.locator('h1')).toContainText('Run history');

    await page.getByRole('link', { name: 'Compare', exact: true }).click();
    await expectRoute(page, '/compare');
    await expect(page.locator('h1')).toContainText('Compare runs');

    await page.getByRole('link', { name: 'Overview', exact: true }).click();
    await expectRoute(page, '/');
    await expect(page.locator('h1')).toContainText('QueryForge');
  });

  test('brand link returns home from a deep route', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    await page.getByRole('banner').getByRole('link', { name: 'QueryForge' }).click();
    await expectRoute(page, '/');
    await expect(page.locator('h1')).toContainText('QueryForge');
  });

  test('active nav item is marked for the current route', async ({ page }) => {
    for (const [path, label] of [
      ['/', 'Overview'],
      ['/experiments', 'Catalog'],
      ['/runs', 'Runs'],
      ['/compare', 'Compare'],
    ]) {
      await page.goto(path);
      await expect(page.getByRole('link', { name: label, exact: true })).toHaveClass(/active/);
      await expect(page.locator('header nav a.active')).toHaveCount(1);
    }
  });

  test('catalog card links to run history pre-filtered by template', async ({ page }) => {
    await page.goto('/experiments');
    const cardLink = page.locator('.card a').first();
    await cardLink.click();
    await page.waitForURL(/\/runs\?template=/, { timeout: 10000 });
    await expect(page.locator('h1')).toContainText('Run history');
    await expect(page.locator('.filter select').first()).not.toHaveValue('');
    await expect(page.locator('.list .row').first()).toBeVisible();
  });

  test('run row opens detail, and the back link returns to history', async ({ page }) => {
    await page.goto('/runs');
    const firstRow = page.locator('.list .row').first();
    const runId = (await firstRow.locator('.id').textContent())!.trim();
    await firstRow.click();
    await page.waitForURL(new RegExp(`/runs/${runId}$`), { timeout: 10000 });
    await expect(page.locator('h1')).toContainText(runId);

    await page.locator('a.back').click();
    await expectRoute(page, '/runs');
    await expect(page.locator('h1')).toContainText('Run history');
  });

  test('plan explorer is reachable from run detail and can go back', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    await page.locator('a.plan-link').first().click();
    await page.waitForURL(/\/plan\//, { timeout: 10000 });
    await expect(page.locator('h1')).toContainText('Explain plan');

    await page.locator('a.back').click();
    await expectRoute(page, '/runs');
  });

  test('theme toggle switches the data-theme attribute', async ({ page }) => {
    await page.goto('/');
    const before = await page.locator('html').getAttribute('data-theme');
    const getBackgroundTokens = () => page.evaluate(() => ({
      canonical: getComputedStyle(document.documentElement).getPropertyValue('--color-background').trim(),
      legacy: getComputedStyle(document.documentElement).getPropertyValue('--bg').trim(),
    }));
    const lightTokens = await getBackgroundTokens();
    expect(lightTokens.legacy).toBe(lightTokens.canonical);
    const next = before === 'dark' ? 'light' : 'dark';
    const toggle = page.getByRole('button', { name: /switch to (dark|light) mode/i });
    await toggle.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', next);
    const darkTokens = await getBackgroundTokens();
    expect(darkTokens.legacy).toBe(darkTokens.canonical);
    await expect(toggle).toHaveAttribute('aria-label', `Switch to ${before} mode`);
    await expect.poll(() => page.evaluate(() => localStorage.getItem('queryforge-theme'))).toBe(next);

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', next);
    // Restore so other tests and screenshots keep their default.
    await page.getByRole('button', { name: /switch to (dark|light) mode/i }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', before!);
  });
});
