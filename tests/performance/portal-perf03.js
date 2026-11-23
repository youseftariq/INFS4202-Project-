// PERF-03: the Portal serves 50 concurrent users browsing postings and submitting applications for 5 minutes with no
// errors and no response above 3 seconds. Test cases TC-PERF-005 (PERF-03) and TC-PERF-006 (limits under load).
//
// Before running: start fresh (.\start.cmd --jar --reset); optionally add 1000 applications (Coordinator → Tools).
// Run:  k6 run tests/performance/portal-perf03.js
// Optional: -e PORTAL_URL=http://127.0.0.1:8080  -e VUS=50  -e DURATION=5m
//
// Each virtual user logs in as one of the seeded students and repeats: open the postings list, open one posting,
// and in one iteration out of three submit an application with a valid letter, with 1–3 s of think time between steps.
// Applications refused by a business rule (limit reached, already applied, not eligible) are normal answers, not errors.
import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.PORTAL_URL || 'http://127.0.0.1:8080';
const PASSWORD = 'Uniplace!2026';

const submitted = new Counter('applications_submitted');
const refused = new Counter('applications_refused');

export const options = {
  noCookiesReset: true,
  scenarios: {
    students: { executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '5m', exec: 'student' },
  },
  thresholds: {
    'http_req_failed{phase:test}': ['rate==0'],       // no errors
    'http_req_duration{phase:test}': ['max<3000'],     // no response above 3 s
    'checks': ['rate==1'],
  },
};

const LETTER = 'I am applying for this internship because it matches the skills I developed in my course projects. '
  + 'I have worked in teams, used version control, and I am keen to learn from experienced colleagues in a real '
  + 'company setting from the first week.';

function csrf(res) {
  return res.html().find('input[name=_csrf]').attr('value');
}

function login(user) {
  const form = http.get(`${BASE}/login`, { tags: { phase: 'login' } });
  const r = http.post(`${BASE}/login`, { username: user, password: PASSWORD, _csrf: csrf(form) }, { tags: { phase: 'login' } });
  if (!r.url.includes('/dashboard')) fail(`login failed for ${user}`);
}

// setup(): the coordinator lists the student accounts that can log in
export function setup() {
  login('coordinator');
  const users = [];
  http.get(`${BASE}/coordinator/users`, { tags: { phase: 'setup' } }).html().find('tbody tr').each((_, tr) => {
    const name = tr.textContent().trim().split(/\s+/)[0];
    if (/^student\.[a-z0-9]+$/.test(name)) users.push(name);
  });
  if (users.length === 0) fail('no student accounts found');
  return { users };
}

let postingIds = null;

export function student(data) {
  if (__ITER === 0) {
    login(data.users[(__VU - 1) % data.users.length]);
  }
  const list = http.get(`${BASE}/postings`, { tags: { phase: 'test', step: 'list' } });
  check(list, { 'postings list 200': (r) => r.status === 200 && !r.url.includes('/login') });
  if (postingIds === null) {
    postingIds = [];
    list.html().find('a').each((_, a) => {
      const href = a.attributes().href ? a.attributes().href.value : '';
      const m = href.match(/^\/postings\/(\d+)$/);
      if (m) postingIds.push(m[1]);
    });
    postingIds = [...new Set(postingIds)];
  }
  sleep(1 + Math.random() * 2);

  const id = postingIds[Math.floor(Math.random() * postingIds.length)];
  const detail = http.get(`${BASE}/postings/${id}`, { tags: { phase: 'test', step: 'detail' } });
  check(detail, { 'posting page 200': (r) => r.status === 200 });
  sleep(1 + Math.random() * 2);

  if (__ITER % 3 === 0 && detail.html().find('#apply-form').size() > 0) {
    const r = http.post(`${BASE}/postings/${id}/apply`, { letter: LETTER, _csrf: csrf(detail) },
      { tags: { phase: 'test', step: 'apply' } });
    check(r, { 'apply answered 200': (res) => res.status === 200 });
    if (/\/applications\/\d+$/.test(r.url)) submitted.add(1); else refused.add(1);
    sleep(1 + Math.random() * 2);
  }
}
