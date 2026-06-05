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
