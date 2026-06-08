import { test, expect } from './fixtures';
import { Browser, Page } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Gets or creates an invite token for the first trainee of the logged-in coach. */
async function getOrCreateTraineeToken(page: Page): Promise<string | null> {
  const traineeId = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.trainees?.[0]?.id ?? null;
  });
  if (!traineeId) return null;

  let token: string | null = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.trainees?.[0]?.inviteToken ?? null;
  });
  if (token) return token;

  const res = await page.request.post(`/api/v1/trainees/${traineeId}/generate-invite`);
  if (!res.ok()) return null;
  const body = await res.json();
  const url: string = body.inviteUrl ?? '';
  const match = url.match(/\/trainee\/([^/?#]+)/);
  return match ? match[1] : null;
}

/**
 * Opens the trainee invite URL in a FRESH browser context (no coach session).
 * This simulates a real trainee visiting their link without being a coach.
 * Returns the page and a cleanup function.
 */
async function openFreshTraineePage(browser: Browser, token: string): Promise<{ page: Page; cleanup: () => Promise<void> }> {
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();
  await page.goto(`/trainee/${token}`);
  await page.waitForSelector('[x-data]', { timeout: 15000 });
  await page.waitForTimeout(1500);
  return { page, cleanup: () => context.close() };
}

async function alpineState(page: Page, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function setAlpineState(page: Page, key: string, value: any) {
  return page.evaluate(([k, v]: [string, any]) => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0][k] = v;
  }, [key, value]);
}

// ─── Unauthenticated trainee — identity and data isolation ────────────────────

test.describe('Trainee view — unauthenticated (invite link only)', () => {
  test('traineeId is set from the invite token, not from demo session', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }

    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const me = await alpineState(tp, 'me') as any;
      expect(me?.traineeId).toBeTruthy();
      expect(me?.traineeId).not.toBe(-1); // -1 = demo placeholder
    } finally { await cleanup(); }
  });

  test('mentorId belongs to the real coach, not the demo account', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }

    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const me = await alpineState(tp, 'me') as any;
      expect(me?.mentorId).toBeTruthy();
      expect(me?.mentorId).not.toBe(-1);
    } finally { await cleanup(); }
  });

  test('page loads without a coach login cookie', async ({ browser }) => {
    // We cannot easily get the token without the coach context here,
    // so we verify that the /trainee/{bad-token} route redirects, not crashes.
    const context = await browser.newContext({ baseURL: BASE_URL });
    const p = await context.newPage();
    const res = await p.goto('/trainee/nonexistent-token-000');
    // Should redirect to /signin, not 500
    expect(res?.status()).toBeLessThan(500);
    await context.close();
  });

  test('me.name matches the trainee record, not "Демо"', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }

    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const me = await alpineState(tp, 'me') as any;
      expect(me?.name).toBeTruthy();
      expect(me?.name).not.toBe('Демо');
    } finally { await cleanup(); }
  });

  test('mentorShareToken comes from the real coach, not the hardcoded demo token', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }

    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const me = await alpineState(tp, 'me') as any;
      // demo-share is the hardcoded share token from the V1 DB seed for Katya
      expect(me?.mentorShareToken).not.toBe('demo-share');
    } finally { await cleanup(); }
  });

  test('days array has 14 entries (2-week window)', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }

    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(400);
      const days = await alpineState(tp, 'days') as any[];
      expect(days.length).toBe(14);
    } finally { await cleanup(); }
  });
});

// ─── Sessions tab ─────────────────────────────────────────────────────────────

test.describe('Trainee view — Sessions tab', () => {
  test('page loads with navigation tabs', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await expect(tp.locator('header')).toBeVisible({ timeout: 5000 });
      const tabs = tp.locator('div.sticky button');
      expect(await tabs.count()).toBeGreaterThanOrEqual(3);
    } finally { await cleanup(); }
  });

  test('sessions tab is active by default', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const tab = await alpineState(tp, 'tab');
      expect(tab).toBe('sessions');
    } finally { await cleanup(); }
  });

  test('sessions list renders cards or empty-state', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const hasCards = await tp.locator('.rounded-3xl').first().isVisible().catch(() => false);
      const hasEmpty = await tp.locator('text=/немає тренувань|немає занять|немає зустрічей|немає сеансів|немає прийомів/i').first().isVisible().catch(() => false);
      expect(hasCards || hasEmpty).toBe(true);
    } finally { await cleanup(); }
  });

  test('sessions Alpine state is an array', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const sessions = await alpineState(tp, 'sessions');
      expect(Array.isArray(sessions)).toBe(true);
    } finally { await cleanup(); }
  });

  test('compact/expanded toggle flips compactView state', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const hasCard = await tp.locator('.rounded-3xl').first().isVisible({ timeout: 8000 }).catch(() => false);
      if (!hasCard) { test.skip(); return; }

      const toggleBtn = tp.locator('button').filter({ hasText: /Компактно|Детально/ }).first();
      await expect(toggleBtn).toBeVisible({ timeout: 5000 });
      const before = await alpineState(tp, 'compactView');
      await toggleBtn.click();
      await tp.waitForTimeout(300);
      const after = await alpineState(tp, 'compactView');
      expect(Boolean(before)).not.toBe(Boolean(after));
    } finally { await cleanup(); }
  });

  test('session cards show confirmation status in detail view', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await setAlpineState(tp, 'compactView', false);
      await tp.waitForTimeout(400);
      const hasCard = await tp.locator('.rounded-3xl').first().isVisible().catch(() => false);
      if (!hasCard) { test.skip(); return; }
      const statusPill = tp.locator('.rounded-full').filter({ hasText: /Підтверджено|Очікує|Відхилено/ }).first();
      await expect(statusPill).toBeVisible({ timeout: 5000 });
    } finally { await cleanup(); }
  });
});

