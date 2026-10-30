import { test, expect } from '@playwright/test';

test.describe('QueryForge E2E smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('homepage loads with brand and nav', async ({ page }) => {
    await expect(page.locator('.brand')).toContainText('QueryForge');
    await expect(page.locator('nav a:has-text("Catalog")')).toBeVisible();
  });

  test('catalog lists experiment templates', async ({ page }) => {
    await page.goto('/experiments');
    await expect(page.locator('h1')).toContainText('Choose a question worth measuring.');
    // At least one card should be visible (templates are hardcoded)
    await expect(page.locator('.card').first()).toBeVisible();
    await expect(page.locator('.experiment-id').first()).toHaveText(/^QF-\d+/);
  });

  test('run history loads existing benchmark runs', async ({ page }) => {
    await page.goto('/runs');
    await expect(page.locator('h1')).toContainText('Run history');
    await expect(page.locator('.row').first()).toBeVisible();
  });

  test('run detail shows metrics and artifacts for existing run', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    await expect(page.locator('h1')).toContainText('qf001-baseline-search-1M-r10');
    await expect(page.locator('text=Artifacts')).toBeVisible();
    await expect(page.locator('.artifact').first()).toBeVisible();
  });

  test('compare page loads and shows baseline vs candidate', async ({ page }) => {
    await page.goto('/compare');
    await expect(page.locator('h1')).toContainText('Compare runs');
    // Compare component uses ngModel with name attributes
    await expect(page.locator('select[name="a"]')).toBeVisible();
    await expect(page.locator('select[name="b"]')).toBeVisible();
    // Wait for async run loading to populate options
    await page.locator('select[name="a"] option').nth(1).waitFor({ state: 'attached' });
    await page.locator('select[name="b"] option').nth(1).waitFor({ state: 'attached' });
    // Select first available non-empty option from each dropdown, ensuring they differ
    const optionsA = await page.locator('select[name="a"] option').all();
    const optionsB = await page.locator('select[name="b"] option').all();
    const valA = await optionsA[1].getAttribute('value');
    const valB = optionsA.length > 2 ? await optionsA[2].getAttribute('value') : valA;
    await page.locator('select[name="a"]').selectOption(valA!);
    await page.locator('select[name="b"]').selectOption(valB!);
    await page.locator('button[type="submit"]').click();
    await expect(page.locator('.result')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.result h2.title')).toContainText('vs');
    await expect(page.locator('.result table')).toBeVisible();
    await expect(page.locator('.result table tbody th').first()).not.toBeEmpty();
  });

  test('artifact download returns content', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    const firstArtifact = page.locator('.artifact').first();
    await expect(firstArtifact).toBeVisible();
    // The href points to /api/artifacts/...
    const href = await firstArtifact.getAttribute('href');
    expect(href).toBeTruthy();
    // Verify the artifact endpoint returns content
    const resp = await page.request.get(href!);
    expect(resp.ok()).toBe(true);
  });

  test('explain plan artifact opens the plan explorer', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    const planLink = page.locator('.plan-link').first();
    await expect(planLink).toBeVisible();
    await planLink.click();
    await expect(page).toHaveURL(/\/plan\//);
    await expect(page.locator('app-plan-explorer .tree')).toBeVisible();
    await expect(page.locator('app-plan-node').first()).toBeVisible();
  });

  test('plan tree nodes expand and collapse with keyboard-operable controls', async ({ page }) => {
    await page.goto('/runs/qf001-baseline-search-1M-r10');
    await page.locator('.plan-link').first().click();

    const node = page.locator('.tree [role="treeitem"][aria-expanded]').first();
    const toggle = node.locator(':scope > button.head');
    const children = node.locator(':scope > .children');
    await expect(node).toHaveAttribute('aria-expanded', 'true');
    await expect(children).toBeVisible();

    await toggle.click();
    await expect(node).toHaveAttribute('aria-expanded', 'false');
    await expect(children).toHaveCount(0);

    await toggle.focus();
    await page.keyboard.press('Space');
    await expect(node).toHaveAttribute('aria-expanded', 'true');
    await expect(children).toBeVisible();
  });

  test('navigation is keyboard accessible', async ({ page }) => {
    await page.goto('/experiments');
    // Tab to the first launch button
    await page.keyboard.press('Tab');
    // We should be able to tab through interactive elements
    const focused = await page.evaluate(() => {
      const el = document.activeElement;
      return el ? (el as HTMLElement).tagName.toLowerCase() : null;
    });
    expect(['a', 'button', 'select', 'input']).toContain(focused);
  });
});
