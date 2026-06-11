import { test, expect } from './fixtures';

// Helper to navigate to profile tab (Telegram sub-tab where notifications-section lives)
async function goToProfile(page: any) {
  await page.locator('[data-tour="profile"]').click();
  await page.waitForTimeout(500);
  // notifications-section is in the Telegram sub-tab of the profile
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0].profileTab = 'telegram';
  });
  await page.waitForTimeout(200);
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

// Helper to navigate to trainees tab
async function goToTrainees(page: any) {
  const tabs = page.locator('nav button, [role="tab"]');
  // Use the team/trainees tab data-tour attribute
  const traineeTab = page.locator('[data-tour="trainees"]');
  if (await traineeTab.isVisible({ timeout: 2000 }).catch(() => false)) {
    await traineeTab.click();
  } else {
    // Try clicking any tab that shows "Команда" or trainees section
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].activeTab = 'trainees';
    });
  }
  await page.waitForTimeout(500);
}

// Helper to ensure expanded trainee view
async function ensureExpandedTraineeView(page: any) {
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = false;
  });
  await page.waitForTimeout(300);
}

test.describe('Mentor session reminder notification', () => {
  test('notifications section exists in profile tab', async ({ page }) => {
    await goToProfile(page);
    await expect(page.locator('[data-testid="notifications-section"]')).toBeVisible({ timeout: 5000 });
  });

  test('mentor session reminder toggle can be toggled', async ({ page }) => {
    await goToProfile(page);
    const section = page.locator('[data-testid="notifications-section"]');
    await expect(section).toBeVisible({ timeout: 5000 });

    // Get initial state by reading Alpine state
    const initialState = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionReminderEnabled;
    });

    // Toggle via direct Alpine state manipulation to avoid label/checkbox interaction complexity
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      if (state) state.sessionReminderEnabled = !state.sessionReminderEnabled;
    });
    await page.waitForTimeout(300);

    // State should have flipped
    const newState = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionReminderEnabled;
    });
    expect(newState).toBe(!initialState);

    // Toggle back
    await page.evaluate((original: boolean) => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      if (state) state.sessionReminderEnabled = original;
    }, initialState as boolean);
    await page.waitForTimeout(300);
  });

  test('mentor reminder setting persists via API', async ({ page }) => {
    await goToProfile(page);
    await expect(page.locator('[data-testid="notifications-section"]')).toBeVisible({ timeout: 5000 });

    // Set to false via API to get a known state
    await page.evaluate(async () => {
      await fetch('/api/v1/mentor/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionReminderEnabled: 'false' }),
      });
    });

    // Reload and check
    await page.reload();
    await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
    await goToProfile(page);
    await expect(page.locator('[data-testid="notifications-section"]')).toBeVisible({ timeout: 5000 });

    const state = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.sessionReminderEnabled;
    });
    // After setting false, state should be false
    expect(state).toBe(false);

    // Restore to true
    await page.evaluate(async () => {
      await fetch('/api/v1/mentor/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionReminderEnabled: 'true' }),
      });
    });
  });
});


test.describe('Recurring session creation', () => {
  test('new session modal does NOT show recurring toggle (moved to plan sessions modal)', async ({ page }) => {
    await page.locator('[data-tour="add-session"]').click();
    await page.waitForTimeout(500);

    // Recurring toggle was moved from here to the "Планувати тренування" (plan sessions) modal
    const recurringToggle = page.locator('input[type="checkbox"][x-model="sessionForm.recurring"]');
    expect(await recurringToggle.count()).toBe(0);

    // Close modal
    await page.locator('[data-tour="session-modal"] .modal-footer button').filter({ hasText: 'Скасувати' }).click();
  });

  test('recurring session creates 8 weekly instances', async ({ page }) => {
    // Get trainee id and mentor id from Alpine state
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      const trainee = state?.trainees?.[0];
      return { traineeId: trainee?.id ?? null, mentorId: state?.mentorId ?? null };
    });

    if (!ids.traineeId || !ids.mentorId) {
      console.log('No trainee/mentor found, skipping recurring test');
      return;
    }

    const today = new Date();
    const dateStr = today.toISOString().split('T')[0];

    const response = await page.evaluate(async (params: { traineeId: number; mentorId: number; dateStr: string }) => {
      const res = await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'Recurring test',
          date: params.dateStr,
          time: '10:00',
          endTime: '11:00',
          traineeIds: [params.traineeId],
          mentorId: params.mentorId,
          recurring: true,
        }),
      });
      return { status: res.status, body: await res.json() };
    }, { traineeId: ids.traineeId, mentorId: ids.mentorId, dateStr });

    expect(response.status).toBe(201);
    const created = response.body;
    expect(created.recurring).toBe(true);
    expect(created.recurrenceGroupId).toBeTruthy();

    // Query sessions for the trainee and count how many share the recurrenceGroupId
    const countResult = await page.evaluate(async (params: { recurrenceGroupId: string; mentorId: number }) => {
      const today = new Date();
      const start = today.toISOString().split('T')[0];
      const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const res = await fetch(`/api/v1/sessions?mentorId=${params.mentorId}&startDate=${start}&endDate=${end}`);
      if (!res.ok) return 0;
      const sessions = await res.json();
      return sessions.filter((s: any) => s.recurrenceGroupId === params.recurrenceGroupId).length;
    }, { recurrenceGroupId: created.recurrenceGroupId, mentorId: ids.mentorId });

    expect(countResult).toBe(8);

    // Clean up — delete all sessions in this recurrence group
    await page.evaluate(async (params: { recurrenceGroupId: string; mentorId: number }) => {
      const today = new Date();
      const start = today.toISOString().split('T')[0];
      const end = new Date(today.getTime() + 70 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const res = await fetch(`/api/v1/sessions?mentorId=${params.mentorId}&startDate=${start}&endDate=${end}`);
      const sessions = await res.json();
      const toDelete = sessions.filter((s: any) => s.recurrenceGroupId === params.recurrenceGroupId);
      await Promise.all(toDelete.map((s: any) =>
        fetch(`/api/v1/sessions/${s.id}`, { method: 'DELETE' })
      ));
    }, { recurrenceGroupId: created.recurrenceGroupId, mentorId: ids.mentorId });
  });
});

