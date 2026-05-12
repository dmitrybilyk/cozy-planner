import { test, expect } from '@playwright/test';

test.describe('Setup page', () => {
  test('redirects to signin when not authenticated', async ({ page }) => {
    await page.goto('/setup');
    await expect(page).toHaveURL(/\/signin/);
  });

  test('renders setup form for authenticated user without club', async ({ page, context }) => {
    await context.addCookies([
      { name: 'dummy_auth', value: 'true', domain: 'localhost', path: '/' },
    ]);
    await page.goto('/setup');
    await expect(page.locator('h1')).toHaveText('Ласкаво просимо!');
    await expect(page.locator('text=Створіть свій простір')).toBeVisible();

    const clubInput = page.locator('input[name="clubName"]');
    await expect(clubInput).toBeVisible();
    await expect(clubInput).toHaveAttribute('placeholder', 'напр. Тенісний клуб');

    const coachInput = page.locator('input[name="coachName"]');
    await expect(coachInput).toBeVisible();
    await expect(coachInput).toHaveAttribute('placeholder', 'напр. Катя');

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeVisible();
    await expect(submitButton).toHaveText('Почати');
  });

  test('demo login skips setup when seed data exists', async ({ page }) => {
    await page.goto('/signin');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/planner/);
    await expect(page.locator('h1')).toContainText('Cozy Planner');
  });
});
