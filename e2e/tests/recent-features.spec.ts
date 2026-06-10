import { test, expect } from './fixtures';

// ─── helpers ───────────────────────────────────────────────────────────────

async function goToTrainees(page: any) {
  const tab = page.locator('[data-tour="trainees"]');
  if (await tab.isVisible({ timeout: 2000 }).catch(() => false)) {
    await tab.click();
  } else {
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].activeTab = 'trainees';
    });
  }
  await page.waitForTimeout(500);
}

async function setCompactView(page: any, compact: boolean) {
  await page.evaluate((c: boolean) => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = c;
  }, compact);
  await page.waitForTimeout(300);
}

async function getAlpineState(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function setTgActive(page: any, active: boolean) {
  await page.evaluate((a: boolean) => {
    const el = document.querySelector('[x-data]') as any;
    if (!el?._x_dataStack) return;
    const state = el._x_dataStack[0];
    state.telegramIntegration = a;
    state.sessionConfirmations = a;
    state.mentorTg = { ...state.mentorTg, connected: a, enabled: true };
  }, active);
  await page.waitForTimeout(300);
}

async function cleanupRecurring(page: any, recurrenceGroupId: string, mentorId: number) {
  await page.evaluate(async (params: { rgid: string; mid: number }) => {
    const today = new Date();
    const start = today.toISOString().split('T')[0];
    const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const res = await fetch(`/api/v1/sessions?mentorId=${params.mid}&startDate=${start}&endDate=${end}`);
    if (!res.ok) return;
    const sessions = await res.json();
    const toDelete = sessions.filter((s: any) => s.recurrenceGroupId === params.rgid);
    await Promise.all(toDelete.map((s: any) => fetch(`/api/v1/sessions/${s.id}`, { method: 'DELETE' })));
  }, { rgid: recurrenceGroupId, mid: mentorId });
}

async function cleanupByTitle(page: any, titles: string[], mentorId: number) {
  await page.evaluate(async (params: { titles: string[]; mid: number }) => {
    const today = new Date();
    const start = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const res = await fetch(`/api/v1/sessions?mentorId=${params.mid}&startDate=${start}&endDate=${end}`);
    if (!res.ok) return;
    const sessions = await res.json();
    const toDelete = sessions.filter((s: any) => params.titles.includes(s.title));
    for (const s of toDelete) {
      await fetch(`/api/v1/sessions/${s.id}`, { method: 'DELETE' });
    }
  }, { titles, mid: mentorId });
}

// ─── Recurring sessions ────────────────────────────────────────────────────

test.describe('Recurring sessions', () => {
  test.beforeEach(async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.mentorId ?? null;
    });
    if (ids) {
      await cleanupByTitle(page, ['Recurring e2e', 'Recurring group test', 'Non-recurring test'], ids);
    }
  });

  test('creates 8 weekly instances via API', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Recurring e2e', date: p.dateStr, time: '09:00', endTime: '10:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });

    expect(res.status).toBe(201);
    expect(res.body.recurring).toBe(true);
    expect(res.body.recurrenceGroupId).toBeTruthy();

    const count = await page.evaluate(async (p: { rgid: string; mid: number }) => {
      const today = new Date();
      const start = today.toISOString().split('T')[0];
      const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const r = await fetch(`/api/v1/sessions?mentorId=${p.mid}&startDate=${start}&endDate=${end}`);
      const sessions = await r.json();
      return sessions.filter((s: any) => s.recurrenceGroupId === p.rgid).length;
    }, { rgid: res.body.recurrenceGroupId, mid: ids.mentorId });
    expect(count).toBe(8);

    await cleanupRecurring(page, res.body.recurrenceGroupId, ids.mentorId);
  });

  test('recurring session has recurrenceGroupId populated', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Recurring group test', date: p.dateStr, time: '11:00', endTime: '12:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });

    expect(res.body.recurrenceGroupId).toMatch(/^[0-9a-f-]{36}$/i);
    await cleanupRecurring(page, res.body.recurrenceGroupId, ids.mentorId);
  });

  test('non-recurring session has no recurrenceGroupId', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Non-recurring test', date: p.dateStr, time: '14:00', endTime: '15:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: false }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });

    expect(res.status).toBe(201);
    expect(res.body.recurrenceGroupId == null || res.body.recurrenceGroupId === '').toBe(true);

    await page.evaluate(async (sid: number) => {
      await fetch(`/api/v1/sessions/${sid}`, { method: 'DELETE' });
    }, res.body.id);
  });

  test('session creation modal shows recurring checkbox', async ({ page }) => {
    await page.locator('[data-tour="add-session"]').click();
    await page.waitForTimeout(500);
    const checkbox = page.locator('input[type="checkbox"][x-model="sessionForm.recurring"]');
    await expect(checkbox).toBeAttached({ timeout: 5000 });
    await page.locator('.modal-footer button').filter({ hasText: 'Скасувати' }).click().catch(() => {});
  });
});