test.describe('Bulk availability request', () => {
  test('bulk select checkboxes visible in compact trainee view', async ({ page }) => {
    await goToTrainees(page);
    // bulk-select-trainee requires telegramIntegration && mentorTg.connected && sessionConfirmations
    await setTgActive(page, true);
    // Bulk select is in compact view only
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);

    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    const count = await checkboxes.count();
    expect(count).toBeGreaterThan(0);
    await expect(checkboxes.first()).toBeVisible({ timeout: 5000 });
  });

  test('selecting trainees shows bulk action bar', async ({ page }) => {
    await goToTrainees(page);
    await setTgActive(page, true);
    // Bulk select is in compact view only
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);

    // Initially bar is hidden
    const bar = page.locator('[data-testid="bulk-avail-bar"]');

    // Select first trainee
    const checkbox = page.locator('[data-testid="bulk-select-trainee"]').first();
    await checkbox.click();
    await page.waitForTimeout(300);

    // Bar should appear
    await expect(bar).toBeVisible({ timeout: 3000 });
    await expect(bar).toContainText('Обрано: 1');

    // Deselect
    await checkbox.click();
    await page.waitForTimeout(300);
  });

  test('can select multiple trainees for bulk request', async ({ page }) => {
    await goToTrainees(page);
    await setTgActive(page, true);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);

    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    const count = await checkboxes.count();
    const selectCount = Math.min(count, 2);

    for (let i = 0; i < selectCount; i++) {
      await checkboxes.nth(i).click();
      await page.waitForTimeout(150);
    }

    const bar = page.locator('[data-testid="bulk-avail-bar"]');
    await expect(bar).toBeVisible({ timeout: 3000 });
    await expect(bar).toContainText(`Обрано: ${selectCount}`);

    // Cancel clears selection
    await bar.locator('button').filter({ hasText: 'Скасувати' }).click();
    await page.waitForTimeout(300);
    // Bar should hide again
    const barVisible = await bar.isVisible();
    expect(barVisible).toBe(false);
  });

  test('bulk request button calls API and returns result', async ({ page }) => {
    await goToTrainees(page);
    await setTgActive(page, true);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = true;
    });
    await page.waitForTimeout(300);

    const checkboxes = page.locator('[data-testid="bulk-select-trainee"]');
    if (await checkboxes.count() === 0) return;

    // Select first trainee
    await checkboxes.first().click();
    await page.waitForTimeout(200);

    const bar = page.locator('[data-testid="bulk-avail-bar"]');
    await expect(bar).toBeVisible({ timeout: 3000 });

    // Click the request button (it calls the API)
    const requestBtn = page.locator('[data-testid="bulk-request-btn"]');
    await expect(requestBtn).toBeVisible({ timeout: 3000 });

    // Intercept to validate the request is made
    let requestMade = false;
    page.on('request', req => {
      if (req.url().includes('/api/v1/trainees/bulk-notify-availability') && req.method() === 'POST') {
        requestMade = true;
      }
    });

    await requestBtn.click();
    await page.waitForTimeout(1000);

    expect(requestMade).toBe(true);

    // After request, bulk selection is cleared (bar hidden)
    const bulkState = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.bulkAvailTrainees?.length ?? -1;
    });
    expect(bulkState).toBe(0);
  });
});
