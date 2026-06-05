import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('sign-in page renders required elements', async ({ page }) => {
    await page.goto('/signin');
    await expect(page.locator('a[href="/oauth2/authorization/google"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('demo login redirects to planner', async ({ page }) => {
    await page.goto('/signin');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/planner/, { timeout: 15000 });
    await expect(page.locator('[data-tour="profile"]')).toBeVisible({ timeout: 10000 });
  });

  test('unauthenticated access to /planner redirects to sign-in', async ({ page }) => {
    await page.goto('/planner');
    await page.waitForURL(/\/signin|\/oauth2/, { timeout: 10000 });
  });

  test('Google OAuth link points to correct endpoint', async ({ page }) => {
    await page.goto('/signin');
    await expect(page.locator('a[href="/oauth2/authorization/google"]'))
      .toHaveAttribute('href', '/oauth2/authorization/google');
  });
});
