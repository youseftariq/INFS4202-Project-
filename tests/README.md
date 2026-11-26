# Automated tests

Every test names the requirement and the designed test case it implements, in `@DisplayName`. For example,
`TC-OFF-004 / OFF-05` points to the matrix in `docs/test-cases.md`.

| Folder | Suite | Level | Status |
| --- | --- | --- | --- |
| `portal/src/test` | Portal unit and integration tests (vendor suite + ours) | Unit, integration (white-box) | In use |
| `tests/api/` | Engine API tests | API (black-box) | In use |
| `tests/e2e/` | End-to-end tests (for example Playwright) | System | Planned |
| `tests/performance/` | Performance scripts and results (k6) | Non-functional | In use |
| `tests/repro/` | Scripts that reproduce a defect on the running system | System | In use |
| `tests/ai-eval/` | AI Assistant evaluation scripts and raw outputs | AI | Planned |

## Requirements

- A JDK 17 or 21. The project requires 17 or 21, and CI uses 17.
- **Newer JDKs (for example 27):** Mockito does not support them yet, and the Portal tests fail with
  "Mockito cannot mock this class". To work around it, set this for the current terminal before running Maven:
  - PowerShell: `$env:JAVA_TOOL_OPTIONS="-Dnet.bytebuddy.experimental=true"`
  - bash: `export JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`
- **`JAVA_HOME`** must point to the JDK, for example `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"`.

## Portal tests (white-box)

```powershell
cd portal
.\mvnw.cmd test "-Dmaven.test.failure.ignore=true"
```

- **Reports:** results are in `portal/target/surefire-reports`, and line and branch coverage is in `portal/target/site/jacoco/index.html`.
  The `failure.ignore` flag makes Maven write the coverage report even when tests fail.
- **One class only:** add `-Dtest=OfferRulesTest`.
- **Our classes:**
  - `service/OfferRulesTest`: TC-OFF-012 to TC-OFF-019 and TC-OFF-031 (OFF-01..04, NOT-04, ADM-01, REL-01)
  - `service/OfferAgreementTest`: TC-OFF-021 to TC-OFF-028 (OFF-07, REL-01, NOT-01, NOT-02)

## Engine API tests (black-box)

```powershell
cd portal
.\mvnw.cmd -f ..\tests\api\pom.xml test
```

- **Where to run it:** from `portal`, so that the Maven wrapper is found.
- **Engine startup:** the tests start `engine/uniplace-engine.jar` themselves on a free port, so nothing else needs to be running.
  To test an Engine that is already running instead, add `"-Dengine.url=http://127.0.0.1:8081/api/v1"`.
- **Our classes:**
  - `AllowanceApiTest`: TC-OFF-001 to TC-OFF-011 (`/allowance`: OFF-05, OFF-07, REL-02)

## Performance tests (k6)

- **Install:** k6 v2.4.0 from the official releases page (github.com/grafana/k6/releases). Unzip it and put the folder on the PATH,
  or call `k6.exe` by its full path.
- **Start the system:** run `.\start.cmd --jar --reset`. For PERF-01 and PERF-05, also log in as `coordinator` → Tools →
  Generate test data → 1000.

Run each script from the repository root:

```powershell
k6 run tests/performance/engine-perf02.js
```
```powershell
k6 run tests/performance/portal-perf01.js
```
```powershell
k6 run tests/performance/portal-perf03.js
```

| Script | Requirement | Test cases | Load |
| --- | --- | --- | --- |
| `engine-perf02.js` | PERF-02 | TC-PERF-001, 002 | 1 user per endpoint, 30 s each (`-e VUS=10` for 10 users) |
| `portal-perf01.js` | PERF-01, PERF-05 | TC-PERF-003, 004 | 1 user per role (coordinator, recruiter, student), 60 s |
| `portal-perf03.js` | PERF-03 | TC-PERF-005, 006 | 50 students, 5 minutes, 1–3 s think time |

- **Pass or fail:** each script states the requirement as k6 thresholds, so k6 prints ✓ or ✗ for each and exits with
  an error when a threshold fails.
- **Saved results:** raw summaries are in `tests/performance/results/` (`--summary-export`).

## Results

Failing tests are expected: each failure is a defect in the system, linked from the test case and the issue.

| Date | Suite | Tests | Passed | Failed | Notes |
| --- | --- | --- | --- | --- | --- |
| 22/11/2026 | Portal vendor suite (baseline, before our tests) | 147 | 147 | 0 | Line coverage: `service` 72.4% (branches 52.8%), `OfferService` 57% (branches 37%) |
| 22/11/2026 | Portal `OfferRulesTest` | 8 | 0 | 8 | All 8 fail on OFF-02, OFF-03, OFF-01/APP-06, OFF-04, NOT-04 and ADM-01 defects |
| 22/11/2026 | Engine `AllowanceApiTest` | 30 | 20 | 10 | Failures: transport at 30 h, monthly cap never applied, month starting on the 31st, 500 for missing or wrongly typed fields |
| 23/11/2026 | Portal `OfferAgreementTest` | 11 | 11 | 0 | Agreement date, late days, refusals, Engine down while recalculating, offer notifications: all as required |
| 23/11/2026 | k6 `engine-perf02.js` (1 user) | 6 thresholds | 5 | 1 | `/rank` with 200 candidates: p95 910 ms (limit 500 ms); eligibility and match p95 about 0.6 ms. With 10 users, rank p95 is 3.5 s |
| 23/11/2026 | k6 `portal-perf01.js` (seed + 1,000) | 7 thresholds | 6 | 1 | All list pages p95 ≤ 28 ms; the Outbox is not paginated (PERF-05) |
| 23/11/2026 | k6 `portal-perf03.js` (50 users, 5 min) | 3 thresholds | 3 | 0 | 8,686 requests, 0 errors, slowest 207 ms |
| 24/11/2026 | Portal full suite (vendor + ours), with coverage | 166 | 158 | 8 | The 8 failures are `OfferRulesTest` (defects). Line coverage: `service` 77.1% (branches 56.2%), up from 72.4% (52.8%); `OfferService` 84% (branches 59%), up from 57% (37%); `EngineRequestMapper` 96%. Still not covered in `OfferService`: the offer form pre-fill (`formFor`) and the Engine-down path while accepting (`tryComputeAllowance`) |
| 26/11/2026 | Portal `OfferRulesTest` (with TC-OFF-031) | 9 | 0 | 9 | New test: accepting while the Engine is down goes through without an allowance (REL-01, #41) |
| 26/11/2026 | Portal full suite (vendor + ours), with coverage | 167 | 158 | 9 | The 9 failures are `OfferRulesTest` (defects). Line coverage: `service` 77.5% (branches 56.2%); `OfferService` 87% (branches 59%). `tryComputeAllowance` is now covered by TC-OFF-031 |