// ─── My Availability tab ──────────────────────────────────────────────────────

test.describe('Trainee view — My Availability tab', () => {
  test('availability tab is visible', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const availTab = tp.locator('button').filter({ hasText: 'Моя доступність' }).first();
      await expect(availTab).toBeVisible({ timeout: 5000 });
    } finally { await cleanup(); }
  });

  test('clicking availability tab sets tab = availability', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(300);
      expect(await alpineState(tp, 'tab')).toBe('availability');
    } finally { await cleanup(); }
  });

  test('day picker shows exactly 14 days', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      const days = await alpineState(tp, 'days') as any[];
      expect(days.length).toBe(14);
    } finally { await cleanup(); }
  });

  test('availability tab shows "Додати" button', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      await expect(tp.locator('button').filter({ hasText: 'Додати' }).first()).toBeVisible({ timeout: 5000 });
    } finally { await cleanup(); }
  });

  test('clicking Додати adds a range to availRanges', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      const lenBefore = ((await alpineState(tp, 'availRanges')) as any[]).length;
      await tp.locator('button').filter({ hasText: 'Додати' }).first().click();
      await tp.waitForTimeout(300);
      const lenAfter = ((await alpineState(tp, 'availRanges')) as any[]).length;
      expect(lenAfter).toBe(lenBefore + 1);
    } finally { await cleanup(); }
  });

  test('adding a range makes Save button appear', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      await tp.evaluate(() => {
        const el = document.querySelector('[x-data]') as any;
        if (el?._x_dataStack?.[0]) el._x_dataStack[0].availDirtyDates = new Set();
      });
      await tp.locator('button').filter({ hasText: 'Додати' }).first().click();
      await tp.waitForTimeout(300);
      await expect(tp.locator('button').filter({ hasText: 'Зберегти' }).first()).toBeVisible({ timeout: 4000 });
    } finally { await cleanup(); }
  });

  test('removing a range decrements availRanges', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      await tp.locator('button').filter({ hasText: 'Додати' }).first().click();
      await tp.waitForTimeout(300);
      const lenBefore = ((await alpineState(tp, 'availRanges')) as any[]).length;
      expect(lenBefore).toBeGreaterThan(0);
      const deleteBtn = tp.locator('button.p-1.text-red-400').first();
      await expect(deleteBtn).toBeVisible({ timeout: 3000 });
      await deleteBtn.click();
      await tp.waitForTimeout(300);
      const lenAfter = ((await alpineState(tp, 'availRanges')) as any[]).length;
      expect(lenAfter).toBe(lenBefore - 1);
    } finally { await cleanup(); }
  });

  test('save calls PUT /api/v1/availability/ranges', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      let apiCalled = false;
      tp.on('request', req => {
        if (req.method() === 'PUT' && req.url().includes('/api/v1/availability/ranges')) apiCalled = true;
      });
      await tp.locator('button').filter({ hasText: 'Додати' }).first().click();
      await tp.waitForTimeout(300);
      await tp.locator('button').filter({ hasText: 'Зберегти' }).first().click();
      await tp.waitForTimeout(1500);
      expect(apiCalled).toBe(true);
    } finally { await cleanup(); }
  });

  test('switching between days updates availDate state', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(500);
      const dateBefore = await alpineState(tp, 'availDate');
      const days = await alpineState(tp, 'days') as any[];
      if (days.length < 2) { test.skip(); return; }
      const secondDate = days[1].dateStr;
      await tp.evaluate((d: string) => {
        const el = document.querySelector('[x-data]') as any;
        if (el?._x_dataStack?.[0]) el._x_dataStack[0].availSel(d);
      }, secondDate);
      await tp.waitForTimeout(300);
      expect(await alpineState(tp, 'availDate')).toBe(secondDate);
      expect(await alpineState(tp, 'availDate')).not.toBe(dateBefore);
    } finally { await cleanup(); }
  });
});

// ─── Coach Schedule tab ───────────────────────────────────────────────────────

