import { test, expect } from './fixtures';

test.describe('Trainees', () => {
  test.beforeEach(async ({ page }) => {
    await page.locator('[data-tour="trainees"]').click();
    await expect(page.locator('h3').filter({ hasText: 'Команда' }).first()).toBeVisible({ timeout: 8000 });
  });

  // ─── helpers ───────────────────────────────────────────────────────────────

  async function setCompactMode(page: any, compact: boolean) {
    await page.evaluate((c: boolean) => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) {
        el._x_dataStack[0].traineeCompactView = c;
        el._x_dataStack[0].traineeExpandedIds = [];
        el._x_dataStack[0]._ref++;
      }
    }, compact);
    await page.waitForTimeout(300);
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  async function enableTgAndTraineeFeatures(page: any) {
    // wait for init() to finish (including fetchMentorTelegramStatus) so our
    // state assignments aren't overwritten by the background server fetch
    await page.waitForFunction(
      () => {
        const el = document.querySelector('[x-data]') as any;
        return el?._x_dataStack?.[0]?.loading === false;
      },
      { timeout: 15000 }
    ).catch(() => {});
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (!el?._x_dataStack?.[0]) return;
      const state = el._x_dataStack[0];
      state.telegramIntegration = true;
      state.mentorTg = { ...state.mentorTg, connected: true, enabled: true };
      state.traineeComm = true;
      if (Array.isArray(state.trainees)) {
        state.trainees = state.trainees.map((t: any) => ({ ...t, telegramEnabled: true }));
      }
    });
    await page.waitForTimeout(300);
  }

  // ─── list visibility ───────────────────────────────────────────────────────

  test('demo trainees are listed in detail view', async ({ page }) => {
    await setCompactMode(page, false);
    const names = page.locator('p.font-bold.text-gray-100.truncate');
    await expect(names.filter({ hasText: 'Анна К.' }).first()).toBeVisible({ timeout: 8000 });
  });

  test('trainee actions row is visible in detail view', async ({ page }) => {
    await setCompactMode(page, false);
    await expect(page.locator('[data-tour="trainee-actions"]').first()).toBeVisible({ timeout: 8000 });
  });

  test('trainee site button is visible in detail view', async ({ page }) => {
    await setCompactMode(page, false);
    await enableTgAndTraineeFeatures(page);
    const siteBtn = page.locator('[data-tour="trainee-site-btn"]').first();
    await expect(siteBtn).toBeVisible({ timeout: 8000 });
    const tag = await siteBtn.evaluate((el: Element) => el.tagName.toLowerCase());
    expect(['a', 'button']).toContain(tag);
  });

  test('compact mode shows only name row', async ({ page }) => {
    await setCompactMode(page, true);
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).not.toBeVisible();
    await expect(page.locator('[data-tour="trainee-actions"]').first()).not.toBeVisible();
    // compact row should be visible
    await expect(page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first()).toBeVisible();
  });

  // ─── expand in compact mode ────────────────────────────────────────────────

  test('clicking compact row expands trainee', async ({ page }) => {
    await enableTgAndTraineeFeatures(page);
    await setCompactMode(page, true);
    const row = page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first();
    await expect(row).toBeVisible({ timeout: 5000 });
    await row.click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).toBeVisible({ timeout: 3000 });
  });

  test('down-arrow button expands trainee in compact mode', async ({ page }) => {
    await enableTgAndTraineeFeatures(page);
    await setCompactMode(page, true);
    // down arrow is the chevron-down button inside compact row
    const downArrow = page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer button').first();
    await expect(downArrow).toBeVisible({ timeout: 5000 });
    await downArrow.click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).toBeVisible({ timeout: 3000 });
  });

  // ─── collapse from expanded (compact mode) ─────────────────────────────────

  test('up-arrow button collapses expanded trainee in compact mode', async ({ page }) => {
    await enableTgAndTraineeFeatures(page);
    await setCompactMode(page, true);
    // first expand via card click
    const row = page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first();
    await row.click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).toBeVisible({ timeout: 3000 });

    // now collapse with up-arrow
    const upArrow = page.locator('[data-tour="trainee-collapse"]').first();
    await upArrow.click();
    await page.waitForTimeout(300);

    // detail actions should be gone
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).not.toBeVisible();
    // compact row should be back
    await expect(page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first()).toBeVisible();
  });

  test('other trainees stay collapsed when one is expanded', async ({ page }) => {
    await setCompactMode(page, true);
    const rows = page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer');
    const count = await rows.count();
    if (count < 2) test.skip();

    await rows.first().click();
    await page.waitForTimeout(300);

    // only one expanded card should exist
    const expandedCards = page.locator('[data-tour="trainee-card-expanded"]');
    await expect(expandedCards).toHaveCount(1, { timeout: 3000 });
    // remaining compact rows: count - 1
    await expect(rows).toHaveCount(count - 1);
  });

  // ─── collapse from detail mode ─────────────────────────────────────────────

  test('up-arrow in detail mode switches to compact mode', async ({ page }) => {
    await setCompactMode(page, false);
    await expect(page.locator('[data-tour="trainee-actions"]').first()).toBeVisible({ timeout: 5000 });

    const upArrow = page.locator('[data-tour="trainee-collapse"]').first();
    await upArrow.click();
    await page.waitForTimeout(300);

    // should now be in compact mode — detail actions gone, compact rows visible
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).not.toBeVisible();
    await expect(page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first()).toBeVisible();
  });

  // ─── toggle detail/compact mode button ────────────────────────────────────

  test('toggle mode button switches between compact and detail', async ({ page }) => {
    await setCompactMode(page, true);
    const toggleBtn = page.locator('[data-tour="trainee-compact-toggle"]');
    await expect(toggleBtn).toBeVisible();

    // switch to detail
    await toggleBtn.click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="trainee-actions"]').first()).toBeVisible({ timeout: 3000 });

    // switch back to compact
    await toggleBtn.click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-tour="trainee-site-btn"]').first()).not.toBeVisible();
    await expect(page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first()).toBeVisible();
  });

  test('switching to detail mode collapses expanded ids', async ({ page }) => {
    await setCompactMode(page, true);
    // expand one trainee
    await page.locator('div.p-3.bg-\\[\\#1c1c1c\\].cursor-pointer').first().click();
    await page.waitForTimeout(200);

    // switch to detail — all should appear expanded, expandedIds cleared
    const toggleBtn = page.locator('[data-tour="trainee-compact-toggle"]');
    await toggleBtn.click();
    await page.waitForTimeout(300);

    const expandedIds = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.traineeExpandedIds ?? [];
    });
    expect(expandedIds).toHaveLength(0);
    await expect(page.locator('[data-tour="trainee-actions"]').first()).toBeVisible({ timeout: 3000 });
  });

  // ─── misc ─────────────────────────────────────────────────────────────────

  test('add trainee button is visible', async ({ page }) => {
    const addBtn = page.locator('button').filter({ hasText: /Додати/ }).first();
    await expect(addBtn).toBeVisible();
  });

  test('search filters the list', async ({ page }) => {
    await setCompactMode(page, false);
    // Use x-model attribute to target the trainees search input specifically
    const searchInput = page.locator('input[x-model="traineeSearch"]').first();
    await searchInput.fill('Анна');
    await page.waitForTimeout(300);
    const names = page.locator('p.font-bold.text-gray-100.truncate');
    await expect(names.filter({ hasText: 'Анна К.' }).first()).toBeVisible();
    await expect(names.filter({ hasText: 'Марія С.' }).first()).not.toBeVisible();
    await searchInput.clear();
  });
});
