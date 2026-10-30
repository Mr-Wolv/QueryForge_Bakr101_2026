// Temporary diagnostic: reproduce data-loading failures with request logging.
import { chromium } from '@playwright/test';

const BASE = 'http://localhost:4200';
const pages = ['/experiments', '/runs', '/runs/qf001-baseline-search-1M-r10', '/compare'];

const browser = await chromium.launch();
for (let round = 1; round <= 3; round++) {
  for (const path of pages) {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    const apiCalls = [];
    page.on('request', r => {
      if (r.url().includes('/api/')) apiCalls.push({ url: r.url(), start: Date.now(), done: false });
    });
    page.on('response', r => {
      const hit = apiCalls.find(c => c.url === r.url() && !c.done);
      if (hit) { hit.done = true; hit.status = r.status(); hit.ms = Date.now() - hit.start; }
    });
    page.on('requestfailed', r => {
      const hit = apiCalls.find(c => c.url === r.url() && !c.done);
      if (hit) { hit.done = true; hit.failed = r.failure()?.errorText; hit.ms = Date.now() - hit.start; }
    });
    page.on('console', m => { if (m.type() === 'error') console.log(`  [console.error] ${m.text().slice(0, 200)}`); });

    const t0 = Date.now();
    // Mimic the Playwright spec: land on the home page first, then route.
    await page.goto(BASE + '/', { waitUntil: 'load' });
    await page.goto(BASE + path, { waitUntil: 'load' });
    await page.waitForTimeout(4000);
    const summary = apiCalls.map(c => `${c.status ?? 'NO-RESPONSE'}${c.failed ? ' FAILED:' + c.failed : ''} ${c.ms}ms ${new URL(c.url).pathname}`).join(' | ');
    console.log(`round ${round} ${path} (${Date.now() - t0}ms): ${summary || '(no api calls)'}`);
    await ctx.close();
  }
}
await browser.close();
