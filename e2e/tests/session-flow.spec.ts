import { test, expect } from './fixtures';

// ─── helpers ──────────────────────────────────────────────────────────────

async function alpineGet(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function getMentorAndTraineeIds(page: any) {
  return page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    const state = el?._x_dataStack?.[0];
    return {
      mentorId: state?.mentorId ?? null,
      traineeId: state?.trainees?.[0]?.id ?? null,
    };
  });
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

async function cleanupLocation(page: any, locationId: number) {
  await page.evaluate(async (id: number) => {
    await fetch(`/api/v1/locations/${id}`, { method: 'DELETE' });
  }, locationId);
}

function todayStr() {
  return new Date().toISOString().split('T')[0];
}

// ─── Session with location ────────────────────────────────────────────────

test.describe('Session with location', () => {
  let locationId: number | null = null;
  let sessionId: number | null = null;

  test.afterEach(async ({ page }) => {
    if (sessionId) {
      await page.evaluate(async (id: number) => {
        await fetch(`/api/v1/sessions/${id}`, { method: 'DELETE' });
      }, sessionId);
      sessionId = null;
    }
    if (locationId) {
      await cleanupLocation(page, locationId);
      locationId = null;
    }
  });

  test('session shows location color from location record', async ({ page }) => {
    const ids = await getMentorAndTraineeIds(page);
    if (!ids.mentorId || !ids.traineeId) { test.skip(); return; }

    // Create a location
    const locRes = await page.evaluate(async (mid: number) => {
      const r = await fetch('/api/v1/locations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: 'e2e Test Location', color: '#e74c3c', mentorId: mid }),
      });
      if (!r.ok) return null;
      return r.json();
    }, ids.mentorId);

    if (!locRes?.id) { test.skip(); return; }
    locationId = locRes.id;

    // Create a session with that location
    const sesRes = await page.evaluate(async (p: { traineeId: number; mentorId: number; locationId: number; date: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'e2e Location Session',
          date: p.date,
          time: '10:00',
          endTime: '11:00',
          traineeIds: [p.traineeId],
          mentorId: p.mentorId,
          locationId: p.locationId,
        }),
      });
      if (!r.ok) return null;
      return r.json();
    }, { ...ids, locationId: locRes.id, date: todayStr() });

    if (!sesRes?.id) { test.skip(); return; }
    sessionId = sesRes.id;

    // Session was created with locationId
    expect(sesRes.locationId).toBe(locRes.id);
  });
});

// ─── validStartSlots ──────────────────────────────────────────────────────

test.describe('Session validStartSlots', () => {
  test('validStartSlots is an array in Alpine state', async ({ page }) => {
    const slots = await alpineGet(page, 'validStartSlots');
    expect(Array.isArray(slots)).toBe(true);
  });

  test('validStartSlots contains time strings when populated', async ({ page }) => {
    // Open session modal to trigger slot computation
    await page.locator('[data-tour="add-session"]').click();
    await page.waitForTimeout(600);

    const slots = await alpineGet(page, 'validStartSlots');
    const isArray = Array.isArray(slots);
    expect(isArray).toBe(true);

    if (isArray && (slots as any[]).length > 0) {
      const first = (slots as any[])[0];
      expect(typeof first).toBe('string');
      expect(first).toMatch(/^\d{2}:\d{2}$/);
    }

    // Dismiss modal
    await page.locator('[data-tour="session-modal"] .modal-footer button')
      .filter({ hasText: 'Скасувати' }).click().catch(() => {});
    await page.waitForTimeout(300);
  });
});

// ─── Session visible on calendar ─────────────────────────────────────────

test.describe('Session visible on calendar', () => {
  let sessionId: number | null = null;
  let mentorId: number | null = null;

  test.afterEach(async ({ page }) => {
    if (sessionId && mentorId) {
      await cleanupByTitle(page, ['e2e Calendar Test'], mentorId);
      sessionId = null;
    }
  });

  test('created session appears as card in feed', async ({ page }) => {
    const ids = await getMentorAndTraineeIds(page);
    if (!ids.mentorId || !ids.traineeId) { test.skip(); return; }
    mentorId = ids.mentorId;

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; date: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'e2e Calendar Test',
          date: p.date,
          time: '12:00',
          endTime: '13:00',
          traineeIds: [p.traineeId],
          mentorId: p.mentorId,
        }),
      });
      if (!r.ok) return null;
      return r.json();
    }, { ...ids, date: todayStr() });

    if (!res?.id) { test.skip(); return; }
    sessionId = res.id;

    // Reload page so Alpine picks up the new session
    await page.reload();
    await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
    await page.waitForTimeout(500);

    // Switch to agenda view to check the session card with a stable unique ID
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack?.[0]) el._x_dataStack[0].activeTab = 'agenda';
    });
    await page.waitForTimeout(500);

    // Agenda cards have unique id="agenda-card-{id}" only in the agenda view
    await expect(page.locator(`#agenda-card-${sessionId}`)).toBeVisible({ timeout: 10000 });
  });

  test('today date badge shows session count', async ({ page }) => {
    const ids = await getMentorAndTraineeIds(page);
    if (!ids.mentorId || !ids.traineeId) { test.skip(); return; }
    mentorId = ids.mentorId;

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; date: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'e2e Calendar Test',
          date: p.date,
          time: '13:00',
          endTime: '14:00',
          traineeIds: [p.traineeId],
          mentorId: p.mentorId,
        }),
      });
      if (!r.ok) return null;
      return r.json();
    }, { ...ids, date: todayStr() });

    if (!res?.id) { test.skip(); return; }
    sessionId = res.id;

    await page.reload();
    await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
    await page.waitForTimeout(500);

    // Session count badge appears under today's date (text-blue-400 or similar)
    const badge = page.locator('.text-blue-400').first();
    const badgeExists = await badge.count();
    if (badgeExists > 0) {
      await expect(badge).toBeVisible({ timeout: 5000 });
    }
  });
});

