import { test, expect } from './fixtures';

async function startTourViaAlpine(page: any) {
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el && el._x_dataStack) {
      const data = el._x_dataStack[0];
      if (typeof data.startTour === 'function') data.startTour();
    }
  });
}

test.describe('Product tour', () => {
  test('tour overlay appears after starting the tour', async ({ page }) => {
    await startTourViaAlpine(page);
    await page.waitForTimeout(1500);
    // Step counter "1 / N" is visible in the tooltip
    await expect(page.locator('span').filter({ hasText: /^1 \// }).first()).toBeVisible({ timeout: 5000 });
  });

  test('demo data is created when tour starts via API', async ({ page }) => {
    // Use browser-level fetch to ensure same session as the logged-in page
    const result = await page.evaluate(async () => {
      const res = await fetch('/api/v1/tour/demo', { method: 'POST' });
      if (!res.ok) return { ok: false, status: res.status };
      const body = await res.json();
      return { ok: true, status: res.status, sessionId: body.sessionId };
    });
    expect(result.ok).toBeTruthy();
    expect(typeof result.sessionId).toBe('number');
    // Cleanup
    await page.evaluate(async () => {
      await fetch('/api/v1/tour/demo', { method: 'DELETE' });
    });
  });

  test('demo session "Демо" appears on today after tour start', async ({ page }) => {
    await startTourViaAlpine(page);
    await page.waitForTimeout(2500);
    // Switch to detail mode so session titles are visible (compact view hides them)
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) el._x_dataStack[0].compactView = false;
    });
    await page.waitForTimeout(300);
    // Demo session title is "Демо" — look for it in the feed
    await expect(page.locator('text=Демо').first()).toBeVisible({ timeout: 10000 });
  });

  test('demo data is deleted when tour ends', async ({ page }) => {
    const { sessionId } = await page.evaluate(async () => {
      const res = await fetch('/api/v1/tour/demo', { method: 'POST' });
      const body = await res.json();
      return { sessionId: body.sessionId };
    });

    await page.evaluate(async () => {
      await fetch('/api/v1/tour/demo', { method: 'DELETE' });
    });

    const sessions: any[] = await page.evaluate(async () => {
      const today = new Date().toISOString().slice(0, 10);
      const res = await fetch(`/api/v1/coach/sessions?startDate=${today}&endDate=${today}`);
      return await res.json();
    });
    const demoSession = sessions.find((s: any) => s.id === sessionId);
    expect(demoSession).toBeUndefined();
  });

  test('tour navigation — next step advances tourStep', async ({ page }) => {
    await startTourViaAlpine(page);
    await page.waitForTimeout(1500);

    const stepBefore = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) return el._x_dataStack[0].tourStep ?? -1;
      return -1;
    });

    const nextBtn = page.locator('button:has-text("Далі")').last();
    if (await nextBtn.isVisible()) {
      await nextBtn.click();
      await page.waitForTimeout(400);
    }

    const stepAfter = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) return el._x_dataStack[0].tourStep ?? -1;
      return -1;
    });

    if (stepBefore >= 0 && stepAfter >= 0) {
      expect(stepAfter).toBeGreaterThan(stepBefore);
    }
  });

  test('tour can be dismissed with skip button', async ({ page }) => {
    await startTourViaAlpine(page);
    await page.waitForTimeout(1500);

    const skipBtn = page.locator('button:has-text("Пропустити")').last();
    if (await skipBtn.isVisible()) {
      await skipBtn.click();
      await page.waitForTimeout(600);
    }

    const tourVisible = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) return el._x_dataStack[0].showTour ?? false;
      return false;
    });
    expect(tourVisible).toBe(false);
  });

  test('creating tour demo twice cleans up previous data', async ({ page }) => {
    const { sessionId: id1 } = await page.evaluate(async () => {
      const res = await fetch('/api/v1/tour/demo', { method: 'POST' });
      const body = await res.json();
      return { sessionId: body.sessionId };
    });

    const { sessionId: id2 } = await page.evaluate(async () => {
      const res = await fetch('/api/v1/tour/demo', { method: 'POST' });
      const body = await res.json();
      return { sessionId: body.sessionId };
    });

    expect(id2).not.toBe(id1);

    // Cleanup
    await page.evaluate(async () => {
      await fetch('/api/v1/tour/demo', { method: 'DELETE' });
    });
  });

  test('tour demo locations appear in locations panel', async ({ page }) => {
    await startTourViaAlpine(page);
    await page.waitForTimeout(2500);

    // Dismiss tour overlay so clicks reach the page
    await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      if (el && el._x_dataStack) el._x_dataStack[0].showTour = false;
    });
    await page.waitForTimeout(300);

    // Navigate to locations tab
    await page.locator('[data-tour="locations-btn"]').click();
    await expect(page.locator('[data-tour="locations-list"]')).toBeVisible({ timeout: 5000 });
    // Demo creates "Зал А" and "Зал Б" locations
    const list = page.locator('[data-tour="locations-list"]');
    await expect(list.locator('p').filter({ hasText: 'Зал А' }).first()).toBeVisible({ timeout: 8000 });
    await expect(list.locator('p').filter({ hasText: 'Зал Б' }).first()).toBeVisible({ timeout: 5000 });
  });
});

