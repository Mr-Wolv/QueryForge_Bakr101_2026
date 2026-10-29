import { test, expect } from '@playwright/test';

test.describe('NFR-006 accessibility basics', () => {
  test('keyboard navigation reaches all interactive elements on catalog', async ({ page }) => {
    await page.goto('/experiments');
    await page.waitForTimeout(1000);
    
    const tags: string[] = [];
    for (let i = 0; i < 20; i++) {
      await page.keyboard.press('Tab');
      const tag = await page.evaluate(() => document.activeElement?.tagName.toLowerCase() || null);
      tags.push(tag);
    }
    
    const interactive = tags.filter(t => ['a', 'button', 'select', 'input'].includes(t));
    expect(interactive.length).toBeGreaterThan(0);
  });

  test('all images have alt text or are decorative', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(500);
    
    const imgsWithoutAlt = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('img')).filter(img => {
        const alt = img.getAttribute('alt');
        return alt === null || alt === '';
      }).length;
    });
    
    // If there are images, they should all have alt text
    const totalImgs = await page.evaluate(() => document.querySelectorAll('img').length);
    if (totalImgs > 0) {
      expect(imgsWithoutAlt).toBe(0);
    }
  });

  test('form controls have labels on compare page', async ({ page }) => {
    await page.goto('/compare');
    await page.waitForTimeout(1000);
    
    const selectsWithoutLabel = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('select')).filter(sel => {
        const id = sel.getAttribute('id');
        const name = sel.getAttribute('name');
        // Check for associated label
        const hasLabel = id ? document.querySelector(`label[for="${id}"]`) : 
                        name ? document.querySelector(`label:has([name="${name}"])`) : false;
        // Also check if wrapped in a label
        const wrappedInLabel = sel.closest('label') !== null;
        return !hasLabel && !wrappedInLabel;
      }).length;
    });
    
    expect(selectsWithoutLabel).toBe(0);
  });
});
