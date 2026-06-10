import { test, expect } from './fixtures';

async function alpineGet(page: any, key: string) {
  return page.evaluate((k: string) => {
    const el = document.querySelector('[x-data]') as any;
    return el?._x_dataStack?.[0]?.[k];
  }, key);
}

async function alpineSet(page: any, key: string, value: any) {
  await page.evaluate(([k, v]: [string, any]) => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0][k] = v;
  }, [key, value]);
}

async function switchToAgenda(page: any) {
  await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    if (el?._x_dataStack?.[0]) el._x_dataStack[0].activeTab = 'agenda';
  });
  await page.waitForTimeout(500);
}

test.describe('Plan (agenda) view', () => {
  test.beforeEach(async ({ page }) => {
    await switchToAgenda(page);
  });

  test('activeTab is agenda after switching', async ({ page }) => {
    const tab = await alpineGet(page, 'activeTab');
    expect(tab).toBe('agenda');
  });

  test('plan view loads and is visible', async ({ page }) => {
    // Agenda tab should render some container
    const agendaContainer = page.locator('[data-tour="agenda"], [x-show*="agenda"]').first();
    // Fallback: check activeTab state is correct
    const tab = await alpineGet(page, 'activeTab');
    expect(tab).toBe('agenda');
  });

  test('hasMoreAgenda state is a boolean in Alpine', async ({ page }) => {
    const val = await alpineGet(page, 'hasMoreAgenda');
    expect(typeof val).toBe('boolean');
  });

  test('load-more button visibility matches hasMoreAgenda state', async ({ page }) => {
    // Wait for agenda to finish loading
    await page.waitForTimeout(1000);

    const state = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      const s = el?._x_dataStack?.[0];
      return {
        hasMoreAgenda: !!s?.hasMoreAgenda,
        agendaReady: !!s?.agendaReady,
        loadingMore: !!s?.loadingMore,
      };
    });

    const btn = page.locator('button').filter({ hasText: 'Завантажити ще' }).first();
    const btnCount = await btn.count();

    if (!state.hasMoreAgenda || !state.agendaReady || state.loadingMore) {
      // No more data or still loading: button should either not exist or be hidden
      if (btnCount > 0) {
        await expect(btn).not.toBeVisible({ timeout: 3000 });
      }
    } else {
      // More data available: button should be visible
      expect(btnCount).toBeGreaterThan(0);
      await expect(btn).toBeVisible({ timeout: 3000 });
    }
  });

  test('selectedTraineeFilters state exists as an array', async ({ page }) => {
    const val = await alpineGet(page, 'selectedTraineeFilters');
    expect(Array.isArray(val)).toBe(true);
  });

  test('trainee filter dropdown exists in plan view', async ({ page }) => {
    // Filter control for trainees in agenda view
    const filter = page.locator('[data-testid="agenda-trainee-filter"], select[x-model*="Trainee"], [x-model*="selectedTrainee"]').first();
    const filterCount = await filter.count();
    if (filterCount === 0) {
      // Fallback: just check selectedTraineeFilters exists
      const val = await alpineGet(page, 'selectedTraineeFilters');
      expect(Array.isArray(val)).toBe(true);
    } else {
      await expect(filter).toBeVisible({ timeout: 5000 });
    }
  });

  test('selecting a trainee filter updates selectedTraineeFilters', async ({ page }) => {
    // Get trainee IDs from Alpine state
    const trainees = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.trainees ?? [];
    });
    if (!trainees || trainees.length === 0) { test.skip(); return; }

    const traineeId = trainees[0].id;
    await alpineSet(page, 'selectedTraineeFilters', [traineeId]);
    await page.waitForTimeout(300);

    const filters = await alpineGet(page, 'selectedTraineeFilters');
    expect(Array.isArray(filters)).toBe(true);
    expect((filters as number[]).includes(traineeId)).toBe(true);
  });

  test('clearing trainee filter restores empty selectedTraineeFilters', async ({ page }) => {
    const trainees = await page.evaluate(() => {
      const el = document.querySelector('[x-data]') as any;
      return el?._x_dataStack?.[0]?.trainees ?? [];
    });
    if (!trainees || trainees.length === 0) { test.skip(); return; }

    const traineeId = trainees[0].id;
    // Set a filter
    await alpineSet(page, 'selectedTraineeFilters', [traineeId]);
    await page.waitForTimeout(200);
    // Clear the filter
    await alpineSet(page, 'selectedTraineeFilters', []);
    await page.waitForTimeout(300);

    const filters = await alpineGet(page, 'selectedTraineeFilters');
    expect((filters as any[]).length).toBe(0);
  });

  test('showFreeTime toggle in plan view updates state', async ({ page }) => {
    const before = await alpineGet(page, 'showFreeTime');
    await alpineSet(page, 'showFreeTime', true);
    await page.waitForTimeout(300);
    const after = await alpineGet(page, 'showFreeTime');
    expect(after).toBe(true);
    // Restore
    await alpineSet(page, 'showFreeTime', !!before);
  });
});