// ─── Intro slides ──────────────────────────────────────────────────────────

test.describe('Intro slides', () => {
  test('introSlides has exactly 4 slides', async ({ page }) => {
    const count = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.introSlides?.length ?? 0;
    });
    expect(count).toBe(4);
  });

  test('first slide title is Ласкаво просимо до Cozy Planner', async ({ page }) => {
    const title = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.introSlides?.[0]?.title ?? '';
    });
    expect(title).toBe('Ласкаво просимо до Cozy Planner');
  });

  test('second slide title contains Жодних накладок', async ({ page }) => {
    const title = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.introSlides?.[1]?.title ?? '';
    });
    expect(title).toContain('Жодних накладок');
  });

  test('third slide has cta tg and title contains Telegram', async ({ page }) => {
    const slide = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0]?.introSlides?.[2];
      return { cta: s?.cta, title: s?.title ?? '' };
    });
    expect(slide.cta).toBe('tg');
    expect(slide.title).toContain('Telegram');
  });

  test('last slide title contains тур', async ({ page }) => {
    const slide = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const slides = el?._x_dataStack?.[0]?.introSlides ?? [];
      return slides[slides.length - 1]?.title ?? '';
    });
    expect(slide).toMatch(/тур/i);
  });

  test('first slide body mentions планування та доступність', async ({ page }) => {
    const body = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.introSlides?.[0]?.body ?? '';
    });
    expect(body).toMatch(/доступність/i);
    expect(body).toMatch(/розклади/i);
  });
});

// ─── Tour step structure (revised) ────────────────────────────────────────

test.describe('Tour step structure — revised', () => {
  test('first tour step targets profile', async ({ page }) => {
    const target = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.tourSteps?.[0]?.target ?? '';
    });
    expect(target).toBe('[data-tour="profile"]');
  });

  test('second tour step targets view-toggle (Plan)', async ({ page }) => {
    const step = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0]?.tourSteps?.[1];
      return { target: s?.target ?? '', title: s?.title ?? '' };
    });
    expect(step.target).toBe('[data-tour="view-toggle"]');
    expect(step.title).toBe('План');
  });

  test('third tour step targets feed-view (Day)', async ({ page }) => {
    const step = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0]?.tourSteps?.[2];
      return { target: s?.target ?? '', title: s?.title ?? '' };
    });
    expect(step.target).toBe('[data-tour="feed-view"]');
    expect(step.title).toBe('День');
  });

  test('filter step body says по локації', async ({ page }) => {
    const steps = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? []).map((s: any) => s.body);
    });
    const filterBody = steps.find((b: string) => /фільтруй/i.test(b)) ?? '';
    expect(filterBody).toMatch(/по локації/i);
    expect(filterBody).not.toMatch(/кабінет/i);
  });

  test('GCal step body starts with capital letter', async ({ page }) => {
    const steps = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? []).map((s: any) => ({ title: s.title, body: s.body }));
    });
    const gcal = steps.find((s: any) => /google calendar/i.test(s.title));
    expect(gcal).toBeTruthy();
    expect(gcal.body.charAt(0)).toBe(gcal.body.charAt(0).toUpperCase());
    expect(gcal.body.charAt(0)).not.toBe(gcal.body.charAt(0).toLowerCase());
  });

  test('avail-intervals step title is Інтервали доступності', async ({ page }) => {
    const steps = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? []).map((s: any) => s.title);
    });
    expect(steps).toContain('Інтервали доступності');
    expect(steps.every((t: string) => !t.includes('Інтервали по'))).toBe(true);
  });

  test('trainees step switches to trainees tab', async ({ page }) => {
    const step = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? [])
        .find((s: any) => s.target === '[data-tour="trainees"]');
    });
    expect(step?.tab).toBe('trainees');
    expect(step?.detailTrainees).toBe(true);
  });

  test('locations-btn step has tab locations', async ({ page }) => {
    const step = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return (el?._x_dataStack?.[0]?.tourSteps ?? [])
        .find((s: any) => s.target === '[data-tour="locations-btn"]');
    });
    expect(step?.tab).toBe('locations');
  });
});
