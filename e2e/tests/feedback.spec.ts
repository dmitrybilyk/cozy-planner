import { test, expect } from './fixtures';

// ─── helpers ───────────────────────────────────────────────────────────────

async function getAlpineState(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

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

// ─── API-level tests ────────────────────────────────────────────────────────

test.describe('Feedback API', () => {
  test('POST /api/v1/feedback creates feedback from mentor to trainee', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromMentorId: p.mentorId,
          toTraineeId: p.traineeId,
          text: 'Feedback e2e test',
          tags: 'Відмінна робота!',
          rating: 5,
        }),
      });
      return { status: r.status, body: await r.json() };
    }, ids);

    expect(res.status).toBe(201);
    expect(res.body.id).toBeTruthy();
    expect(res.body.toTraineeId).toBe(ids.traineeId);
    expect(res.body.fromMentorId).toBe(ids.mentorId);
    expect(res.body.tags).toBe('Відмінна робота!');
    expect(res.body.rating).toBe(5);
  });

  test('GET /api/v1/feedback/received-by-trainee returns feedback list', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    // Create feedback first
    await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromMentorId: p.mentorId,
          toTraineeId: p.traineeId,
          text: 'Test feedback for received check',
          rating: 4,
        }),
      });
    }, ids);

    const result = await page.evaluate(async (traineeId: number) => {
      const r = await fetch(`/api/v1/feedback/received-by-trainee?traineeId=${traineeId}`);
      return { status: r.status, body: await r.json() };
    }, ids.traineeId);

    expect(result.status).toBe(200);
    expect(Array.isArray(result.body)).toBe(true);
    const myFeedback = result.body.filter((f: any) => f.text?.includes('Test feedback for received check'));
    expect(myFeedback.length).toBeGreaterThan(0);
  });

  test('GET /api/v1/feedback/sent-by-mentor returns sent feedback', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromMentorId: p.mentorId,
          toTraineeId: p.traineeId,
          text: 'Sent by mentor test',
          tags: 'Гарна техніка',
        }),
      });
    }, ids);

    const result = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch(`/api/v1/feedback/sent-by-mentor?mentorId=${p.mentorId}&traineeId=${p.traineeId}`);
      return { status: r.status, body: await r.json() };
    }, ids);

    expect(result.status).toBe(200);
    expect(Array.isArray(result.body)).toBe(true);
    expect(result.body.length).toBeGreaterThan(0);
    const mine = result.body.filter((f: any) => f.text === 'Sent by mentor test');
    expect(mine.length).toBeGreaterThan(0);
    expect(mine[0].tags).toContain('Гарна техніка');
  });

  test('POST /api/v1/feedback/{id}/read marks feedback as read', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const created = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromMentorId: p.mentorId,
          toTraineeId: p.traineeId,
          text: 'Read test feedback',
        }),
      });
      return r.json();
    }, ids);

    expect(created.isRead).toBe(false);

    const readRes = await page.evaluate(async (id: number) => {
      const r = await fetch(`/api/v1/feedback/${id}/read`, { method: 'POST' });
      return r.status;
    }, created.id);

    expect(readRes).toBe(200);
  });

  test('POST /api/v1/feedback rejects missing sender', async ({ page }) => {
    const status = await page.evaluate(async () => {
      const r = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toTraineeId: 1, text: 'Missing sender' }),
      });
      return r.status;
    });
    expect(status).toBe(400);
  });

  test('POST /api/v1/feedback accepts trainee feedback to mentor', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromTraineeId: p.traineeId,
          toMentorId: p.mentorId,
          text: 'Trainee feedback e2e',
          tags: 'Дякую!,Корисно',
          rating: 5,
        }),
      });
      return { status: r.status, body: await r.json() };
    }, ids);

    expect(res.status).toBe(201);
    expect(res.body.fromTraineeId).toBe(ids.traineeId);
    expect(res.body.toMentorId).toBe(ids.mentorId);
  });

  test('GET /api/v1/feedback/received-by-mentor returns trainee feedback', async ({ page }) => {
    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const state = el?._x_dataStack?.[0];
      return {
        traineeId: state?.trainees?.[0]?.id ?? null,
        mentorId: state?.mentorId ?? null,
      };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromTraineeId: p.traineeId,
          toMentorId: p.mentorId,
          text: 'Mentor received test',
        }),
      });
    }, ids);

    const result = await page.evaluate(async (mentorId: number) => {
      const r = await fetch(`/api/v1/feedback/received-by-mentor?mentorId=${mentorId}`);
      return { status: r.status, body: await r.json() };
    }, ids.mentorId);

    expect(result.status).toBe(200);
    expect(Array.isArray(result.body)).toBe(true);
    const mine = result.body.filter((f: any) => f.text === 'Mentor received test');
    expect(mine.length).toBeGreaterThan(0);
  });
});

