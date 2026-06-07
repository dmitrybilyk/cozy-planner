import { test, expect } from './fixtures';

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Returns a trainee invite token, generating one if the trainee has none yet. */
async function getOrCreateTraineeToken(page: any): Promise<string | null> {
  // First get the trainee ID from Alpine state
  const traineeId = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.trainees?.[0]?.id ?? null;
  });
  if (!traineeId) return null;

  // Check if an invite token already exists
  let token: string | null = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.trainees?.[0]?.inviteToken ?? null;
  });
  if (token) return token;

  // Generate one via the API
  const res = await page.request.post(`/api/v1/trainees/${traineeId}/generate-invite`);
  if (!res.ok()) return null;
  const body = await res.json();
  // inviteUrl looks like http://localhost:8080/trainee/<token>
  const url: string = body.inviteUrl ?? '';
  const match = url.match(/\/trainee\/([^/?#]+)/);
  return match ? match[1] : null;
}

async function goToTraineePage(page: any, token: string) {
  await page.goto(`/trainee/${token}`);
  await page.waitForSelector('[x-data]', { timeout: 15000 });
  await page.waitForTimeout(1200);
}

async function alpineState(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function setAlpineState(page: any, key: string, value: any) {
  return page.evaluate(([k, v]: [string, any]) => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0][k] = v;
  }, [key, value]);
}

// ─── Sessions tab ─────────────────────────────────────────────────────────────

test.describe('Trainee view — Sessions tab', () => {
  test('page loads with navigation tabs', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await expect(page.locator('header')).toBeVisible({ timeout: 5000 });
    // at least 3 tab buttons in the sticky nav
    const tabs = page.locator('div.sticky button');
    expect(await tabs.count()).toBeGreaterThanOrEqual(3);
  });

  test('sessions tab is active by default', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const tab = await alpineState(page, 'tab');
    expect(tab).toBe('sessions');
  });

  test('sessions list renders cards or empty-state', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    // Either session cards or the empty-state message is visible
    const hasCards = await page.locator('.rounded-3xl').first().isVisible().catch(() => false);
    const hasEmpty = await page.locator('text=/немає тренувань|немає занять|немає зустрічей|немає сеансів|немає прийомів/i').first().isVisible().catch(() => false);
    expect(hasCards || hasEmpty).toBe(true);
  });

  test('session cards show confirmation status badge in detail view', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    // compactView defaults to true in trainee JS — switch to detail
    await setAlpineState(page, 'compactView', false);
    await page.waitForTimeout(400);

    const hasCard = await page.locator('.rounded-3xl').first().isVisible().catch(() => false);
    if (!hasCard) { test.skip(); return; } // no sessions for this trainee

    // Full card shows status pill (Підтверджено / Очікує / Відхилено)
    const statusPill = page.locator('.rounded-full').filter({ hasText: /Підтверджено|Очікує|Відхилено/ }).first();
    await expect(statusPill).toBeVisible({ timeout: 5000 });
  });

  test('compact/expanded toggle flips compactView state', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.waitForSelector('.rounded-3xl', { timeout: 10000 });

    const toggleBtn = page.locator('button').filter({ hasText: /Компактно|Детально/ }).first();
    await expect(toggleBtn).toBeVisible({ timeout: 5000 });

    const before = await alpineState(page, 'compactView');
    await toggleBtn.click();
    await page.waitForTimeout(300);
    const after = await alpineState(page, 'compactView');
    expect(Boolean(before)).not.toBe(Boolean(after));
  });

  test('compact mode shows condensed cards with expand chevron', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    // compactView already true by default — just verify chevron buttons exist
    const hasCard = await page.locator('.rounded-3xl').first().isVisible({ timeout: 8000 }).catch(() => false);
    if (!hasCard) { test.skip(); return; }

    const chevron = page.locator('.rounded-3xl button.ml-auto').first();
    await expect(chevron).toBeVisible({ timeout: 5000 });
  });

  test('expanding compact card shows full session details', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const hasCard = await page.locator('.rounded-3xl').first().isVisible({ timeout: 8000 }).catch(() => false);
    if (!hasCard) { test.skip(); return; }

    const chevron = page.locator('.rounded-3xl button.ml-auto').first();
    await chevron.click();
    await page.waitForTimeout(400);

    // full card shows date emoji
    const dateInfo = page.locator('.rounded-3xl').first().locator('span').filter({ hasText: /📅/ });
    await expect(dateInfo).toBeVisible({ timeout: 5000 });
  });

  test('GCal export button appears in full session card', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const hasCard = await page.locator('.rounded-3xl').first().isVisible({ timeout: 8000 }).catch(() => false);
    if (!hasCard) { test.skip(); return; }

    // Switch to detail view to see full card buttons
    await setAlpineState(page, 'compactView', false);
    await page.waitForTimeout(400);

    const gcalBtn = page.locator('button').filter({ hasText: 'Експорт в Google Calendar' }).first();
    await expect(gcalBtn).toBeVisible({ timeout: 5000 });
  });

  test('sessions Alpine state is an array', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const sessions = await alpineState(page, 'sessions');
    expect(Array.isArray(sessions)).toBe(true);
  });
});

