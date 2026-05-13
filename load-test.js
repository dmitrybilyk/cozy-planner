import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Custom metrics for load analysis
const traineeCreateTime = new Trend('trainee_create_time');
const sessionCreateTime = new Trend('session_create_time');
const sessionEditTime = new Trend('session_edit_time');
const sessionDeleteTime = new Trend('session_delete_time');
const traineeDeleteTime = new Trend('trainee_delete_time');
const errorRate = new Rate('request_errors');

export const options = {
  scenarios: {
    continuous: {
      executor: 'constant-vus',
      vus: 5,
      duration: '24h',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<8000'],
    request_errors: ['rate<0.15'],
  },
  noConnectionReuse: true,
};

const BASE_URL = 'https://cozy-planner.duckdns.org';

const TRAINEE_NAMES = [
  'LT-Олександр', 'LT-Марія', 'LT-Іван', 'LT-Ольга', 'LT-Андрій',
  'LT-Наталія', 'LT-Сергій', 'LT-Катерина', 'LT-Дмитро', 'LT-Анна',
  'LT-Михайло', 'LT-Юлія', 'LT-Тарас', 'LT-Софія', 'LT-Артем',
  'LT-Вікторія', 'LT-Максим', 'LT-Ірина', 'LT-Павло', 'LT-Олена',
];

const SESSION_TITLES = [
  'Ранкове тренування', 'Спарринг', 'Техніка подачі',
  'Фізична підготовка', 'Робота біля сітки', 'Пересування кортом',
  'Групова гра', 'Індивідуальне заняття', 'Розминка',
  'Активне відновлення',
];

function randItem(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

function fmtDate(d) {
  return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
}

function randDate(ahead) {
  const d = new Date(); d.setDate(d.getDate() + Math.floor(Math.random() * ahead));
  return fmtDate(d);
}

function randTime() {
  const h = 7 + Math.floor(Math.random() * 11);
  return `${h.toString().padStart(2,'0')}:${Math.random()<0.5?'00':'30'}`;
}

function randEnd(t) {
  const [h, m] = t.split(':').map(Number);
  const total = h*60 + m + 60 + Math.floor(Math.random()*3)*30;
  const eh = Math.floor(total/60)%24;
  return `${eh.toString().padStart(2,'0')}:${total%60===0?'00':'30'}`;
}

// ── AUTH via demo-login ─────────────────────────────────────────────────────
export function setup() {
  // POST to /k6-login — k6 stores the session cookie automatically
  const loginRes = http.post(`${BASE_URL}/k6-login`);

  if (loginRes.status !== 302 && loginRes.status !== 200) {
    console.error(`Login failed: ${loginRes.status}`);
    return { sessionOk: false, coachId: -1, locationIds: [1, 2] };
  }

  // Now fetch /api/v1/me to get coachId
  const meRes = http.get(`${BASE_URL}/api/v1/me`);
  let coachId = -1;
  let locationIds = [1, 2];

  if (meRes.status === 200) {
    const me = JSON.parse(meRes.body);
    coachId = me.coach?.id || me.mentor?.id || -1;
    console.log(`Mentor ID: ${coachId}`);

    const locRes = http.get(`${BASE_URL}/api/v1/coaches/${coachId}/locations`);
    if (locRes.status === 200) {
      const locs = JSON.parse(locRes.body);
      if (locs.length > 0) locationIds = locs.map(l => l.id);
    }

    return { sessionOk: true, coachId, locationIds };
  }

  return { sessionOk: false, coachId: -1, locationIds: [1, 2] };
}

// ── MAIN ────────────────────────────────────────────────────────────────────
export default function (data) {
  if (!data.sessionOk) {
    sleep(3);
    return;
  }

  const { coachId, locationIds } = data;
  const ids = { trainees: [], workouts: [] };

  // Use a fresh cookie jar per VU to simulate distinct users
  // (but share the session from setup)
  const jar = http.cookieJar();
  // k6 automatically persists cookies from the setup() response,
  // so subsequent calls should carry the session cookie.

  group('1. Fetch existing data', () => {
    const r1 = http.get(`${BASE_URL}/api/v1/coaches/${coachId}/athletes`);
    check(r1, { 'GET athletes ok': r => r.status === 200 });
    errorRate.add(r1.status !== 200);
    sleep(0.2);

    const today = new Date();
    const start = new Date(today); start.setDate(today.getDate() - 3);
    const end   = new Date(today); end.setDate(today.getDate() + 14);
    const r2 = http.get(`${BASE_URL}/api/v1/workouts?coachId=${coachId}&startDate=${fmtDate(start)}&endDate=${fmtDate(end)}`);
    check(r2, { 'GET workouts ok': r => r.status === 200 });
    errorRate.add(r2.status !== 200);
    sleep(0.3);
  });

  group('2. Create trainees', () => {
    for (let i = 0; i < 3; i++) {
      const name = `${randItem(TRAINEE_NAMES)}-vu${__VU}-it${__ITER}-${i}`;
      const start = Date.now();
      const res = http.post(`${BASE_URL}/api/v1/athletes`,
        JSON.stringify({ name, description: 'load-test', coachId }),
        { headers: { 'Content-Type': 'application/json' } }
      );
      traineeCreateTime.add(Date.now() - start);
      const ok = check(res, { 'trainee created': r => r.status === 201 });
      errorRate.add(!ok);
      if (ok) ids.trainees.push(JSON.parse(res.body).id);
      sleep(0.2 + Math.random() * 0.3);
    }
  });

  group('3. Edit first trainee', () => {
    if (ids.trainees.length > 0) {
      const id = ids.trainees[0];
      const res = http.put(`${BASE_URL}/api/v1/athletes/${id}`,
        JSON.stringify({ name: `${randItem(TRAINEE_NAMES)}-EDITED-${__VU}`, coachId }),
        { headers: { 'Content-Type': 'application/json' } }
      );
      check(res, { 'trainee edited': r => r.status === 200 });
      errorRate.add(res.status !== 200);
    }
    sleep(0.3);
  });

  group('4. Create sessions', () => {
    for (let i = 0; i < 4; i++) {
      const t = randTime();
      const aIds = ids.trainees.length > 0
        ? ids.trainees.slice(0, Math.min(ids.trainees.length, 2))
        : [];
      const body = {
        title: randItem(SESSION_TITLES),
        description: 'load-test',
        date: randDate(14),
        time: t,
        endTime: randEnd(t),
        coachId,
        locationId: randItem(locationIds),
        athleteIds: aIds,
      };
      const start = Date.now();
      const res = http.post(`${BASE_URL}/api/v1/workouts`,
        JSON.stringify(body),
        { headers: { 'Content-Type': 'application/json' } }
      );
      sessionCreateTime.add(Date.now() - start);
      const ok = check(res, { 'session created': r => r.status === 201 });
      errorRate.add(!ok);
      if (ok) ids.workouts.push(JSON.parse(res.body).id);
      sleep(0.2 + Math.random() * 0.3);
    }
  });

  group('5. Edit sessions', () => {
    for (const wid of ids.workouts) {
      const t = randTime();
      const body = {
        id: wid,
        title: `${randItem(SESSION_TITLES)} (edited)`,
        description: 'load-test-edited',
        date: randDate(14),
        time: t,
        endTime: randEnd(t),
        coachId,
        locationId: randItem(locationIds),
        athleteIds: [],
      };
      const start = Date.now();
      const res = http.post(`${BASE_URL}/api/v1/workouts`,
        JSON.stringify(body),
        { headers: { 'Content-Type': 'application/json' } }
      );
      sessionEditTime.add(Date.now() - start);
      check(res, { 'session edited': r => r.status === 201 });
      errorRate.add(res.status !== 201);
      sleep(0.2 + Math.random() * 0.2);
    }
  });

  group('6. Delete sessions', () => {
    for (const wid of ids.workouts) {
      const start = Date.now();
      const res = http.del(`${BASE_URL}/api/v1/workouts/${wid}`);
      sessionDeleteTime.add(Date.now() - start);
      check(res, { 'session deleted': r => r.status === 204 });
      errorRate.add(res.status !== 204);
      sleep(0.1 + Math.random() * 0.2);
    }
  });

  group('7. Delete trainees', () => {
    for (const tid of ids.trainees) {
      const start = Date.now();
      const res = http.del(`${BASE_URL}/api/v1/athletes/${tid}`);
      traineeDeleteTime.add(Date.now() - start);
      check(res, { 'trainee deleted': r => r.status === 204 });
      errorRate.add(res.status !== 204);
      sleep(0.1 + Math.random() * 0.2);
    }
  });

  sleep(1);
}

export function teardown(data) {
  console.log(`Load test completed. VUs: ${__VU}`);
}
