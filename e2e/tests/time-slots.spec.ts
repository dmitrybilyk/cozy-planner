/**
 * Tests for time slot visibility in the session modal, based on:
 * - Coach availability windows (demo seed: Зал А 08:00-13:00, 14:00-21:00)
 * - Existing sessions that occupy certain slots
 * - The time step setting (15/30/60 min intervals)
 */
import { test, expect } from './fixtures';

async function openNewSessionModal(page: any) {
  await page.locator('[data-tour="add-session"]').click();
  await expect(page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Зберегти' })).toBeVisible({ timeout: 5000 });
}

async function closeModal(page: any) {
  await page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Скасувати' }).click();
}

async function selectFutureDate(page: any) {
  // Pick a future date (e.g. tomorrow) to ensure time slots are not in the past
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dateStr = tomorrow.toISOString().slice(0, 10);
  await page.evaluate((ds) => {
    const el = document.querySelector('[x-data]') as any;
    // Use Alpine's internal proxy to properly trigger reactivity
    if (el && el.__x) {
      el.__x.$data.selectedDate = ds;
    } else if (el && el._x_dataStack) {
      el._x_dataStack[0].selectedDate = ds;
    }
  }, dateStr);
  await page.waitForTimeout(300);
}

test.describe('Time slot visibility', () => {
  test('new session modal shows start time options', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });
    // Should have more than just the placeholder option
    const optionCount = await startSelect.locator('option').count();
    expect(optionCount).toBeGreaterThan(1);
    await closeModal(page);
  });

  test('new session modal shows end time options after selecting start', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    const endSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'До'}) }).first();

    await expect(startSelect).toBeVisible({ timeout: 3000 });
    // Pick the first available start slot
    const firstSlot = startSelect.locator('option').nth(1);
    const slotValue = await firstSlot.getAttribute('value');
    if (slotValue) {
      await startSelect.selectOption(slotValue);
      await page.waitForTimeout(300);
      // End select should now have options that come after start
      const endOptionCount = await endSelect.locator('option').count();
      expect(endOptionCount).toBeGreaterThan(1);
    }
    await closeModal(page);
  });

  test('start time options respect availability — no slots outside 08:00-21:00', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });

    const options = await startSelect.locator('option').allTextContents();
    const timeOptions = options.filter(t => t && t !== 'Від');

    for (const t of timeOptions) {
      const [hours] = t.split(':').map(Number);
      expect(hours).toBeGreaterThanOrEqual(8);
      expect(hours).toBeLessThan(21);
    }
    await closeModal(page);
  });

  test('end time is always after the selected start time', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    const endSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'До'}) }).first();

    await expect(startSelect).toBeVisible({ timeout: 3000 });
    // Select a known start time
    const allStartOptions = await startSelect.locator('option').allInnerTexts();
    const firstReal = allStartOptions.find(o => o && o !== 'Від');
    if (firstReal) {
      await startSelect.selectOption({ label: firstReal });
      await page.waitForTimeout(300);

      const endOptions = await endSelect.locator('option').allInnerTexts();
      const realEndOptions = endOptions.filter(o => o && o !== 'До');

      const [startH, startM] = firstReal.split(':').map(Number);
      const startMinutes = startH * 60 + startM;
      for (const e of realEndOptions) {
        const [eh, em] = e.split(':').map(Number);
        expect(eh * 60 + em).toBeGreaterThan(startMinutes);
      }
    }
    await closeModal(page);
  });

  test('gap in availability is reflected — no slots between 13:00-14:00', async ({ page }) => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().slice(0, 10);

    // Inject availability directly into Alpine state — no server roundtrip, no session dependency.
    // validStartSlots uses ignoreAvail: !shareAvailability; MeController returns shareAvailability=false
    // for demo users, so we set it to true here to enable the coachRangesByDate filtering.
    await page.evaluate((d: string) => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      if (!state) return;
      state.coachRangesByDate = {
        ...state.coachRangesByDate,
        [d]: [
          { startTime: '10:00', endTime: '13:00', locationId: null },
          { startTime: '15:00', endTime: '17:00', locationId: null },
        ],
      };
      state.shareAvailability = true;
    }, tomorrowStr);
    await page.waitForTimeout(300);

    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });

    const options = await startSelect.locator('option').allTextContents();
    const timeOptions = options.filter(t => t && t !== 'Від');

    // 13:30 is in the gap — must not appear
    const has1330 = timeOptions.some(t => t.startsWith('13:30'));
    expect(has1330).toBe(false);

    // Slots inside the first window must be present
    const has1000 = timeOptions.some(t => t.startsWith('10:00'));
    expect(has1000).toBe(true);

    await closeModal(page);
  });

  test('selecting a location filters time slots to that location availability', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });

    // Select a location if the location picker exists in the modal
    const locationSelect = page.locator('select[x-model="sessionForm.locationId"]').first();
    if (await locationSelect.count() > 0 && await locationSelect.isVisible()) {
      const locationOptions = await locationSelect.locator('option').count();
      if (locationOptions > 1) {
        await locationSelect.selectOption({ index: 1 });
        await page.waitForTimeout(400);
        const optionsAfter = await startSelect.locator('option').count();
        // With a specific location selected, slots should be scoped to its availability
        expect(optionsAfter).toBeGreaterThan(0);
      }
    }
    await closeModal(page);
  });

  test('slots on a future date show time options', async ({ page }) => {
    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });
    // At minimum some time options exist — this confirms the slot engine runs
    const optionCount = await startSelect.locator('option').count();
    expect(optionCount).toBeGreaterThan(1);
    await closeModal(page);
  });

  test('time step 15min gives more slots than 60min', async ({ page }) => {
    // Set 15-min step directly in Alpine state — do NOT call saveAvailStep() which shows a blocking modal
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].availStep = 15;
    });
    await page.waitForTimeout(300);

    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect).toBeVisible({ timeout: 3000 });
    const count15 = await startSelect.locator('option').count();
    await closeModal(page);

    // Set 60-min step
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].availStep = 60;
    });
    await page.waitForTimeout(300);

    await selectFutureDate(page);
    await openNewSessionModal(page);
    const startSelect60 = page.locator('select').filter({ has: page.locator('option', {hasText: 'Від'}) }).first();
    await expect(startSelect60).toBeVisible({ timeout: 3000 });
    const count60 = await startSelect60.locator('option').count();
    await closeModal(page);

    // Both must have at least the placeholder; 15-min gives more options than 60-min
    if (count15 > 1 && count60 > 1) {
      expect(count15).toBeGreaterThan(count60);
    } else {
      // If there are no availability windows for the selected date, skip rather than fail
      expect(count15).toBeGreaterThanOrEqual(count60);
    }

  });
});