// ─── My Availability tab ──────────────────────────────────────────────────────

test.describe('Trainee view — My Availability tab', () => {
  test('availability tab button is visible', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const availTab = page.locator('button').filter({ hasText: 'Моя доступність' }).first();
    await expect(availTab).toBeVisible({ timeout: 5000 });
  });

  test('clicking availability tab sets tab = availability', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const availTab = page.locator('button').filter({ hasText: 'Моя доступність' }).first();
    await availTab.click();
    await page.waitForTimeout(300);

    const tab = await alpineState(page, 'tab');
    expect(tab).toBe('availability');
  });

  test('availability tab shows a day picker with multiple days', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    // day picker buttons — at least 7 days
    const days = await alpineState(page, 'days');
    expect((days as any[]).length).toBeGreaterThanOrEqual(7);
  });

  test('availability tab shows "Додати" button', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    // the Додати button to add a time range
    const addBtn = page.locator('button').filter({ hasText: 'Додати' }).first();
    await expect(addBtn).toBeVisible({ timeout: 5000 });
  });

  test('availRanges is an array after init', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const ranges = await alpineState(page, 'availRanges');
    expect(Array.isArray(ranges)).toBe(true);
  });

  test('clicking Додати adds a range to availRanges', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    const lenBefore = ((await alpineState(page, 'availRanges')) as any[]).length;

    const addBtn = page.locator('button').filter({ hasText: 'Додати' }).first();
    await addBtn.click();
    await page.waitForTimeout(300);

    const lenAfter = ((await alpineState(page, 'availRanges')) as any[]).length;
    expect(lenAfter).toBe(lenBefore + 1);
  });

  test('adding a range makes the Save button appear', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    // clear any pre-existing dirty state
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack?.[0]) el._x_dataStack[0].availDirtyDates = new Set();
    });

    const addBtn = page.locator('button').filter({ hasText: 'Додати' }).first();
    await addBtn.click();
    await page.waitForTimeout(300);

    // Save button appears (fixed bottom button)
    const saveBtn = page.locator('button').filter({ hasText: 'Зберегти' }).first();
    await expect(saveBtn).toBeVisible({ timeout: 4000 });
  });

  test('removing a range decrements availRanges', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    // Add one range first
    const addBtn = page.locator('button').filter({ hasText: 'Додати' }).first();
    await addBtn.click();
    await page.waitForTimeout(300);

    const lenBefore = ((await alpineState(page, 'availRanges')) as any[]).length;
    expect(lenBefore).toBeGreaterThan(0);

    // Delete button in range row — has class p-1 (not the session reject button which is px-3 py-1.5)
    const deleteBtn = page.locator('button.p-1.text-red-400').first();
    await expect(deleteBtn).toBeVisible({ timeout: 3000 });
    await deleteBtn.click();
    await page.waitForTimeout(300);

    const lenAfter = ((await alpineState(page, 'availRanges')) as any[]).length;
    expect(lenAfter).toBe(lenBefore - 1);
  });

  test('save calls PUT /api/v1/availability/ranges', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    let apiCalled = false;
    page.on('request', req => {
      if (req.method() === 'PUT' && req.url().includes('/api/v1/availability/ranges')) {
        apiCalled = true;
      }
    });

    const addBtn = page.locator('button').filter({ hasText: 'Додати' }).first();
    await addBtn.click();
    await page.waitForTimeout(300);

    const saveBtn = page.locator('button').filter({ hasText: 'Зберегти' }).first();
    await saveBtn.click();
    await page.waitForTimeout(1500);

    expect(apiCalled).toBe(true);
  });

  test('switching between days updates availDate state', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(500);

    const dateBefore = await alpineState(page, 'availDate');

    // Click the second day button in the day-picker area
    const days = await alpineState(page, 'days') as any[];
    if (days.length < 2) { test.skip(); return; }
    const secondDate = days[1].dateStr;

    await page.evaluate((d: string) => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack?.[0]) el._x_dataStack[0].availSel(d);
    }, secondDate);
    await page.waitForTimeout(300);

    const dateAfter = await alpineState(page, 'availDate');
    expect(dateAfter).not.toBe(dateBefore);
    expect(dateAfter).toBe(secondDate);
  });
});

