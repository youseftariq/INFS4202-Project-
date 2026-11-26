// PERF-02: the Engine answers an eligibility or match request in at most 200 ms, and ranks 200 candidates in at most
// 500 ms, at the 95th percentile. Test cases TC-PERF-001 (eligibility, match) and TC-PERF-002 (rank 200).
//
// Run (Engine running on 127.0.0.1:8081):
//   k6 run tests/performance/engine-perf02.js
// Optional: -e ENGINE_URL=http://127.0.0.1:8081/api/v1   -e VUS=10 (concurrent users; default 1)
//           --summary-export tests/performance/results/engine-perf02.json
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.ENGINE_URL || 'http://127.0.0.1:8081/api/v1';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };
const VUS = Number(__ENV.VUS || 1);

// PERF-02 names no load, so the default is one user sending requests back to back (the fairest reading); VUS=10 adds
// concurrency. Each endpoint gets its own 30 s scenario, one after the other, so the p95 of one endpoint is not mixed
// with load on another.
export const options = {
  scenarios: {
    eligibility: { executor: 'constant-vus', vus: VUS, duration: '30s', exec: 'eligibility', startTime: '0s' },
    match: { executor: 'constant-vus', vus: VUS, duration: '30s', exec: 'match', startTime: '35s' },
    rank200: { executor: 'constant-vus', vus: VUS, duration: '30s', exec: 'rank200', startTime: '70s' },
  },
  thresholds: {
    'http_req_duration{endpoint:eligibility}': ['p(95)<=200'],
    'http_req_duration{endpoint:match}': ['p(95)<=200'],
    'http_req_duration{endpoint:rank200}': ['p(95)<=500'],
    'checks{endpoint:eligibility}': ['rate==1'],
    'checks{endpoint:match}': ['rate==1'],
    'checks{endpoint:rank200}': ['rate==1'],
  },
};

const today = new Date().toISOString().slice(0, 10);

const eligibilityBody = JSON.stringify({
  gpa: 3.1, creditsCompleted: 75, standing: 'GOOD', priorInternships: 0, minGpa: 2.5, minCredits: 45, asOf: today,
  documents: [
    { type: 'CV', sizeBytes: 150000, mimeType: 'application/pdf' },
    { type: 'TRANSCRIPT', sizeBytes: 300000, mimeType: 'application/pdf', issueDate: today },
  ],
});

const matchBody = JSON.stringify({
  studentSkills: ['Java', 'SQL', 'Git', 'Python', 'Excel'],
  requiredSkills: ['Java', 'Selenium', 'SQL'],
  niceToHaveSkills: ['Git', 'Playwright'],
});

// 200 candidates with varied scores, GPAs and submission times (realistic ties included)
const candidates = [];
for (let i = 1; i <= 200; i++) {
  const day = String(1 + (i % 28)).padStart(2, '0');
  const hour = String(i % 24).padStart(2, '0');
  candidates.push({
    applicationId: i,
    score: (i * 37) % 101,
    gpa: Math.round(((i * 13) % 401)) / 100,
    submittedAt: `2026-10-${day}T${hour}:00:00+03:00`,
  });
}
const rankBody = JSON.stringify({ seats: 5, candidates });

export function eligibility() {
  const r = http.post(`${BASE}/eligibility`, eligibilityBody, Object.assign({ tags: { endpoint: 'eligibility' } }, JSON_HEADERS));
  check(r, { 'eligibility 200 with a verdict': (res) => res.status === 200 && typeof res.json('eligible') === 'boolean' },
    { endpoint: 'eligibility' });
}

export function match() {
  const r = http.post(`${BASE}/match`, matchBody, Object.assign({ tags: { endpoint: 'match' } }, JSON_HEADERS));
  check(r, { 'match 200 with a score': (res) => res.status === 200 && typeof res.json('score') === 'number' },
    { endpoint: 'match' });
}

export function rank200() {
  const r = http.post(`${BASE}/rank`, rankBody, Object.assign({ tags: { endpoint: 'rank200' } }, JSON_HEADERS));
  check(r, { 'rank 200 ranks all 200': (res) => res.status === 200 && res.json('ranked').length === 200 },
    { endpoint: 'rank200' });
}