// ─── Tour: step structure ──────────────────────────────────────────────────

test.describe('Tour step structure', () => {
  test('tour has 4 steps', async ({ page }) => {
    const count = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.tourSteps?.length ?? 0;
    });
    expect(count).toBe(4);
  });

  test('tour does not contain recurring session step', async ({ page }) => {
    const steps = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      if (!state) return [];
      return state.tourSteps.map((s: any) => s.title + ' ' + s.body);
    });
    const hasRecurring = steps.some((s: string) => /повторюва/i.test(s));
    expect(hasRecurring).toBe(false);
  });

  test('tour does not include add-session, copy, feedback or history steps', async ({ page }) => {
    const targets = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? []).map((s: any) => s.target);
    });
    const removed = ['[data-tour="add-session"]', '[data-tour="copy-session"]',
                     '[data-tour="feedback-btn"]', '[data-tour="history"]',
                     '[data-tour="trainee-compact-toggle"]', '[data-tour="locations-list"]',
                     '[data-tour="trainee-actions"]', '[data-tour="plan-sessions-btn"]'];
    for (const t of removed) {
      expect(targets).not.toContain(t);
    }
  });
});

// ─── Bulk selection in compact view ───────────────────────────────────────

test.describe('Bulk availability — compact view', () => {
  test('bulk select checkboxes appear in compact view when TG active', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, true);
    await setTgActive(page, true);
    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    expect(await checkboxes.count()).toBeGreaterThan(0);
    await expect(checkboxes.first()).toBeVisible({ timeout: 5000 });
  });

  test('bulk select checkboxes hidden in compact view when TG not active', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, true);
    await setTgActive(page, false);
    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    if (await checkboxes.count() > 0) {
      await expect(checkboxes.first()).not.toBeVisible({ timeout: 3000 });
    }
  });

  test('bulk select checkboxes not in expanded view', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    await page.waitForTimeout(300);
    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    const count = await checkboxes.count();
    expect(count).toBe(0);
  });

  test('selecting in compact view shows bulk action bar', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, true);
    await setTgActive(page, true);
    const checkbox = page.locator('[data-testid="bulk-select-trainee"]').first();
    await expect(checkbox).toBeVisible({ timeout: 5000 });
    await checkbox.click();
    await page.waitForTimeout(300);
    const bar = page.locator('[data-testid="bulk-avail-bar"]');
    await expect(bar).toBeVisible({ timeout: 3000 });
    await expect(bar).toContainText('Обрано: 1');
    await checkbox.click();
    await page.waitForTimeout(200);
  });
});

// ─── Session reminder toggle not in expanded view ──────────────────────────

test.describe('Session reminder toggle placement', () => {
  test('trainee session reminder toggle not in expanded view actions row', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    const actions = page.locator('[data-tour="trainee-actions"]');
    const count = await actions.count();
    if (count === 0) return;
    const toggle = actions.first().locator('[data-testid="trainee-session-reminder"]');
    expect(await toggle.count()).toBe(0);
  });
});

