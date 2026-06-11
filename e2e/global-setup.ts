import { FullConfig, request } from '@playwright/test';

/**
 * Resets the demo coach's feature flags to a known-enabled state before every test run.
 *
 * Why: buildTraineeData() reads directly from the DB (no demo override), so tests that
 * open a fresh trainee page need shareAvailability / traineeComm / sessionConfirmations
 * to be true in the DB. These flags default to false in the V7 migration, but previous
 * test runs or manual interaction may have changed them. Starting from a fixed baseline
 * makes parallel workers (--workers=4) deterministic.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:8080';
  const req = await request.newContext({ baseURL });

  try {
    // 1. Demo login — sets google_sub="demo-seed" in the session
    await req.post('/demo-login');

    // 2. Call /api/v1/me — the handler reads google_sub, looks up the mentor, and
    //    stores mentor_id in the session (required by PUT /api/v1/mentor/profile).
    const meRes = await req.get('/api/v1/me');
    if (!meRes.ok()) {
      console.warn(`[global-setup] /api/v1/me returned ${meRes.status()} — skipping profile reset`);
      return;
    }

    // 3. Enable all feature flags that trainee-view tests depend on.
    const profileRes = await req.put('/api/v1/mentor/profile', {
      data: {
        shareAvailability: 'true',
        traineeComm: 'true',
        sessionConfirmations: 'true',
      },
    });

    if (!profileRes.ok()) {
      console.warn(`[global-setup] PUT /api/v1/mentor/profile returned ${profileRes.status()}`);
    }
  } finally {
    await req.dispose();
  }
}