// ─── UI-level tests ─────────────────────────────────────────────────────────

test.describe('Feedback UI — Coach', () => {
  test('coach view has feedbackModal state in Alpine', async ({ page }) => {
    const val = await getAlpineState(page, 'feedbackModal');
    expect(val).toBeTruthy();
    expect(typeof (val as any).show).toBe('boolean');
  });

  test('COACH_FEEDBACK_TAGS exists in Alpine state', async ({ page }) => {
    const tags = await getAlpineState(page, 'COACH_FEEDBACK_TAGS');
    expect(Array.isArray(tags)).toBe(true);
    expect((tags as string[]).length).toBeGreaterThan(0);
  });

  test('Feedback button appears in trainee expanded view', async ({ page }) => {
    await goToTrainees(page);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = false;
    });
    await page.waitForTimeout(400);
    const btn = page.locator('[data-tour="trainee-actions"] button').filter({ hasText: 'Розмова' }).first();
    await expect(btn).toBeVisible({ timeout: 5000 });
  });

  test('Clicking Feedback button opens feedback modal', async ({ page }) => {
    await goToTrainees(page);
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].traineeCompactView = false;
    });
    await page.waitForTimeout(400);
    const btn = page.locator('[data-tour="trainee-actions"] button').filter({ hasText: 'Розмова' }).first();
    await expect(btn).toBeVisible({ timeout: 5000 });
    await btn.click();
    await page.waitForTimeout(400);
    const modalOpen = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.conversationModal?.show;
    });
    expect(modalOpen).toBe(true);
    // Close via Escape
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
  });

  test('Feedback modal contains tag buttons', async ({ page }) => {
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) {
        const state = el._x_dataStack[0];
        state.feedbackModal = { show: true, traineeId: 1, traineeName: 'Test', text: '', tags: [], rating: 0 };
      }
    });
    await page.waitForTimeout(300);
    const tags = page.locator('[x-data] [x-show="feedbackModal.show"] button').filter({ hasText: /Відмінна|Прогрес|Техніка/i });
    expect(await tags.count()).toBeGreaterThan(0);
    // Close
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el?._x_dataStack) el._x_dataStack[0].feedbackModal.show = false;
    });
  });
});

// ─── Trainee UI helpers ──────────────────────────────────────────────────────

async function getTraineeToken(page: any): Promise<string | null> {
  const token = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.trainees?.[0]?.inviteToken ?? null;
  });
  return token;
}

async function goToTraineePage(page: any, token: string) {
  await page.goto(`/trainee/${token}`);
  await page.waitForSelector('[x-data]', { timeout: 15000 });
  await page.waitForTimeout(1000);
}

