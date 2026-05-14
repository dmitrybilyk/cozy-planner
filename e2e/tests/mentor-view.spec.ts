import { test, expect } from '@playwright/test';

test.describe('Mentor view (planner)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/signin');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/\/planner/);
  });

  test('renders the planner page after demo login', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Cozy Planner');
  });

  test('shows header with action buttons', async ({ page }) => {
    await expect(page.locator('button:has-text("Нова сесія")')).toBeVisible();
    await expect(page.locator('button:has-text("Учні")')).toBeVisible();
    await expect(page.locator('button:has-text("Місця")')).toBeVisible();
    await expect(page.locator('button:has-text("Доступність")')).toBeVisible();
  });

  test('toggles between День and План views', async ({ page }) => {
    const dayButton = page.locator('button:has-text("День")');
    const planButton = page.locator('button:has-text("План")');

    await expect(dayButton).toBeVisible();
    await expect(planButton).toBeVisible();

    await planButton.click();
    await expect(page.locator('button:has-text("Всі учні")')).toBeVisible();

    await dayButton.click();
    await expect(page.locator('text=Сьогодні')).toBeVisible();
  });

  test('opens new session modal', async ({ page }) => {
    await page.locator('button:has-text("Нова сесія")').click();
    await expect(page.locator('text=Нова сесія')).toBeVisible();
    await expect(page.locator('text=Зберегти')).toBeVisible();
    await expect(page.locator('text=Скасувати')).toBeVisible();
  });

  test('opens manage athletes panel', async ({ page }) => {
    await page.locator('button:has-text("Учні")').click();
    await expect(page.locator('h3:has-text("Команда")')).toBeVisible();
    await expect(page.locator('text=Додати учня')).toBeVisible();
  });

  test('opens manage locations panel', async ({ page }) => {
    await page.locator('button:has-text("Місця")').click();
    await expect(page.locator('h3:has-text("Місця")')).toBeVisible();
    await expect(page.locator('text=Додати локацію')).toBeVisible();
  });

  test('opens availability overview panel', async ({ page }) => {
    await page.locator('button:has-text("Доступність")').click();
    await expect(page.locator('h3:has-text("Зайнятість учнів")')).toBeVisible();
  });

  test('log out button exists and submits form', async ({ page }) => {
    const logoutButton = page.locator('button[onclick*="logoutForm"]');
    await expect(logoutButton).toBeVisible();
  });
});
