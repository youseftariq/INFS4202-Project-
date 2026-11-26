// PERF-01: every list page (postings, applications, candidates, outbox) renders in at most 2 seconds at the 95th
// percentile with the seed data plus 1,000 additional applications. PERF-05: no page returns an unbounded list.
// Test cases TC-PERF-003 (PERF-01) and TC-PERF-004 (PERF-05).
//
// Before running: start fresh (.\start.cmd --jar --reset), log in as coordinator → Tools → Generate test data → 1000.
// Run:  k6 run tests/performance/portal-perf01.js
// Optional: -e PORTAL_URL=http://127.0.0.1:8080  -e VUS=1 (users per role)  -e DURATION=60s
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE = __ENV.PORTAL_URL || 'http://127.0.0.1:8080';
const PASSWORD = 'Uniplace!2026';
const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || '60s';
const MAX_ROWS = 50;   // PERF-05: a bounded list shows at most a page of rows (APP-10 pages applications by 20)

export const options = {
  noCookiesReset: true,   // stay logged in across iterations (k6 clears cookies between iterations by default)
  scenarios: {
    coordinator: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'coordinator' },
    recruiter: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'recruiter' },
    student: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'student' },
  },
  thresholds: {
    'http_req_duration{page:postings}': ['p(95)<=2000'],
    'http_req_duration{page:applications}': ['p(95)<=2000'],
    'http_req_duration{page:candidates}': ['p(95)<=2000'],
    'http_req_duration{page:outbox}': ['p(95)<=2000'],
    'http_req_duration{page:notifications}': ['p(95)<=2000'],
    'checks{kind:loaded}': ['rate==1'],
    'checks{kind:bounded}': ['rate==1'],
  },
};

function login(user) {
  const form = http.get(`${BASE}/login`, { tags: { page: 'login' } });
  const token = form.html().find('input[name=_csrf]').attr('value');
  const r = http.post(`${BASE}/login`, { username: user, password: PASSWORD, _csrf: token }, { tags: { page: 'login' } });
  if (!r.url.includes('/dashboard')) {
    fail(`login failed for ${user}: ended on ${r.url}`);
  }
}

function rows(res) {
  return res.html().find('tbody tr').size();
}

function visit(path, page) {
  const res = http.get(`${BASE}${path}`, { tags: { page } });
  check(res, { [`${page} loaded`]: (r) => r.status === 200 && !r.url.includes('/login') }, { kind: 'loaded' });
  check(res, { [`${page} bounded (<= ${MAX_ROWS} rows)`]: (r) => rows(r) <= MAX_ROWS }, { kind: 'bounded' });
  return res;
}

let candidatePaths = null;

export function coordinator() {
  if (__ITER === 0) login('coordinator');
  visit('/postings', 'postings');
  visit('/coordinator/applications', 'applications');
  visit('/coordinator/applications?page=30', 'applications');
  visit('/coordinator/outbox', 'outbox');
  visit('/coordinator/outbox?page=30', 'outbox');
}

export function recruiter() {
  if (__ITER === 0) {
    login('recruiter.qtech');
    const list = http.get(`${BASE}/recruiter/postings`, { tags: { page: 'setup' } });
    candidatePaths = [];
    list.html().find('a').each((_, a) => {
      const href = a.attributes().href ? a.attributes().href.value : '';
      const m = href.match(/^\/(?:recruiter\/)?postings\/(\d+)$/);
      if (m) candidatePaths.push(`/recruiter/postings/${m[1]}/candidates`);
    });
    candidatePaths = [...new Set(candidatePaths)];
  }
  visit('/applications', 'applications');
  for (const p of candidatePaths) visit(p, 'candidates');
}

export function student() {
  if (__ITER === 0) login('student.amal');
  visit('/postings', 'postings');
  visit('/applications', 'applications');
  visit('/notifications', 'notifications');
}
