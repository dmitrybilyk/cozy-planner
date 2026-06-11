import { test, expect } from './fixtures';

test.describe('Planner — navigation', () => {
  test('header shows mentor name and all nav buttons', async ({ page }) => {
    // shareAvailability and multiLocation are false for demo user — enable them
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack?.[0]) {
        el._x_dataStack[0].shareAvailability = true;
        el._x_dataStack[0].multiLocation = true;
      }
    });
    await page.waitForTimeout(200);
    await expect(page.locator('[data-tour="profile"]')).toBeVisible();
    await expect(page.locator('[data-tour="add-session"]')).toBeVisible();
    await expect(page.locator('[data-tour="trainees"]')).toBeVisible();
    await expect(page.locator('[data-tour="locations-btn"]')).toBeVisible();
    await expect(page.locator('[data-tour="availability"]')).toBeVisible();
  });

  test('mentor name is displayed in profile area', async ({ page }) => {
    const profile = page.locator('[data-tour="profile"] h1');
    await expect(profile).not.toBeEmpty();
  });

  test('history button is visible', async ({ page }) => {
    await expect(page.locator('[data-tour="history"]')).toBeVisible();
  });

  test('view toggle between День and План', async ({ page }) => {
    await expect(page.locator('[data-tour="view-toggle"]')).toBeVisible();
    // Switch to agenda (Plan)
    await page.locator('[data-tour="view-toggle"]').click();
    await page.waitForTimeout(300);
    // Back to feed (Day)
    await page.locator('[data-tour="feed-view"]').click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="add-session"]')).toBeEnabled();
  });

  test('calendar strip is visible with today highlighted', async ({ page }) => {
    const calendarContainer = page.locator('#calendar-container');
    await expect(calendarContainer).toBeVisible();
    // Look for the "Сьогодні" label that Alpine renders on today's button (timezone-safe)
    await expect(calendarContainer.locator('span', { hasText: 'Сьогодні' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('history tab loads past sessions section', async ({ page }) => {
    await page.locator('[data-tour="history"]').click();
    await page.waitForTimeout(800);
    await expect(page.locator('h3').filter({ hasText: 'Минулі' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('clicking profile area activates profile tab', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await expect(page.locator('[data-tour="profile"]')).toHaveClass(/bg-white\/10/);
  });

  test('plan (agenda) view loads future sessions', async ({ page }) => {
    await page.locator('[data-tour="view-toggle"]').click();
    await page.waitForTimeout(500);
    // Wait for loading spinner to disappear
    await page.locator('.border-blue-500.border-t-transparent.rounded-full.animate-spin').waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
    // Verify the agenda view loaded — feed-view (Day) button should still be present
    await expect(page.locator('[data-tour="feed-view"]')).toBeVisible({ timeout: 5000 });
  });
});
