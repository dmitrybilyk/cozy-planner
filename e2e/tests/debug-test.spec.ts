import { test, expect } from './fixtures';
test('debug different times', async ({ page }) => {
  const ids = { traineeId: 5, mentorId: 1 };
  const today = new Date().toISOString().split('T')[0];
  
  // Try 07:00 (outside work hours)
  let res = await page.evaluate(async (p: any) => {
    const r = await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'T1', date: p.today, time: '07:00', endTime: '08:00', traineeIds: [p.traineeId], mentorId: p.mentorId }) });
    return { status: r.status, body: await r.text() };
  }, {...ids, today});
  console.log('07:00:', res.status, res.body.substring(0, 200));
  
  // Try 15:00 (clear time)
  res = await page.evaluate(async (p: any) => {
    const r = await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'T2', date: p.today, time: '15:00', endTime: '16:00', traineeIds: [p.traineeId], mentorId: p.mentorId, recurring: true }) });
    return { status: r.status, body: await r.text() };
  }, {...ids, today});
  console.log('15:00 recurring:', res.status, res.body.substring(0, 200));
  
  // Cleanup
  if (res.status === 201) {
    const body = JSON.parse(res.body);
    await page.evaluate(async (p: any) => {
      const list = await fetch(`/api/v1/sessions?mentorId=${p.mid}&startDate=${p.today}&endDate=2027-01-01`);
      const sessions = await list.json();
      const toDelete = sessions.filter((s: any) => s.recurrenceGroupId === p.rgid);
      await Promise.all(toDelete.map((s: any) => fetch(`/api/v1/sessions/${s.id}`, { method: 'DELETE' })));
    }, { mid: 1, today, rgid: body.recurrenceGroupId });
    console.log('cleaned up');
  }
});
