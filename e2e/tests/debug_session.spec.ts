import { test, expect } from './fixtures';

test('debug session creation error', async ({ page }) => {
  const ids = await page.evaluate(() => {
    const el = document.querySelector('[x-data]') as any;
    const state = el?._x_dataStack?.[0];
    return { traineeId: state?.trainees?.[0]?.id ?? null, mentorId: state?.mentorId ?? null };
  });
  console.log('IDs:', JSON.stringify(ids));
  
  const dateStr = new Date().toISOString().split('T')[0];
  const res = await page.evaluate(async (p: any) => {
    const r = await fetch('/api/v1/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Debug test', date: p.dateStr, time: '09:00', endTime: '10:00',
        traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true }),
    });
    let body;
    try { body = await r.json(); } catch(e: any) { body = { parseError: e.message }; }
    return { status: r.status, body };
  }, { ...ids, dateStr });
  
  console.log('Response status:', res.status);
  console.log('Response body:', JSON.stringify(res.body));
  expect(res.status).toBe(201);
});