// ─── Coach Schedule tab ───────────────────────────────────────────────────────

test.describe('Trainee view — Coach Schedule tab', () => {
  test('schedule tab button is visible', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    // The label varies by mentor profile: Доступність тренера / майстра / etc.
    const scheduleTab = page.locator('button').filter({ hasText: /Доступність/ }).first();
    await expect(scheduleTab).toBeVisible({ timeout: 5000 });
  });

  test('clicking coach schedule tab sets tab = schedule', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const scheduleTab = page.locator('button').filter({ hasText: /Доступність/ }).first();
    await scheduleTab.click();
    await page.waitForTimeout(600);

    const tab = await alpineState(page, 'tab');
    expect(tab).toBe('schedule');
  });

  test('schedule tab shows loading or slot content', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const scheduleTab = page.locator('button').filter({ hasText: /Доступність/ }).first();
    await scheduleTab.click();
    await page.waitForTimeout(800);

    // Either spinner or the date scroll area is visible
    const spinner = page.locator('.animate-spin').first();
    const datePicker = page.locator('button').filter({ hasText: /Пн|Вт|Ср|Чт|Пт|Сб|Нд/ }).first();
    const hasSpinnerOrPicker = (await spinner.isVisible()) || (await datePicker.isVisible({ timeout: 5000 }).catch(() => false));
    expect(hasSpinnerOrPicker).toBe(true);
  });

  test('coachSelectedDate state is set to today initially', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const coachSelectedDate = await alpineState(page, 'coachSelectedDate');
    // should be a date string YYYY-MM-DD
    expect(typeof coachSelectedDate).toBe('string');
    expect(coachSelectedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  test('mentor info card appears after schedule loads', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const scheduleTab = page.locator('button').filter({ hasText: /Доступність/ }).first();
    await scheduleTab.click();
    // Wait up to 8s for load to finish
    await page.waitForSelector('[x-show]', { timeout: 8000 });
    await page.waitForTimeout(1000);

    const mentorName = await alpineState(page, 'me');
    expect((mentorName as any)?.mentorName).toBeTruthy();
  });
});

// ─── Tab navigation state ─────────────────────────────────────────────────────

test.describe('Trainee view — tab state', () => {
  test('switching to feedback tab sets tab = feedback', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const feedbackTab = page.locator('button').filter({ hasText: '💬' }).first();
    await feedbackTab.click();
    await page.waitForTimeout(300);

    const tab = await alpineState(page, 'tab');
    expect(tab).toBe('feedback');
  });

  test('switching back to sessions tab from another restores sessions', async ({ page }) => {
    const token = await getOrCreateTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    // go to availability then back
    await page.locator('button').filter({ hasText: 'Моя доступність' }).first().click();
    await page.waitForTimeout(300);
    const labels = await alpineState(page, 'tabLabels') as any;
    const sessionsLabel = labels?.sessions || 'Тренування';
    await page.locator('button').filter({ hasText: sessionsLabel }).first().click();
    await page.waitForTimeout(300);

    const tab = await alpineState(page, 'tab');
    expect(tab).toBe('sessions');
    // session cards still visible
    await expect(page.locator('.rounded-3xl').first()).toBeVisible({ timeout: 5000 });
  });
});

// ─── Coach availability sharing ───────────────────────────────────────────────

test.describe('Coach availability sharing', () => {
  test.beforeEach(async ({ page }) => {
    // Start tour to seed demo availability
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

  test('clicking "Посиланням" generates a shareToken in Alpine state', async ({ page }) => {
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

  test('/shared/{token} endpoint returns a non-5xx response', async ({ page }) => {
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

  test('POST /api/v1/coach/share-token creates a token', async ({ page }) => {
    // Intercept to verify the endpoint works
    const res = await page.request.post('/api/v1/coach/share-token');
    // May return 200 or 401 depending on session — coach is logged in via demo
    expect(res.status()).toBeLessThan(500);
    if (res.ok()) {
      const body = await res.json();
      expect(body.shareToken).toBeTruthy();
    }
  });

  test('intervals section is visible after tour seeding', async ({ page }) => {
    const intervals = page.locator('[data-tour="avail-intervals"]');
    await expect(intervals).toBeVisible({ timeout: 10000 });
  });

  test('switching back to feed keeps session data', async ({ page }) => {
    await page.locator('[data-tour="feed-view"]').click();
    await page.waitForTimeout(300);
    await expect(page.locator('[data-session-id]').first()).toBeVisible({ timeout: 8000 });
    // back to availability
    await page.locator('[data-tour="availability"]').click();
    await page.waitForTimeout(600);
    await expect(page.locator('[data-tour="avail-share"]')).toBeVisible({ timeout: 10000 });
  });
});
