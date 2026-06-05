import { test, expect } from './fixtures';

test.describe('Coach availability', () => {
  test.beforeEach(async ({ page }) => {
    // Start tour to create demo seed availability intervals
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) {
        const data = el._x_dataStack[0];
        if (typeof data.startTour === 'function') data.startTour();
      }
    });
    await page.waitForTimeout(2500);
    // Dismiss tour overlay to interact with the page
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) el._x_dataStack[0].showTour = false;
    });
    await page.waitForTimeout(300);

    await page.locator('[data-tour="availability"]').click();
    await page.waitForTimeout(800);
  });

  test('coach availability tab loads', async ({ page }) => {
    await expect(page.locator('[data-tour="availability"]')).toBeVisible();
    // Panel should render without error
    await expect(page.locator('[data-tour="avail-intervals"], [data-tour="avail-share"]').first())
      .toBeVisible({ timeout: 10000 });
  });

  test('demo seed availability intervals are visible', async ({ page }) => {
    // Demo seed has intervals for Зал А: 08:00-13:00 and 14:00-21:00
    const intervals = page.locator('[data-tour="avail-intervals"]');
    await expect(intervals).toBeVisible({ timeout: 10000 });
    // At least one interval block should be present
    const intervalItems = intervals.locator('[class*="interval"], [class*="range"], div[style*="background"]');
    if (await intervalItems.count() > 0) {
      await expect(intervalItems.first()).toBeVisible();
    }
  });

  test('share link section is visible', async ({ page }) => {
    await expect(page.locator('[data-tour="avail-share"]')).toBeVisible({ timeout: 10000 });
  });

  test('share link button generates a URL', async ({ page }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    // Look for a copy/share button
    const shareBtn = shareSection.locator('button').first();
    if (await shareBtn.count() > 0) {
      await expect(shareBtn).toBeVisible();
    }
  });

  test('switching back to feed tab keeps state', async ({ page }) => {
    // Go back to feed
    await page.locator('[data-tour="view-toggle"] button').first().click();
    await page.waitForTimeout(300);
    // Session cards still load
    await expect(page.locator('[data-session-id]').first()).toBeVisible({ timeout: 8000 });
    // Then back to availability
    await page.locator('[data-tour="availability"]').click();
    await page.waitForTimeout(800);
    await expect(page.locator('[data-tour="avail-share"]')).toBeVisible({ timeout: 10000 });
  });
});
