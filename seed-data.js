import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080';
// const BASE_URL = 'https://cozy-planner.duckdns.org';

const TRAINEE_NAMES = [
  'Олександр', 'Марія', 'Іван', 'Ольга', 'Андрій',
  'Наталія', 'Сергій', 'Катерина', 'Дмитро', 'Анна',
  'Михайло', 'Юлія', 'Тарас', 'Софія', 'Артем',
];

const SESSION_TITLES = [
  'Ранкове тренування',
  'Спарринг',
  'Техніка подачі',
  'Фізична підготовка',
  'Робота біля сітки',
  'Пересування кортом',
  'Групова гра',
  'Індивідуальне заняття',
  'Розминка',
  'Активне відновлення',
];

function fmtDate(d) {
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function sessionTimes(index) {
  const slots = [
    ['07:00', '08:00'],
    ['08:00', '09:00'],
    ['09:00', '10:00'],
    ['10:00', '11:00'],
    ['11:00', '12:00'],
    ['12:00', '13:00'],
    ['13:00', '14:00'],
    ['14:00', '15:00'],
    ['15:00', '16:00'],
    ['16:00', '17:00'],
  ];
  return slots[index % slots.length];
}

export const options = {
  scenarios: {
    seed: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<30000'],
  },
};

export default function () {
  console.log('=== SEED DATA SCRIPT START ===');

  // ── Login ──
  const loginRes = http.post(`${BASE_URL}/k6-login`, null, { redirects: 0 });
  if (loginRes.status !== 302) {
    console.log(`Login FAILED: ${loginRes.status}`);
    return;
  }
  const loc = loginRes.headers.Location || loginRes.headers.location;
  if (loc) http.get(`${BASE_URL}${loc}`);
  const meRes = http.get(`${BASE_URL}/api/v1/me`);
  if (meRes.status !== 200) {
    console.log(`/api/v1/me failed: ${meRes.status}`);
    return;
  }
  const me = JSON.parse(meRes.body);
  const mentorId = me.mentor?.id;
  console.log(`Logged in as mentorId=${mentorId}`);

  // ── Locations ──
  const locRes = http.get(`${BASE_URL}/api/v1/mentors/${mentorId}/locations`);
  let locationIds;
  if (locRes.status === 200) {
    const locs = JSON.parse(locRes.body);
    locationIds = locs.map(l => l.id);
    console.log(`Existing locations: ${locationIds}`);
  }
  if (!locationIds || locationIds.length === 0) {
    console.log('ERROR: No locations found. Create at least one location first.');
    return;
  }

  // ── Trainees (reuse existing or create) ──
  const trRes = http.get(`${BASE_URL}/api/v1/mentors/${mentorId}/trainees`);
  let traineeIds;
  if (trRes.status === 200) {
    const existing = JSON.parse(trRes.body);
    traineeIds = existing.map(t => t.id).slice(0, 5);
    console.log(`Using ${traineeIds.length} existing trainees: ${traineeIds}`);
  }
  if (!traineeIds || traineeIds.length === 0) {
    traineeIds = [];
    for (let i = 0; i < 5; i++) {
      const name = `SEED-${TRAINEE_NAMES[i]}`;
      const res = http.post(`${BASE_URL}/api/v1/trainees`,
        JSON.stringify({ name, description: 'seed-data', mentorId }),
        { headers: { 'Content-Type': 'application/json' } }
      );
      if (res.status === 201) {
        const id = JSON.parse(res.body).id;
        traineeIds.push(id);
        console.log(`Created trainee id=${id} name=${name}`);
        sleep(0.5);
      }
    }
    console.log(`Trainees ready: ${traineeIds}`);
  }

  // ── Build date range: past 14 days + next 7 days ──
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dates = [];
  for (let d = -14; d <= 6; d++) {
    const dt = new Date(today);
    dt.setDate(dt.getDate() + d);
    dates.push(dt);
  }
  console.log(`Date range: ${fmtDate(dates[0])} → ${fmtDate(dates[dates.length - 1])} (${dates.length} days)`);

  // ── Create 10 sessions per day ──
  let created = 0;
  let failed = 0;

  for (const date of dates) {
    for (let slot = 0; slot < 10; slot++) {
      const [time, endTime] = sessionTimes(slot);
      const body = {
        title: SESSION_TITLES[slot],
        description: 'seed-data',
        date: fmtDate(date),
        time,
        endTime,
        mentorId,
        locationId: locationIds[slot % locationIds.length],
        traineeIds: slot < traineeIds.length ? [traineeIds[slot]] : [],
      };

      const res = http.post(`${BASE_URL}/api/v1/sessions`,
        JSON.stringify(body),
        { headers: { 'Content-Type': 'application/json' } }
      );

      if (res.status === 201) {
        created++;
      } else {
        failed++;
        if (failed <= 5) {
          console.log(`FAILED: ${fmtDate(date)} ${time} status=${res.status}`);
        }
      }

      if ((created + failed) % 50 === 0) {
        console.log(`Progress: ${created} created, ${failed} failed`);
      }
    }
  }

  console.log(`=== DONE: ${created} sessions created, ${failed} failed ===`);
}