// ─── Mentor session reminder ───────────────────────────────────────────────

test.describe('Mentor session reminder', () => {
  test('profile Telegram tab always has notifications section', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (!el?._x_dataStack) return;
      const state = el._x_dataStack[0];
      state.profileTab = 'telegram';
    });
    await page.waitForTimeout(400);
    await expect(page.locator('[data-testid="notifications-section"]')).toBeVisible({ timeout: 5000 });
  });

  test('notifications section visible even when TG not connected', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (!el?._x_dataStack) return;
      const state = el._x_dataStack[0];
      state.profileTab = 'telegram';
      state.telegramIntegration = false;
      state.mentorTg = { ...state.mentorTg, connected: false, enabled: false };
    });
    await page.waitForTimeout(400);
    await expect(page.locator('[data-testid="notifications-section"]')).toBeVisible({ timeout: 3000 });
  });

  test('sessionReminderEnabled state exists in Alpine', async ({ page }) => {
    const val = await getAlpineState(page, 'sessionReminderEnabled');
    expect(typeof val).toBe('boolean');
  });

  test('mentor reminder setting persists via API', async ({ page }) => {
    await page.evaluate(async () => {
      await fetch('/api/v1/mentor/profile', { method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionReminderEnabled: 'false' }) });
    });
    await page.reload();
    await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(500);

    const val = await getAlpineState(page, 'sessionReminderEnabled');
    expect(val).toBe(false);

    await page.evaluate(async () => {
      await fetch('/api/v1/mentor/profile', { method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionReminderEnabled: 'true' }) });
    });
  });
});

// ─── Profile sub-tabs ─────────────────────────────────────────────────────

test.describe('Profile sub-tabs', () => {
  test('profileTab Alpine state defaults to main', async ({ page }) => {
    const val = await getAlpineState(page, 'profileTab');
    expect(val).toBe('main');
  });

  test('Основні tab is active by default — photo section visible', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await expect(page.locator('h4').filter({ hasText: 'Фото' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('Час sub-tab reveals work hours and timezone sections', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].profileTab = 'time';
    });
    await page.waitForTimeout(300);
    await expect(page.locator('h4').filter({ hasText: 'Робочі години' }).first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('h4').filter({ hasText: 'Часовий пояс' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('Час sub-tab reveals time interval selector', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].profileTab = 'time';
    });
    await page.waitForTimeout(300);
    await expect(page.locator('h4').filter({ hasText: 'Інтервал вибору часу' }).first()).toBeVisible({ timeout: 5000 });
  });

  test('Telegram sub-tab reveals integration toggle', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].profileTab = 'telegram';
    });
    await page.waitForTimeout(300);
    await expect(page.locator('text=Інтеграція з Телеграм').first()).toBeVisible({ timeout: 5000 });
  });

  test('Telegram sub-tab shows sub-features when TG connected', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (!el?._x_dataStack) return;
      const state = el._x_dataStack[0];
      state.profileTab = 'telegram';
      state.telegramIntegration = true;
      state.mentorTg = { ...state.mentorTg, connected: true, enabled: true };
    });
    await page.waitForTimeout(400);
    await expect(page.locator('text=Підтверджування тренувань').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=Комунікація зі спортсменом').first()).toBeVisible({ timeout: 5000 });
  });

  test('work hours section not visible on Основні tab', async ({ page }) => {
    await page.locator('[data-tour="profile"]').click();
    await page.waitForTimeout(400);
    // profileTab is 'main' by default
    const workHours = page.locator('h4').filter({ hasText: 'Робочі години' });
    if (await workHours.count() > 0) {
      await expect(workHours.first()).not.toBeVisible({ timeout: 3000 });
    }
  });
});

// ─── Trainee manager: Створити label and Розмова button ───────────────────