async function getTraineeAlpineState(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

// ─── Trainee UI tests ────────────────────────────────────────────────────────

test.describe('Feedback UI — Trainee', () => {
  test('trainee sessions page has feedback tab', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const feedbackTab = page.locator('button').filter({ hasText: '💬' }).first();
    await expect(feedbackTab).toBeVisible({ timeout: 5000 });
  });

  test('trainee page has feedbackModal state', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const val = await getTraineeAlpineState(page, 'feedbackModal');
    expect(val).toBeTruthy();
    expect(typeof (val as any).show).toBe('boolean');
  });

  test('trainee page has TRAINEE_FEEDBACK_TAGS state', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const tags = await getTraineeAlpineState(page, 'TRAINEE_FEEDBACK_TAGS');
    expect(Array.isArray(tags)).toBe(true);
    expect((tags as string[]).length).toBeGreaterThan(0);
  });

  test('trainee page has conversation state array', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const conversation = await getTraineeAlpineState(page, 'conversation');
    expect(Array.isArray(conversation)).toBe(true);
  });

  test('trainee sends feedback and it appears in conversation', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return { traineeId: s?.traineeId ?? null, mentorId: s?.me?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    // Post feedback via API
    const res = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fromTraineeId: p.traineeId,
          toMentorId: p.mentorId,
          text: 'Trainee conversation test message',
          rating: 4,
        }),
      });
      return { status: r.status, body: await r.json() };
    }, ids);

    expect(res.status).toBe(201);

    // Load conversation and verify trainee's own message appears
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      if (s?.loadFeedbackReceived) s.loadFeedbackReceived();
    });
    await page.waitForTimeout(800);

    const conversation = await getTraineeAlpineState(page, 'conversation');
    expect(Array.isArray(conversation)).toBe(true);
    const mine = (conversation as any[]).filter(f => f.text === 'Trainee conversation test message');
    expect(mine.length).toBeGreaterThan(0);
    expect(mine[0].fromTraineeId).toBe(ids.traineeId);
  });

  test('conversation shows both trainee and mentor messages', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return { traineeId: s?.traineeId ?? null, mentorId: s?.me?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    // Create one message from each side
    await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fromTraineeId: p.traineeId, toMentorId: p.mentorId, text: 'From trainee side' }),
      });
      await fetch('/api/v1/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fromMentorId: p.mentorId, toTraineeId: p.traineeId, text: 'From mentor side' }),
      });
    }, ids);

    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      if (s?.loadFeedbackReceived) s.loadFeedbackReceived();
    });
    await page.waitForTimeout(800);

    const conversation = await getTraineeAlpineState(page, 'conversation');
    const fromTrainee = (conversation as any[]).filter(f => f.fromTraineeId && f.text === 'From trainee side');
    const fromMentor  = (conversation as any[]).filter(f => f.fromMentorId  && f.text === 'From mentor side');
    expect(fromTrainee.length).toBeGreaterThan(0);
    expect(fromMentor.length).toBeGreaterThan(0);
  });

  test('conversation tab renders both directions in UI', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return { traineeId: s?.traineeId ?? null, mentorId: s?.me?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    // Seed both sides
    await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      await fetch('/api/v1/feedback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fromTraineeId: p.traineeId, toMentorId: p.mentorId, text: 'UI trainee msg', rating: 3 }),
      });
      await fetch('/api/v1/feedback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fromMentorId: p.mentorId, toTraineeId: p.traineeId, text: 'UI mentor msg', rating: 5 }),
      });
    }, ids);

    // Switch to feedback tab
    const feedbackTab = page.locator('button').filter({ hasText: '💬' }).first();
    await feedbackTab.click();
    await page.waitForTimeout(800);

    // Both messages should be in the DOM
    await expect(page.locator('text=UI trainee msg')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=UI mentor msg')).toBeVisible({ timeout: 5000 });
    // "Ви" label for trainee's own messages
    await expect(page.locator('text=Ви').first()).toBeVisible({ timeout: 3000 });
  });

  test('GET /api/v1/feedback/conversation returns both directions', async ({ page }) => {
    const token = await getTraineeToken(page);
    if (!token) { test.skip(); return; }
    await goToTraineePage(page, token);

    const ids = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return { traineeId: s?.traineeId ?? null, mentorId: s?.me?.mentorId ?? null };
    });
    if (!ids.traineeId || !ids.mentorId) { test.skip(); return; }

    const result = await page.evaluate(async (p: { traineeId: number; mentorId: number }) => {
      const r = await fetch(`/api/v1/feedback/conversation?mentorId=${p.mentorId}&traineeId=${p.traineeId}`);
      return { status: r.status, body: await r.json() };
    }, ids);

    expect(result.status).toBe(200);
    expect(Array.isArray(result.body)).toBe(true);
    // Every item is between these two parties
    for (const fb of result.body as any[]) {
      const valid =
        (fb.fromMentorId === ids.mentorId && fb.toTraineeId === ids.traineeId) ||
        (fb.fromTraineeId === ids.traineeId && fb.toMentorId === ids.mentorId);
      expect(valid).toBe(true);
    }
  });
});