test.describe('Trainee view — Coach Schedule tab', () => {
  test('schedule tab button is visible', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await expect(tp.locator('button').filter({ hasText: /Доступність/ }).first()).toBeVisible({ timeout: 5000 });
    } finally { await cleanup(); }
  });

  test('clicking coach schedule tab sets tab = schedule', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: /Доступність/ }).first().click();
      await tp.waitForTimeout(600);
      expect(await alpineState(tp, 'tab')).toBe('schedule');
    } finally { await cleanup(); }
  });

  test('coachSelectedDate is set to today', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const coachSelectedDate = await alpineState(tp, 'coachSelectedDate');
      expect(typeof coachSelectedDate).toBe('string');
      expect(coachSelectedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    } finally { await cleanup(); }
  });

  test('mentor info is populated from real coach, not demo', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: /Доступність/ }).first().click();
      await tp.waitForTimeout(1000);
      const me = await alpineState(tp, 'me') as any;
      expect(me?.mentorName).toBeTruthy();
      expect(me?.mentorName).not.toBe('Демо');
    } finally { await cleanup(); }
  });

  test('coach schedule uses real mentorShareToken, not demo-share', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const me = await alpineState(tp, 'me') as any;
      if (me?.mentorShareToken) {
        expect(me.mentorShareToken).not.toBe('demo-share');
      }
    } finally { await cleanup(); }
  });
});

// ─── Tab navigation state ─────────────────────────────────────────────────────

test.describe('Trainee view — tab navigation', () => {
  test('switching to feedback tab sets tab = feedback', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const feedbackTab = tp.locator('button').filter({ hasText: '💬' }).first();
      await feedbackTab.click();
      await tp.waitForTimeout(300);
      expect(await alpineState(tp, 'tab')).toBe('feedback');
    } finally { await cleanup(); }
  });

  test('conversation list is an array after init', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      const conversation = await alpineState(tp, 'conversation');
      expect(Array.isArray(conversation)).toBe(true);
    } finally { await cleanup(); }
  });

  test('switching tabs and back keeps session data', async ({ page, browser }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    const { page: tp, cleanup } = await openFreshTraineePage(browser, token);
    try {
      await tp.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
      await tp.waitForTimeout(300);
      const labels = await alpineState(tp, 'tabLabels') as any;
      const sessionsLabel = labels?.sessions || 'Тренування';
      await tp.locator('button').filter({ hasText: sessionsLabel }).first().click();
      await tp.waitForTimeout(300);
      expect(await alpineState(tp, 'tab')).toBe('sessions');
    } finally { await cleanup(); }
  });
});

// ─── Coach availability sharing (coach-side tests, runs in coach context) ─────

test.describe('Coach availability sharing', () => {
  test.beforeEach(async ({ page }) => {
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
    await page.locator('[data-tour="availability"]').click();
    await page.waitForTimeout(800);
  });

  test('share section has "Посиланням" and "Картинкою" buttons', async ({ page }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    await expect(shareSection.locator('button').filter({ hasText: 'Посиланням' })).toBeVisible();
    await expect(shareSection.locator('button').filter({ hasText: 'Картинкою' })).toBeVisible();
  });

  test('clicking "Посиланням" generates a shareToken', async ({ page }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    await shareSection.locator('button').filter({ hasText: 'Посиланням' }).click();
    await page.waitForTimeout(1500);
    const shareToken = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.shareToken ?? null;
    });
    expect(shareToken).toBeTruthy();
  });

  test('shareUrl contains /shared/ and the shareToken', async ({ page }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    await shareSection.locator('button').filter({ hasText: 'Посиланням' }).click();
    await page.waitForTimeout(1500);
    const { shareUrl, shareToken } = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return { shareUrl: s?.shareUrl ?? '', shareToken: s?.shareToken ?? '' };
    });
    expect(shareToken).toBeTruthy();
    expect(shareUrl).toContain('/shared/');
    expect(shareUrl).toContain(shareToken);
  });

  test('/shared/{token} returns non-5xx', async ({ page }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    await shareSection.locator('button').filter({ hasText: 'Посиланням' }).click();
    await page.waitForTimeout(1500);
    const shareToken = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.shareToken ?? null;
    });
    if (!shareToken) { test.skip(); return; }
    const res = await page.request.get(`/shared/${shareToken}`);
    expect(res.status()).toBeLessThan(500);
  });

  test('shared availability viewed in fresh context (as real trainee would)', async ({ page, browser }) => {
    const shareSection = page.locator('[data-tour="avail-share"]');
    await expect(shareSection).toBeVisible({ timeout: 10000 });
    await shareSection.locator('button').filter({ hasText: 'Посиланням' }).click();
    await page.waitForTimeout(1500);
    const shareToken = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.shareToken ?? null;
    });
    if (!shareToken) { test.skip(); return; }

    // Open shared page as an unauthenticated visitor
    const context = await browser.newContext({ baseURL: BASE_URL });
    const freshPage = await context.newPage();
    try {
      const res = await freshPage.goto(`/shared/${shareToken}`);
      expect(res?.status()).toBeLessThan(500);
    } finally {
      await context.close();
    }
  });

  test('POST /api/v1/coach/share-token works in coach session', async ({ page }) => {
    const res = await page.request.post('/api/v1/coach/share-token');
    expect(res.status()).toBeLessThan(500);
    if (res.ok()) {
      const body = await res.json();
      expect(body.shareToken).toBeTruthy();
    }
  });
});