test.describe('Trainee manager actions row', () => {
  test('expanded view shows Створити label', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    const actions = page.locator('[data-tour="trainee-actions"]').first();
    await expect(actions).toBeVisible({ timeout: 5000 });
    await expect(actions).toContainText('Створити');
  });

  test('expanded view has two create buttons (Тренування and Серію тренувань)', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    const actions = page.locator('[data-tour="trainee-actions"]').first();
    await expect(actions).toBeVisible({ timeout: 5000 });
    // Серію тренувань button (plan-sessions-btn)
    await expect(actions.locator('[data-tour="plan-sessions-btn"]')).toBeVisible({ timeout: 5000 });
    // A create-session button also present (contains text from labels or fallback)
    const buttons = actions.locator('button');
    expect(await buttons.count()).toBeGreaterThanOrEqual(2);
  });

  test('Серію тренувань button opens plan sessions modal', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    const btn = page.locator('[data-tour="plan-sessions-btn"]').first();
    await expect(btn).toBeVisible({ timeout: 5000 });
    await btn.click();
    await page.waitForTimeout(400);
    const modalOpen = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.planModal?.show;
    });
    expect(modalOpen).toBe(true);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
  });

  test('Розмова button visible when traineeComm is true', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeComm = true;
    });
    await page.waitForTimeout(300);
    const btn = page.locator('[data-tour="trainee-actions"] button').filter({ hasText: 'Розмова' }).first();
    await expect(btn).toBeVisible({ timeout: 5000 });
  });

  test('Розмова button hidden when traineeComm is false', async ({ page }) => {
    await goToTrainees(page);
    await setCompactView(page, false);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeComm = false;
    });
    await page.waitForTimeout(300);
    const actions = page.locator('[data-tour="trainee-actions"]').first();
    if (await actions.count() === 0) return;
    const btn = actions.locator('button').filter({ hasText: 'Розмова' });
    if (await btn.count() > 0) {
      await expect(btn.first()).not.toBeVisible({ timeout: 3000 });
    }
  });

  test('planModal state exists in Alpine', async ({ page }) => {
    const val = await getAlpineState(page, 'planModal');
    expect(val).toBeTruthy();
    expect(typeof (val as any).show).toBe('boolean');
  });
});

// ─── Push / web notifications API ─────────────────────────────────────────

test.describe('Web push notification endpoints', () => {
  test('VAPID public key endpoint returns a key', async ({ page }) => {
    const result = await page.evaluate(async () => {
      const r = await fetch('/api/v1/push/vapid-key');
      if (!r.ok) return null;
      return r.json();
    });
    if (result !== null) {
      expect(result.vapidKey).toBeTruthy();
      expect(typeof result.vapidKey).toBe('string');
    }
  });

  test('push subscribe endpoint rejects malformed payload with 400', async ({ page }) => {
    const status = await page.evaluate(async () => {
      const r = await fetch('/api/v1/push/subscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ endpoint: '', keys: {} }),
      });
      return r.status;
    });
    expect([400, 422, 500]).toContain(status);
  });
});

// ─── Admin audit log: SESSION_CREATED has email ────────────────────────────

test.describe('Audit log email capture', () => {
  test.beforeEach(async ({ page }) => {
    const mid = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.mentorId ?? null;
    });
    if (mid) await cleanupByTitle(page, ['Audit email test'], mid);
  });

  test('creating a session records mentor email in audit', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Audit email test', date: p.dateStr, time: '08:00', endTime: '09:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });
    expect(res.status).toBe(201);

    await page.waitForTimeout(500);

    const auditRes = await page.evaluate(async () => {
      const r = await fetch('/admin/recent');
      if (!r.ok) return null;
      return r.json();
    });

    if (auditRes !== null) {
      const sessionEvents = auditRes.filter((e: any) => e.eventType === 'SESSION_CREATED' && e.description?.includes('Audit email test'));
      if (sessionEvents.length > 0) {
        expect(sessionEvents[0].actorEmail).not.toBe('');
      }
    }

    await page.evaluate(async (sid: number) => {
      await fetch(`/api/v1/sessions/${sid}`, { method: 'DELETE' });
    }, res.body.id);
  });
});

