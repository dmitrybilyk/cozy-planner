import { test, expect } from './fixtures';

test.describe('Locations', () => {
  test.beforeEach(async ({ page }) => {
    // Start tour to create demo seed locations (Зал А, Зал Б)
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

    await page.locator('[data-tour="locations-btn"]').click();
    // Wait for locations panel heading to confirm the tab is active
    await expect(page.locator('[data-tour="locations-list"]')).toBeVisible({ timeout: 8000 });
  });

  test('locations list shows demo seed locations', async ({ page }) => {
    // Location names are in <p class="text-base font-bold text-gray-100"> inside locations-list
    const list = page.locator('[data-tour="locations-list"]');
    await expect(list.locator('p').filter({ hasText: 'Зал А' }).first()).toBeVisible({ timeout: 8000 });
    await expect(list.locator('p').filter({ hasText: 'Зал Б' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('location cards have colored left border', async ({ page }) => {
    // Each card has style="border-left: 5px solid <color>"
    const cards = page.locator('[data-tour="locations-list"] div[style*="border-left"]');
    await expect(cards.first()).toBeVisible({ timeout: 8000 });
    expect(await cards.count()).toBeGreaterThanOrEqual(3);
  });

  test('add location form shows 8 color swatches', async ({ page }) => {
    // Button is the sibling in the header div just before the locations-list
    const addBtn = page.locator('xpath=//div[@data-tour="locations-list"]/preceding-sibling::div[1]/button');
    await addBtn.click();
    await page.waitForTimeout(300);
    // 8 colored round swatch buttons inside the modal
    const swatches = page.locator('button.w-8.h-8.rounded-full');
    await expect(swatches.first()).toBeVisible({ timeout: 3000 });
    expect(await swatches.count()).toBe(8);
  });

  test('black color is not in color picker swatches', async ({ page }) => {
    const addBtn = page.locator('xpath=//div[@data-tour="locations-list"]/preceding-sibling::div[1]/button');
    await addBtn.click();
    await page.waitForTimeout(300);
    // None of the swatches should have black background
    await expect(page.locator('button.w-8.h-8.rounded-full[style*="#000000"]')).toHaveCount(0);
    await expect(page.locator('button.w-8.h-8.rounded-full[style*="#1a1a1a"]')).toHaveCount(0);
    await expect(page.locator('button.w-8.h-8.rounded-full[style*="#111"]')).toHaveCount(0);
  });

  test('edit location form prefills existing data', async ({ page }) => {
    const list = page.locator('[data-tour="locations-list"]');
    await list.locator('p').filter({ hasText: 'Зал А' }).first().waitFor({ timeout: 8000 });
    // Click the edit (pencil) icon on the first location card
    const editBtn = list.locator('button[title="Правка"]').first();
    await editBtn.click();
    await page.waitForTimeout(300);
    // Name input should be pre-filled
    const nameInput = page.locator('input[placeholder*="Назва"]').first();
    await expect(nameInput).not.toBeEmpty({ timeout: 3000 });
  });
});
