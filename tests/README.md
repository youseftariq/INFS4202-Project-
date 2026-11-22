# Automated tests

Every test names the requirement and the designed test case it implements, in `@DisplayName`. For example,
`TC-OFF-004 / OFF-05` points to the matrix in `docs/test-cases.md`.

| Folder | Suite | Level | Status |
| --- | --- | --- | --- |
| `portal/src/test` | Portal unit and integration tests (vendor suite + ours) | Unit, integration (white-box) | In use |
| `tests/api/` | Engine API tests | API (black-box) | In use |
| `tests/e2e/` | End-to-end tests (for example Playwright) | System | Planned |
| `tests/performance/` | Performance scripts and results (k6) | Non-functional | Planned |
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
  - `service/OfferRulesTest`: TC-OFF-012 to TC-OFF-019 (OFF-01..04, NOT-04, ADM-01)

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

## Results

Failing tests are expected: each failure is a defect in the system, linked from the test case and the issue.

| Date | Suite | Tests | Passed | Failed | Notes |
| --- | --- | --- | --- | --- | --- |
| 22/11/2026 | Portal vendor suite (baseline, before our tests) | 147 | 147 | 0 | Line coverage: `service` 72.4% (branches 52.8%), `OfferService` 57% (branches 37%) |
| 22/11/2026 | Portal `OfferRulesTest` | 8 | 0 | 8 | All 8 fail on OFF-02, OFF-03, OFF-01/APP-06, OFF-04, NOT-04 and ADM-01 defects |
| 22/11/2026 | Engine `AllowanceApiTest` | 30 | 20 | 10 | Failures: transport at 30 h, monthly cap never applied, month starting on the 31st, 500 for missing or wrongly typed fields |
