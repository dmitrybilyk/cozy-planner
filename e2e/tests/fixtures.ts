import { test as base, expect, Page } from '@playwright/test';

export async function loginAsDemo(page: Page) {
  await page.goto('/signin');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/planner/, { timeout: 15000 });
  // Wait until Alpine.js finishes loading the app
  await page.waitForSelector('[data-tour="profile"]', { timeout: 15000 });
}

// Extends page fixture so every test using this gets an authenticated session
export const test = base.extend<{ page: Page }>({
  page: async ({ page }, use) => {
    await loginAsDemo(page);
    await use(page);
  },
});

export { expect };