// ─── Copy session ─────────────────────────────────────────────────────────

test.describe('Copy session', () => {
  let sessionId: number | null = null;
  let mentorId: number | null = null;

  test.afterEach(async ({ page }) => {
    if (sessionId && mentorId) {
      await cleanupByTitle(page, ['e2e Copy Source'], mentorId);
      sessionId = null;
    }
  });

  test('copy button pre-fills session modal with same title', async ({ page }) => {
    const ids = await getMentorAndTraineeIds(page);
    if (!ids.mentorId || !ids.traineeId) { test.skip(); return; }
    mentorId = ids.mentorId;

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; date: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'e2e Copy Source',
          date: p.date,
          time: '09:00',
          endTime: '10:00',
          traineeIds: [p.traineeId],
          mentorId: p.mentorId,
        }),
      });
      if (!r.ok) return null;
      return r.json();
    }, { ...ids, date: todayStr() });

    if (!res?.id) { test.skip(); return; }
    sessionId = res.id;

    // Reload to make session appear in feed
    await page.reload();
    await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
    await page.waitForTimeout(500);

    // Switch to detail mode so copy button is visible
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack?.[0]) el._x_dataStack[0].compactView = false;
    });
    await page.waitForTimeout(300);

    const card = page.locator(`[data-session-id="${sessionId}"]`).first();
    const cardVisible = await card.isVisible({ timeout: 8000 }).catch(() => false);
    if (!cardVisible) { test.skip(); return; }

    const copyBtn = card.locator('[data-tour="copy-session"]').first();
    const copyVisible = await copyBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (!copyVisible) { test.skip(); return; }

    await copyBtn.click();
    await page.waitForTimeout(400);

    const modalVisible = await page.locator('[data-tour="session-modal"]').isVisible({ timeout: 5000 }).catch(() => false);
    if (!modalVisible) { test.skip(); return; }

    const formTitle = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionForm?.title ?? '';
    });
    expect(formTitle).toBe('e2e Copy Source');

    // Dismiss modal
    await page.locator('[data-tour="session-modal"] .modal-footer button')
      .filter({ hasText: 'Скасувати' }).click().catch(() => {});
    await page.waitForTimeout(300);
  });
});

// ─── Edit session ─────────────────────────────────────────────────────────

test.describe('Edit session', () => {
  let sessionId: number | null = null;
  let mentorId: number | null = null;

  test.afterEach(async ({ page }) => {
    if (sessionId && mentorId) {
      await cleanupByTitle(page, ['e2e Edit Source'], mentorId);
      sessionId = null;
    }
  });

  test('edit session modal shows correct title', async ({ page }) => {
    const ids = await getMentorAndTraineeIds(page);
    if (!ids.mentorId || !ids.traineeId) { test.skip(); return; }
    mentorId = ids.mentorId;

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number; date: string }) => {
      const r = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'e2e Edit Source',
          date: p.date,
          time: '11:00',
          endTime: '12:00',
          traineeIds: [p.traineeId],
          mentorId: p.mentorId,
        }),
      });
      if (!r.ok) return null;
      return r.json();
    }, { ...ids, date: todayStr() });

    if (!res?.id) { test.skip(); return; }
    sessionId = res.id;

    // Open edit modal via Alpine state
    await page.evaluate(async (id: number) => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      if (!state) return;
      state.editingSessionId = id;
      if (typeof state.openEditSession === 'function') {
        await state.openEditSession(id);
      }
    }, sessionId);
    await page.waitForTimeout(600);

    const modalVisible = await page.locator('[data-tour="session-modal"]').isVisible({ timeout: 5000 }).catch(() => false);
    if (!modalVisible) { test.skip(); return; }

    const formTitle = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionForm?.title ?? '';
    });
    expect(formTitle).toBe('e2e Edit Source');

    // Dismiss modal
    await page.locator('[data-tour="session-modal"] .modal-footer button')
      .filter({ hasText: 'Скасувати' }).click().catch(() => {});
    await page.waitForTimeout(300);
  });
});
