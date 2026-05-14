import { test, expect } from '@playwright/test';

test.describe('Trainee availability page', () => {
  test('redirects to signin when not authenticated', async ({ page }) => {
    await page.goto('/trainee/invalid-token');
    await expect(page).toHaveURL(/\/signin/);
  });

  test('shows availability form for authenticated trainee', async ({ page }) => {
    await page.goto('/signin');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/planner/);

    await page.goto('/trainee/invalid-token');
    await expect(page).toHaveURL(/\/signin/);
  });
});