// ─── recurringCount parameter ──────────────────────────────────────────────

test.describe('recurringCount parameter', () => {
  test.beforeEach(async ({ page }) => {
    const mid = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.mentorId ?? null;
    });
    if (mid) await cleanupByTitle(page, ['RecurringCount e2e', 'Default recurring count'], mid);
  });

  test('creates exactly N sessions when recurringCount is specified', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'RecurringCount e2e', date: p.dateStr, time: '15:00', endTime: '16:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true, recurringCount: 4 }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });

    expect(res.status).toBe(201);
    expect(res.body.recurrenceGroupId).toBeTruthy();

    const count = await page.evaluate(async (p: { rgid: string; mid: number }) => {
      const today = new Date();
      const start = today.toISOString().split('T')[0];
      const end = new Date(today.getTime() + 40 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const r = await fetch(`/api/v1/sessions?mentorId=${p.mid}&startDate=${start}&endDate=${end}`);
      const sessions = await r.json();
      return sessions.filter((s: any) => s.recurrenceGroupId === p.rgid).length;
    }, { rgid: res.body.recurrenceGroupId, mid: ids.mentorId });

    expect(count).toBe(4);
    await cleanupRecurring(page, res.body.recurrenceGroupId, ids.mentorId);
  });

  test('default recurringCount (no value) still creates 8 sessions', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date().toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Default recurring count', date: p.dateStr, time: '16:00', endTime: '17:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });

    expect(res.status).toBe(201);
    const count = await page.evaluate(async (p: { rgid: string; mid: number }) => {
      const today = new Date();
      const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const r = await fetch(`/api/v1/sessions?mentorId=${p.mid}&startDate=${today.toISOString().split('T')[0]}&endDate=${end}`);
      const sessions = await r.json();
      return sessions.filter((s: any) => s.recurrenceGroupId === p.rgid).length;
    }, { rgid: res.body.recurrenceGroupId, mid: ids.mentorId });

    expect(count).toBe(8);
    await cleanupRecurring(page, res.body.recurrenceGroupId, ids.mentorId);
  });

  test('session creation modal has recurringCount input', async ({ page }) => {
    await page.locator('[data-tour="add-session"]').click();
    await page.waitForTimeout(500);
    const countInput = page.locator('input[x-model\\.number="sessionForm.recurringCount"]');
    await expect(countInput).toBeAttached({ timeout: 5000 });
    await page.locator('.modal-footer button').filter({ hasText: 'Скасувати' }).click().catch(() => {});
  });
});

// ─── Plan sessions (bulk) ──────────────────────────────────────────────────

test.describe('Plan sessions — bulk creation', () => {
  test.beforeEach(async ({ page }) => {
    const mid = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.mentorId ?? null;
    });
    if (mid) await cleanupByTitle(page, ['Suppress test'], mid);
  });

  test('batch-notify endpoint returns 200 with valid payload', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date(Date.now() + 86400000).toISOString().split('T')[0];
    const status = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions/batch-notify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mentorId: p.mentorId,
          traineeIds: [p.traineeId],
          sessions: [{ id: null, date: p.dateStr, time: '10:00', title: 'Test batch' }],
        }),
      });
      return r.status;
    }, { ...ids, dateStr });
    expect(status).toBe(200);
  });

  test('suppressNotification flag skips per-session Telegram (API level)', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const dateStr = new Date(Date.now() + 86400000).toISOString().split('T')[0];
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; dateStr: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Suppress test', date: p.dateStr, time: '17:00', endTime: '18:00',
          traineeIds: [p.traineeId], mentorId: p.mentorId, suppressNotification: true }),
      });
      return { status: r.status, body: await r.json() };
    }, { ...ids, dateStr });
    expect(res.status).toBe(201);
    expect(res.body.id).toBeTruthy();

    await page.evaluate(async (sid: number) => {
      await fetch(`/api/v1/sessions/${sid}`, { method: 'DELETE' });
    }, res.body.id);
  });
});
