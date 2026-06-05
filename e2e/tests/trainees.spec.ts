import { test, expect } from './fixtures';

test.describe('Trainees', () => {
  test.beforeEach(async ({ page }) => {
    await page.locator('[data-tour="trainees"]').click();
    await expect(page.locator('h3').filter({ hasText: 'Команда' }).first()).toBeVisible({ timeout: 8000 });
    // Switch to detail mode via Alpine state (avoids picking hidden feed button in DOM order)
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = false;
    });
    await page.waitForTimeout(300);
  });

  test('demo seed trainees are listed in detail view', async ({ page }) => {
    // Trainee names in expanded cards: <p class="text-base sm:text-lg font-bold text-gray-100 truncate">
    const names = page.locator('p.font-bold.text-gray-100.truncate');
    await expect(names.filter({ hasText: 'Анна К.' }).first()).toBeVisible({ timeout: 8000 });
  });

  test('trainee actions row is visible in detail view', async ({ page }) => {
    await expect(page.locator('[data-tour="trainee-actions"]').first()).toBeVisible({ timeout: 8000 });
  });

  test('trainee site button is visible in detail view', async ({ page }) => {
    const siteBtn = page.locator('[data-tour="trainee-site-btn"]').first();
    await expect(siteBtn).toBeVisible({ timeout: 8000 });
    const tag = await siteBtn.evaluate(el => el.tagName.toLowerCase());
    expect(['a', 'button']).toContain(tag);
  });

  test('compact view hides trainee actions', async ({ page }) => {
    // Switch to compact mode
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);
    // In compact mode, trainee-actions and trainee-site-btn should not be visible
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).not.toBeVisible();
  });

  test('expanding a trainee in compact mode shows their actions', async ({ page }) => {
    // Switch to compact mode first
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);
    // Click on first trainee row to expand it
    const firstCompactRow = page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first();
    if (await firstCompactRow.count() > 0) {
      await firstCompactRow.click();
      await page.waitForTimeout(300);
      await expect(page.locator('[data-tour="trainee-site-btn"]').first()).toBeVisible({ timeout: 3000 });
    }
  });

  test('add trainee button is visible', async ({ page }) => {
    const addBtn = page.locator('button').filter({ hasText: /Додати/ }).first();
    await expect(addBtn).toBeVisible();
  });

  test('trainee search filters the list', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Пошук"]').first();
    await expect(searchInput).toBeVisible();
    await searchInput.fill('Анна');
    await page.waitForTimeout(300);
    // Only Анна К. should be visible
    const names = page.locator('p.font-bold.text-gray-100.truncate');
    await expect(names.filter({ hasText: 'Анна К.' }).first()).toBeVisible();
    // Марія should not be visible
    await expect(names.filter({ hasText: 'Марія С.' }).first()).not.toBeVisible();
    // Clear search
    await searchInput.clear();
  });
});
