# Q3. Test design and execution: Offers & Allowance, Performance, Settings (Salman)

Draft for the final report (section Q3). Test cases: `docs/test-cases.md`. How to run the tests: `tests/README.md`.

## Why Offers & Allowance is a primary module

Offers & Allowance (OFF-01 to OFF-07) touches more of the strategy's high risks than any other module:
- It decides money: the allowance, the transport supplement, the cap and the late-documents penalty (R2).
- It changes the state of applications and postings: accepting, withdrawing and filling seats, including the race on the
  last seat (R3, R4).
- It is time-dependent: the 72-hour response window, reminders and expiry (R8).
- It is the main place where the Portal depends on the Engine (`/allowance`) and on Settings (R5, R10).

## Test cases

40 test cases: 31 for the primary module (OFF), 7 for performance (PERF) and 2 for Settings (ADM-01).

| Module | Test cases | Black-box | White-box | Functional | Non-functional | Pass | Fail | Blocked |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Offers & Allowance (OFF) | 31 | 25 | 6 | 26 | 5 | 17 | 14 | 0 |
| Performance (PERF) | 7 | 7 | 0 | 0 | 7 | 4 | 2 | 1 |
| Settings (ADM-01) | 2 | 0 | 2 | 2 | 0 | 0 | 2 | 0 |
| **Total** | **40** | **32** | **8** | **28** | **12** | **21** | **18** | **1** |

**Classification used:**
- **White-box:** the test case was designed from the code: statement or branch coverage, data-flow review or code
  review. These are TC-OFF-012, 016, 018, 019, 023, 031, TC-ADM-001 and TC-ADM-002.
- **Non-functional:** the test case verifies a REL or PERF requirement. These are TC-OFF-010, 011, 017, 024, 031 and all PERF
  cases.

**Techniques in the primary module:**

| Technique | Test cases |
| --- | --- |
| Boundary value analysis | 8 (TC-OFF-002, 003, 006, 007, 008, 009, 011, 021) |
| Equivalence partitioning | 3 (TC-OFF-001, 005, 022) |
| Decision table | 1 (TC-OFF-004) |
| State transition and state-based tests | 8 (TC-OFF-013, 014, 015, 025, 026, 027, 028, and the reminder part of 018) |
| Error guessing and fault injection | 3 (TC-OFF-010, 024, 031) |
| Concurrency test | 1 (TC-OFF-017) |
| Exploratory testing and use case test | 3 (TC-OFF-020, 029, 030) |
| Coverage, data-flow review or code review (white-box) | 6 (TC-OFF-012, 016, 018, 019, 023, 031) |

TC-OFF-018 combines a code review with a state-based test, and TC-OFF-031 is fault injection designed from a path the
coverage report showed as uncovered, so both appear in two rows. The other rows each list a test case once.

**Levels:** 13 API, 17 integration, 10 system. **Automation:** 33 test cases are automated, and 7 are run by hand on the
running system (TC-OFF-020, 029, 030, TC-PERF-006, 007, TC-ADM-001, 002).

## Automated tests

| Suite | Level | Tool | Tests | Passed | Failed | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `tests/api` `AllowanceApiTest` | API (black-box, Engine) | JUnit 5, Java `HttpClient`, Jackson | 30 | 20 | 10 | Starts the Engine jar on a free port; failures are #12, #13, #14, #16 |
| `OfferRulesTest` | Integration (white-box, Portal) | JUnit 5, Spring Boot Test, Mockito, controllable clock | 9 | 0 | 9 | Each failure is a defect: #6, #7, #8, #9, #10, #11, #15, #17, #41 |
| `OfferAgreementTest` | Integration (Portal) | JUnit 5, Spring Boot Test, MockMvc, Mockito | 11 | 11 | 0 | Agreement date, late days, Engine down, offer notifications |
| Portal full suite (vendor + ours) | Unit and integration | Maven, JUnit 5, JaCoCo | 167 | 158 | 9 | The 9 failures are `OfferRulesTest` |
| `engine-perf02.js` | API (performance) | k6 v2.4.0 | 6 thresholds | 5 | 1 | `/rank` with 200 candidates: p95 910 ms (limit 500 ms) |
| `portal-perf01.js` | System (performance) | k6 v2.4.0 | 7 thresholds | 6 | 1 | List pages p95 ≤ 28 ms; the Outbox is not paginated |
| `portal-perf03.js` | System (performance) | k6 v2.4.0 | 3 thresholds | 3 | 0 | 50 users for 5 minutes: 8,686 requests, 0 errors, slowest 207 ms |
| `tests/repro/off-04-last-seat-race.sh` | System (reproduction) | bash, curl | 1 script | 0 | 1 | Both students accept the last seat (#6) |

**How to run:**
- **Portal:** `cd portal; .\mvnw.cmd test "-Dmaven.test.failure.ignore=true"`.
- **Engine API:** `cd portal; .\mvnw.cmd -f ..\tests\api\pom.xml test`.
- **k6:** `k6 run tests/performance/<script>.js`, with the system started by `.\start.cmd --jar --reset`.

See `tests/README.md` for the JDK note and the test data each script needs.

## Coverage

JaCoCo, measured on 26/11/2026 with the full Portal suite:

| Code | Lines before our tests | Lines after | Branches before | Branches after |
| --- | --- | --- | --- | --- |
| `OfferService` | 57% | 87% | 37% | 59% |
| `service` package | 72.4% | 77.5% | 52.8% | 56.2% |
| `EngineRequestMapper` | | 96% | | 75% |

**Not covered in `OfferService`:**
- `formFor`: the offer form pre-fill, which is a display helper.
- `tryComputeAllowance`, the Engine-down path while a student accepts, was the other gap on 24/11. TC-OFF-031 now
  covers it (100%) and found #41.

The Engine is a black box, so its coverage is not measured. The API tests cover every rule of OFF-05 and the invalid
inputs of the OpenAPI contract instead.
