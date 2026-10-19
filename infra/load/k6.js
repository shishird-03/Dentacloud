// Load test (PRD 13, 17): 500 concurrent users over the top endpoints.
// Targets: p50 < 80 ms, p95 < 300 ms, p99 < 800 ms, errors < 1 %, patient search p95 < 200 ms
// with 100,000 patients in one clinic. Run through infra/load/run.sh, which seeds that clinic first.
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE = (__ENV.BASE_URL || 'http://host.docker.internal:8095') + '/api/v1';
const PASSWORD = 'password123';
const NAMES = ['Asha', 'Bala', 'Chitra', 'Deepak', 'Meera', 'Ravi', 'Sana', 'Tara', 'Iyer', 'Reddy', 'Khan', 'Nair'];

export const options = {
  scenarios: {
    staff: {
      executor: 'ramping-vus',
      exec: 'staff',
      stages: [
        { duration: '2m', target: 500 },
        { duration: '5m', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
    // Password hashing is deliberately slow, so logins run at a realistic trickle, not in the mix.
    logins: { executor: 'constant-arrival-rate', exec: 'login', rate: 1, timeUnit: '1s', duration: '7m', preAllocatedVUs: 5 },
  },
  thresholds: {
    'http_req_duration{scenario:staff}': ['p(50)<80', 'p(95)<300', 'p(99)<800'],
    'http_req_duration{name:search}': ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function headers(token, extra) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  return { headers: Object.assign(h, extra || {}) };
}

function post(path, body, token, name, extra) {
  return http.post(BASE + path, JSON.stringify(body), Object.assign(headers(token, extra), { tags: { name } }));
}

function get(path, token, name) {
  return http.get(BASE + path, Object.assign(headers(token), { tags: { name } }));
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function signup(name) {
  const email = `load+${uuid()}@test.clinic`;
  const r = post('/auth/signup', { clinicName: name, ownerName: `Dr ${name}`, email, password: PASSWORD }, null, 'signup');
  check(r, { 'signup 201': (x) => x.status === 201 || x.status === 200 });
  return Object.assign(r.json(), { email });
}

export function setup() {
  const clinics = [];
  // The 100k-patient clinic, created and seeded by run.sh; log in fresh so the token outlives setup.
  const big = http.post(BASE + '/auth/login', JSON.stringify({ email: __ENV.BIG_EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  if (big.status !== 200) throw new Error(`big clinic login failed: ${big.status}`);
  clinics.push(Object.assign(big.json(), { email: __ENV.BIG_EMAIL }));
  for (let i = 1; i < 10; i++) clinics.push(signup(`Load Dental ${i}`));

  return clinics.map((c) => {
    const t = c.accessToken;
    const patients = [];
    for (let i = 0; i < 20; i++) {
      const phone = `98${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
      const p = post('/patients', { fullName: `${NAMES[i % NAMES.length]} Load ${i}`, phone, allergies: 'none' }, t, 'patient_create');
      patients.push({ id: p.json('id'), phone });
    }
    return { token: t, email: c.email, dentist: c.memberId, patients };
  });
}

export function staff(clinics) {
  const c = clinics[exec.vu.idInTest % clinics.length];
  const p = c.patients[Math.floor(Math.random() * c.patients.length)];
  const roll = Math.random();
  let r;
  if (roll < 0.30) {
    r = get(`/patients?q=${NAMES[Math.floor(Math.random() * NAMES.length)].toLowerCase()}&limit=20`, c.token, 'search');
  } else if (roll < 0.40) {
    r = get(`/patients?q=${p.phone}`, c.token, 'search');
  } else if (roll < 0.55) {
    r = get(`/patients/${p.id}`, c.token, 'patient_get');
  } else if (roll < 0.65) {
    const now = Date.now();
    r = get(`/appointments?from=${new Date(now - 7 * 864e5).toISOString()}&to=${new Date(now + 7 * 864e5).toISOString()}`,
      c.token, 'appointments_list');
  } else if (roll < 0.70) {
    // A unique 30-minute slot per VU and iteration, far in the future: no collisions, so a 409 is a real bug.
    const slot = Date.UTC(2031, 0, 1) + (exec.vu.idInTest * 100000 + exec.vu.iterationInScenario) * 30 * 60000;
    r = post('/appointments', { patientId: p.id, dentistId: c.dentist, startAt: slot, endAt: slot + 30 * 60000 },
      c.token, 'appointment_book', { 'Idempotency-Key': uuid() });
  } else if (roll < 0.80) {
    r = get(`/patients/${p.id}/chart`, c.token, 'chart_get');
  } else if (roll < 0.85) {
    r = post('/visits', { patientId: p.id, complaint: 'Sensitivity', diagnosis: 'Load test', notes: 'Dummy' }, c.token, 'visit_create');
  } else if (roll < 0.90) {
    const inv = post('/invoices', { patientId: p.id, discountPaise: 0,
      lines: [{ description: 'Scaling', qty: 1, amountPaise: 120000, taxBasisPoints: 0 }] },
      c.token, 'invoice_issue', { 'Idempotency-Key': uuid() });
    check(inv, { 'invoice 2xx': (x) => x.status >= 200 && x.status < 300 });
    r = post(`/invoices/${inv.json('id')}/payments`, { mode: 'UPI', amountPaise: 120000 }, c.token, 'payment', { 'Idempotency-Key': uuid() });
  } else if (roll < 0.95) {
    r = get('/sync/pull?since=0&limit=200', c.token, 'sync_pull');
  } else if (roll < 0.98) {
    r = get('/reports/daily-collection', c.token, 'report');
  } else {
    r = post('/patients', { fullName: `New Load ${uuid().slice(0, 8)}` }, c.token, 'patient_create');
  }
  check(r, { '2xx': (x) => x.status >= 200 && x.status < 300 });
  sleep(1 + Math.random() * 2);
}

export function login(clinics) {
  const c = clinics[Math.floor(Math.random() * clinics.length)];
  const r = http.post(BASE + '/auth/login', JSON.stringify({ email: c.email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } });
  check(r, { 'login 200': (x) => x.status === 200 });
}

export function handleSummary(data) {
  return {
    '/out/summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const m = data.metrics;
  const ms = (v) => (v === undefined ? '-' : `${v.toFixed(1)} ms`);
  const lines = ['', 'DentaCloud load test'];
  const staffKey = Object.keys(m).find((k) => k === 'http_req_duration{scenario:staff}');
  if (staffKey) {
    const v = m[staffKey].values;
    lines.push(`staff requests  p50 ${ms(v.med)}  p95 ${ms(v['p(95)'])}  p99 ${ms(v['p(99)'])}  max ${ms(v.max)}`);
  }
  if (m['http_req_duration{name:search}']) {
    const v = m['http_req_duration{name:search}'].values;
    lines.push(`patient search  p50 ${ms(v.med)}  p95 ${ms(v['p(95)'])}  p99 ${ms(v['p(99)'])}`);
  }
  lines.push(`requests ${m.http_reqs.values.count}  (${m.http_reqs.values.rate.toFixed(1)}/s)  failed ${(m.http_req_failed.values.rate * 100).toFixed(2)} %`);
  for (const [name, t] of Object.entries(data.metrics)) {
    if (t.thresholds) {
      for (const [expr, res] of Object.entries(t.thresholds)) lines.push(`${res.ok ? 'PASS' : 'FAIL'}  ${name}  ${expr}`);
    }
  }
  return lines.join('\n') + '\n';
}
