import { test, expect } from '@playwright/test';

test.describe('Login page', () => {
  test('renders the login page with all elements', async ({ page }) => {
    await page.goto('/signin');

    await expect(page.locator('h1')).toHaveText('Cozy Planner');
    await expect(page.locator('text=Твій помічник для планування')).toBeVisible();

    const googleButton = page.locator('a[href="/oauth2/authorization/google"]');
    await expect(googleButton).toBeVisible();
    await expect(googleButton).toContainText('Увійти через Google');

    const demoButton = page.locator('button[type="submit"]');
    await expect(demoButton).toBeVisible();
    await expect(demoButton).toContainText('Увійти як Демо');
  });

  test('demo login redirects to planner', async ({ page }) => {
    await page.goto('/signin');
    await page.locator('button[type="submit"]').click();

    await page.waitForURL(/\/planner/);
    await expect(page.locator('h1')).toContainText('Cozy Planner');
  });

  test('login page has proper dark theme', async ({ page }) => {
    await page.goto('/signin');

    const bg = await page.locator('body').evaluate(el =>
      window.getComputedStyle(el).background
    );
    expect(bg).toContain('rgb(18, 18, 18)');
  });

  test('has Google OAuth link with correct href', async ({ page }) => {
    await page.goto('/signin');

    const googleLink = page.locator('a[href="/oauth2/authorization/google"]');
    await expect(googleLink).toBeVisible();
    await expect(googleLink).toHaveAttribute('href', '/oauth2/authorization/google');
  });
});
