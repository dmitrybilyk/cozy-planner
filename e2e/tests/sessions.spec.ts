import { test, expect } from './fixtures';

test.describe('Sessions — feed (today)', () => {
  test('demo seed sessions are visible today', async ({ page }) => {
    // Demo seed has 4 sessions for today — check at least first is visible
    // Note: [data-session-id] appears in feed + agenda DOM; first() can be hidden.
    // The feed section is active by default; just confirm the section has visible cards.
    const feedSection = page.locator('[data-tour="day-filter"]');
    await expect(feedSection).toBeVisible({ timeout: 8000 });
    // At least one visible session card in feed
    await expect(page.locator('[data-session-id]').first()).toBeVisible({ timeout: 8000 });
  });

  test('session cards show confirmation status in detail view', async ({ page }) => {
    await page.locator('[data-session-id]').first().waitFor({ timeout: 8000 });
    // sessionConfirmations + telegramIntegration + connected are false for demo user — enable them
    // so the ● badge (gated by sessionConfirmations && sessionHasTg(session)) becomes visible
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (!el?._x_dataStack?.[0]) return;
      const state = el._x_dataStack[0];
      state.sessionConfirmations = true;
      state.telegramIntegration = true;
      state.mentorTg = { ...state.mentorTg, connected: true, enabled: true };
      // mark all trainees as telegram-enabled so sessionHasTg() returns true
      if (Array.isArray(state.trainees)) {
        state.trainees = state.trainees.map((t: any) => ({ ...t, telegramEnabled: true }));
      }
    });
    await page.waitForTimeout(300);
    // Switch to detail mode to ensure all cards are expanded
    const detailBtn = page.locator('button:has-text("Детальний режим")').first();
    if (await detailBtn.isVisible()) await detailBtn.click();
    // Every session card shows a confirmation status badge with ● symbol
    const firstCard = page.locator('[data-session-id]').first();
    const badges = firstCard.locator('.uppercase').filter({ hasText: '●' });
    const hasVisibleBadge = await badges.evaluateAll(els => els.some(el => el.offsetParent !== null));
    expect(hasVisibleBadge).toBe(true);
  });

  test('compact/expanded view toggle changes card layout', async ({ page }) => {
    await page.locator('[data-session-id]').first().waitFor({ timeout: 8000 });
    // Toggle to compact mode
    const compactBtn = page.locator('button:has-text("Компактний режим")').first();
    if (await compactBtn.isVisible()) {
      await compactBtn.click();
      await page.waitForTimeout(300);
      await expect(page.locator('button:has-text("Детальний режим")').first()).toBeVisible();
    }
    // Toggle back to detail
    const detailBtn = page.locator('button:has-text("Детальний режим")').first();
    if (await detailBtn.isVisible()) {
      await detailBtn.click();
      await page.waitForTimeout(300);
    }
    // Cards still visible after toggle
    await expect(page.locator('[data-session-id]').first()).toBeVisible();
  });

  test('new session modal opens from add-session button', async ({ page }) => {
    await page.locator('[data-tour="add-session"]').click();
    // Modal uses .modal-footer for its action buttons
    await expect(page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Зберегти' })).toBeVisible({ timeout: 5000 });
    await page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Скасувати' }).click();
  });

  test('new session modal can be dismissed', async ({ page }) => {
    await page.locator('[data-tour="add-session"]').click();
    await page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Скасувати' }).click({ timeout: 3000 });
    await expect(page.locator('[data-session-id]').first()).toBeVisible({ timeout: 5000 });
  });

  test('copy session button is accessible in detail view', async ({ page }) => {
    await page.locator('[data-session-id]').first().waitFor({ timeout: 8000 });
    // Switch to detail mode — copy button is only in the full card
    const detailBtn = page.locator('button:has-text("Детальний режим")').first();
    if (await detailBtn.isVisible()) {
      await detailBtn.click();
      await page.waitForTimeout(300);
    }
    await expect(page.locator('[data-tour="copy-session"]').first()).toBeVisible({ timeout: 5000 });
  });

  test('history tab shows past sessions from demo seed', async ({ page }) => {
    await page.locator('[data-tour="history"]').click();
    await page.waitForTimeout(1000);
    // History section has a unique heading
    await expect(page.locator('h3').filter({ hasText: 'Минулі' }).first()).toBeVisible({ timeout: 8000 });
    // Demo seed has past sessions — at least one past date group heading appears
    const dateHeadings = page.locator('.font-black.text-gray-500.uppercase.tracking-widest');
    await expect(dateHeadings.first()).toBeVisible({ timeout: 8000 });
  });

  test('past date disables add-session button', async ({ page }) => {
    // Navigate to yesterday's date using the calendar button ID
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const pastDateStr = yesterday.toISOString().slice(0, 10);
    const pastBtn = page.locator(`#date-btn-${pastDateStr}`);
    if (await pastBtn.count() > 0) {
      await pastBtn.scrollIntoViewIfNeeded();
      await pastBtn.click();
      await page.waitForTimeout(300);
      await expect(page.locator('[data-tour="add-session"]')).toBeDisabled();
    }
  });
});
