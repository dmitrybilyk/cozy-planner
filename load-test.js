import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const traineeCreateTime = new Trend('trainee_create_time');
const sessionCreateTime = new Trend('session_create_time');
const sessionDeleteTime = new Trend('session_delete_time');
const traineeDeleteTime = new Trend('trainee_delete_time');
const errorRate = new Rate('request_errors');

export const options = {
  scenarios: {
    continuous: {
      executor: 'constant-vus',
      vus: 3,
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

let ITER = 0;

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

function log(msg) {
  const now = new Date().toISOString().slice(11, 19);
  console.log(`[${now}][VU=${__VU}] ${msg}`);
}

// ── Auth state per VU ──
const vuState = { loggedIn: false };

// Re-login: fresh POST + full setup.
// Returns true if successful, false otherwise.
function relogin() {
  const loginRes = http.post(`${BASE_URL}/k6-login`, null, { redirects: 0 });
  if (loginRes.status !== 302) {
    log(`Relogin FAILED: ${loginRes.status}`);
    return false;
  }

  const loc = loginRes.headers.Location || loginRes.headers.location;
  if (loc) http.get(`${BASE_URL}${loc}`);

  const meRes = http.get(`${BASE_URL}/api/v1/me`);
  if (meRes.status !== 200) {
    log(`Relogin: /api/v1/me failed: ${meRes.status}`);
    return false;
  }

  const me = JSON.parse(meRes.body);
  const coachId = me.coach?.id || me.mentor?.id || -1;
  log(`Relogin OK, Mentor ID: ${coachId}`);

  let locationIds = [1, 2];
  const locRes = http.get(`${BASE_URL}/api/v1/coaches/${coachId}/locations`);
  if (locRes.status === 200) {
    const locs = JSON.parse(locRes.body);
    if (locs.length > 0) locationIds = locs.map(l => l.id);
  }

  vuState.loggedIn = true;
  vuState.coachId = coachId;
  vuState.locationIds = locationIds;
  return true;
}

// Do a GET, and if it returns 302 (session expired), re-login and retry once.
function authedGet(url) {
  let res = http.get(url, { redirects: 0 });
  if (res.status === 302) {
    log(`Session expired on GET ${url}, re-logging in`);
    if (relogin()) {
      res = http.get(url, { redirects: 0 });
    }
  }
  return res;
}

// Do a POST, same re-login-on-302 logic.
function authedPost(url, body, params) {
  const fullParams = { ...params, redirects: 0 };
  let res = http.post(url, body, fullParams);
  if (res.status === 302) {
    log(`Session expired on POST ${url}, re-logging in`);
    if (relogin()) {
      res = http.post(url, body, fullParams);
    }
  }
  return res;
}

// Do a PUT, same re-login-on-302 logic.
function authedPut(url, body, params) {
  const fullParams = { ...params, redirects: 0 };
  let res = http.put(url, body, fullParams);
  if (res.status === 302) {
    log(`Session expired on PUT ${url}, re-logging in`);
    if (relogin()) {
      res = http.put(url, body, fullParams);
    }
  }
  return res;
}

// Do a DELETE, same re-login-on-302 logic.
function authedDel(url, params) {
  const fullParams = { ...params, redirects: 0 };
  let res = http.del(url, null, fullParams);
  if (res.status === 302) {
    log(`Session expired on DEL ${url}, re-logging in`);
    if (relogin()) {
      res = http.del(url, null, fullParams);
    }
  }
  return res;
}

// ── Entry point ──

export function setup() {
  log('Setup complete');
  return {};
}

export default function (data) {
  // First-time login
  if (!vuState.loggedIn) {
    if (!relogin()) {
      log('Cannot log in, sleeping 10s');
      sleep(10);
      return;
    }
  }

  ITER++;
  const iterLabel = `ITER=${ITER}`;
  const coachId = vuState.coachId;
  const locationIds = vuState.locationIds;
  const ids = { trainees: [], workouts: [] };

  log(`=== ${iterLabel} START === coachId=${coachId}`);

  group('1. Fetch existing', () => {
    const r1 = authedGet(`${BASE_URL}/api/v1/coaches/${coachId}/athletes`);
    const ok1 = check(r1, { 'GET athletes ok': r => r.status === 200 });
    errorRate.add(r1.status !== 200);
    if (ok1) {
      const athletes = JSON.parse(r1.body);
      log(`Existing athletes: ${athletes.length}`);
    } else {
      log(`FAILED to fetch athletes: ${r1.status}`);
    }
    sleep(0.5);

    const today = new Date();
    const start = new Date(today); start.setDate(today.getDate() - 3);
    const end   = new Date(today); end.setDate(today.getDate() + 14);
    const r2 = authedGet(`${BASE_URL}/api/v1/workouts?coachId=${coachId}&startDate=${fmtDate(start)}&endDate=${fmtDate(end)}`);
    const ok2 = check(r2, { 'GET workouts ok': r => r.status === 200 });
    errorRate.add(r2.status !== 200);
    if (ok2) {
      const workouts = JSON.parse(r2.body);
      log(`Existing workouts: ${workouts.length}`);
    } else {
      log(`FAILED to fetch workouts: ${r2.status}`);
    }
    sleep(0.5);
  });

  group('2. Create trainees', () => {
    for (let i = 0; i < 3; i++) {
      const name = `${randItem(TRAINEE_NAMES)}-vu${__VU}-it${ITER}-${i}`;
      const start = Date.now();
      const res = authedPost(`${BASE_URL}/api/v1/athletes`,
        JSON.stringify({ name, description: 'load-test', coachId }),
        { headers: { 'Content-Type': 'application/json' } }
      );
      traineeCreateTime.add(Date.now() - start);
      const ok = check(res, { 'trainee created': r => r.status === 201 });
      errorRate.add(!ok);
      if (ok) {
        const id = JSON.parse(res.body).id;
        ids.trainees.push(id);
        log(`Created trainee: id=${id} name="${name}"`);
      } else {
        log(`FAILED to create trainee: ${res.status} ${res.body.slice(0, 200)}`);
      }
      sleep(0.5);
    }
    log(`Trainees created: ${ids.trainees.length}`);
  });

  group('3. Edit first trainee', () => {
    if (ids.trainees.length > 0) {
      const id = ids.trainees[0];
      const newName = `${randItem(TRAINEE_NAMES)}-EDITED-${__VU}`;
      const res = authedPut(`${BASE_URL}/api/v1/athletes/${id}`,
        JSON.stringify({ name: newName, coachId }),
        { headers: { 'Content-Type': 'application/json' } }
      );
      const ok = check(res, { 'trainee edited': r => r.status === 200 });
      errorRate.add(res.status !== 200);
      if (ok) log(`Edited trainee: id=${id} newName="${newName}"`);
      else log(`FAILED to edit trainee: ${res.status}`);
    }
    sleep(1);
  });

  group('4. Create sessions', () => {
    for (let i = 0; i < 4; i++) {
      const t = randTime();
      const aIds = ids.trainees.length > 0
        ? ids.trainees.slice(0, Math.min(ids.trainees.length, 2))
        : [];
      const title = randItem(SESSION_TITLES);
      const body = {
        title,
        description: 'load-test',
        date: randDate(14),
        time: t,
        endTime: randEnd(t),
        coachId,
        locationId: randItem(locationIds),
        athleteIds: aIds,
      };
      const start = Date.now();
      const res = authedPost(`${BASE_URL}/api/v1/workouts`,
        JSON.stringify(body),
        { headers: { 'Content-Type': 'application/json' } }
      );
      sessionCreateTime.add(Date.now() - start);
      const ok = check(res, { 'session created': r => r.status === 201 });
      errorRate.add(!ok);
      if (ok) {
        const id = JSON.parse(res.body).id;
        ids.workouts.push(id);
        log(`Created session: id=${id} title="${title}" ${body.date} ${body.time}-${body.endTime}`);
      } else {
        log(`FAILED to create session: ${res.status} ${res.body.slice(0, 200)}`);
      }
      sleep(0.5);
    }
    log(`Sessions created: ${ids.workouts.length} — visible on planner page now!`);
  });

  if (ids.workouts.length > 0) {
    log(`Holding ${ids.workouts.length} sessions for 8s — watch the planner page!`);
    sleep(8);
  }

  group('5. Delete sessions', () => {
    for (const wid of ids.workouts) {
      const start = Date.now();
      const res = authedDel(`${BASE_URL}/api/v1/workouts/${wid}`);
      sessionDeleteTime.add(Date.now() - start);
      const ok = check(res, { 'session deleted': r => r.status === 204 });
      errorRate.add(res.status !== 204);
      if (ok) log(`Deleted session: id=${wid}`);
      else log(`FAILED to delete session: ${res.status}`);
      sleep(0.3);
    }
    if (ids.workouts.length > 0) log(`All ${ids.workouts.length} sessions deleted`);
  });

  group('6. Delete trainees', () => {
    for (const tid of ids.trainees) {
      const start = Date.now();
      const res = authedDel(`${BASE_URL}/api/v1/athletes/${tid}`);
      traineeDeleteTime.add(Date.now() - start);
      const ok = check(res, { 'trainee deleted': r => r.status === 204 });
      errorRate.add(res.status !== 204);
      if (ok) log(`Deleted trainee: id=${tid}`);
      else log(`FAILED to delete trainee: ${res.status}`);
      sleep(0.3);
    }
    if (ids.trainees.length > 0) log(`All ${ids.trainees.length} trainees deleted`);
  });

  log(`=== ${iterLabel} END ===`);
  sleep(2);
}
