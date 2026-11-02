import { test, expect, type Page } from '@playwright/test';

/**
 * Default-stack acceptance tests.
 *
 * These run against the unauthenticated Docker Compose stack (default profile):
 * nginx → Spring Boot backend → PostgreSQL. No Keycloak, no auth, no messaging.
 *
 * Prerequisites:
 *   docker compose --profile app up -d --build
 *   curl http://127.0.0.1:8081/actuator/health | jq -r '.status'   # expect UP
 *
 * Run:
 *   cd frontend
 *   npx playwright test e2e/default-stack.spec.ts
 */

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:8080';
const API_URL = process.env.PLAYWRIGHT_API_URL || 'http://127.0.0.1:8081';

async function waitForBackend(page: Page, retries = 60) {
  for (let i = 0; i < retries; i++) {
    try {
      const res = await page.request.get(`${API_URL}/actuator/health`);
      if (res.ok()) {
        const body = await res.json();
        if (body.status === 'UP') return true;
      }
    } catch {
      // ignore
    }
    await page.waitForTimeout(1000);
  }
  return false;
}

async function waitForDataset(page: Page, retries = 120) {
  for (let i = 0; i < retries; i++) {
    try {
      const res = await page.request.get(`${API_URL}/api/lab/readiness`);
      if (res.ok()) {
        const body = await res.json();
        if (body.database?.rowCount > 0) return body.database.rowCount;
      }
    } catch {
      // ignore
    }
    await page.waitForTimeout(1000);
  }
  return 0;
}

async function initializeDataset(page: Page) {
  const res = await page.request.post(`${API_URL}/api/lab/initialize?rows=10000`);
  expect(res.ok()).toBe(true);
  return res.json();
}

test.describe('default Docker stack (no auth)', () => {
  test('backend health is UP', async ({ page }) => {
    const ready = await waitForBackend(page);
    expect(ready).toBe(true);
  });

  test('homepage loads with brand and nav', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.brand')).toContainText('QueryForge');
    await expect(page.locator('nav a:has-text("Catalog")')).toBeVisible();
  });

  test('catalog lists experiment templates and can initialize dataset', async ({ page }) => {
    await page.goto('/experiments');
    await expect(page.locator('h1')).toContainText('Choose a question worth measuring.');
    await expect(page.locator('.card').first()).toBeVisible();

    // Ensure dataset is initialized for subsequent tests
    const rows = await waitForDataset(page);
    if (rows === 0) {
      await initializeDataset(page);
    }
  });

  test('run history loads after dataset is ready', async ({ page }) => {
    await page.goto('/runs');
    await expect(page.locator('h1')).toContainText('Run history');
  });

  test('compare page loads without auth', async ({ page }) => {
    await page.goto('/compare');
    await expect(page.locator('h1')).toContainText('Compare runs');
  });
});
