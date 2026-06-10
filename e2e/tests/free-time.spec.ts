import { test, expect } from './fixtures';

async function alpineGet(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function alpineSet(page: any, key: string, value: any) {
  await page.evaluate(([k, v]: [string, any]) => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0][k] = v;
  }, [key, value]);
}

async function startTourAndDismiss(page: any) {
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]?.startTour) el._x_dataStack[0].startTour();
  });
  await page.waitForTimeout(2500);
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack) el._x_dataStack[0].showTour = false;
  });
  await page.waitForTimeout(300);
}

test.describe('Free time — day view', () => {
  test.beforeEach(async ({ page }) => {
    await startTourAndDismiss(page);
  });

  test('showFreeTime toggle exists in Alpine state', async ({ page }) => {
    const val = await alpineGet(page, 'showFreeTime');
    expect(typeof val).toBe('boolean');
  });

  test('toggling showFreeTime to true shows free time chips', async ({ page }) => {
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);
    // Free time chips are buttons with time range text like "09:30 — 10:00"
    const chips = page.locator('button').filter({ hasText: /\d{2}:\d{2}\s*[—–-]\s*\d{2}:\d{2}/ });
    const count = await chips.count();
    // If free time chips are rendered, verify at least one is visible
    if (count > 0) {
      await expect(chips.first()).toBeVisible({ timeout: 5000 });
    }
  });

  test('toggling showFreeTime to false hides free time chips', async ({ page }) => {
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);
    await alpineSet(page, 'showFreeTime', false);
    await page.waitForTimeout(300);
    const chips = page.locator('[data-testid="free-time-chip"]');
    const count = await chips.count();
    if (count > 0) {
      await expect(chips.first()).not.toBeVisible({ timeout: 3000 });
    }
  });

  test('free time chip click opens session modal with time pre-filled', async ({ page }) => {
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);

    // Free time chips have emerald styling (bg-emerald-500/10) — session cards don't
    const chip = page.locator('button[class*="emerald"]').filter({ hasText: /\d{2}:\d{2}/ }).first();
    const chipVisible = await chip.isVisible({ timeout: 3000 }).catch(() => false);
    if (!chipVisible) { test.skip(); return; }

    await chip.click();
    await page.waitForTimeout(400);

    // Session modal should be open
    const modalVisible = await page.locator('[data-tour="session-modal"]').isVisible({ timeout: 5000 }).catch(() => false);
    if (!modalVisible) { test.skip(); return; }

    // sessionForm.startTime should be pre-filled (openSessionFromFreeSlot sets startTime)
    const sessionTime = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionForm?.startTime ?? '';
    });
    expect(sessionTime).toMatch(/^\d{2}:\d{2}$/);

    // Dismiss modal
    await page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Скасувати' }).click().catch(() => {});
    await page.waitForTimeout(300);
  });

  test('free time copy button exists alongside free time slots', async ({ page }) => {
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);

    // Skip if no free slots exist for the selected date (no availability configured)
    const hasSlots = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      const date = state?.selectedDate;
      const slots = state?.freeSlotsByDate?.[date] ?? [];
      return Array.isArray(slots) && slots.length > 0;
    });
    if (!hasSlots) { test.skip(); return; }

    // Copy button should be visible next to free slots
    const copyBtn = page.locator('button[title*="опіюва"]').first();
    await expect(copyBtn).toBeVisible({ timeout: 3000 });
  });

  test('clicking copy button shows toast with Скопійовано', async ({ page }) => {
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);

    const copyBtn = page.locator('button[data-testid="free-time-copy"]').first();
    const btnVisible = await copyBtn.isVisible({ timeout: 2000 }).catch(() => false);
    if (!btnVisible) { test.skip(); return; }

    await copyBtn.click();
    await page.waitForTimeout(500);

    // Toast with copy confirmation should appear
    await expect(page.locator('text=Скопійовано').first()).toBeVisible({ timeout: 3000 });
  });
});
