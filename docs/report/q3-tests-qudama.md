# Q3. Test design and execution: Applications (Qudama)

Draft for the final report (section Q3). Test cases: `docs/test-cases.md` (TC-APP-001 to TC-APP-030). How to run the
tests: `tests/README.md`.

## Why Applications is a primary module

Applications (APP-01 to APP-10) is where every other module meets:
- It applies the rules a student is judged by: eligibility, active-application limits, one application per posting,
  and the shortlist threshold (APP-02, APP-03, APP-07).
- It owns the application state machine that Interviews and Offers build on (APP-06, APP-08).
- It depends on the Engine for three decisions: `/eligibility`, `/match` and `/rank` (APP-04, APP-09). So it is
  exposed to wrong Engine answers and to the Engine being down (REL-01, REL-02).
- It reads its limits and threshold from Settings and its freeze rule from the company status (ADM-01, COM-02).

## Test cases

30 test cases, TC-APP-001 to TC-APP-030.

| Module | Test cases | Black-box | White-box | Functional | Non-functional | Pass | Fail | Not run |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Applications (APP) | 30 | 26 | 4 | 25 | 5 | 4 | 19 | 7 |

**Classification used:**
- **White-box:** the test case was designed from the code: data-flow review, code review of a request sent to the
  Engine, or branch coverage. These are TC-APP-009, 010, 013 and 026. TC-APP-005 also covers both branches of
  `activeLimitFor`, but it was designed from the APP-02 boundary, so it is counted as black-box.
- **Non-functional:** the test case verifies a REL, PERF or USA requirement. These are TC-APP-011 (REL-01), 021 (REL-02),
  027 (USA-07, PERF-05), 028 (PERF-02) and 029 (USA-01, USA-06).

**Techniques (one main technique per test case):**

| Technique | Test cases |
| --- | --- |
| Boundary value analysis | 10 (TC-APP-001, 002, 003, 004, 005, 015, 018, 019, 025, 027) |
| Equivalence partitioning | 5 (TC-APP-006, 016, 021, 022, 023) |
| Decision table | 4 (TC-APP-008, 017, 020, 024) |
| State transition and state-based tests | 4 (TC-APP-007, 013, 014, 030) |
| Error guessing | 3 (TC-APP-011, 012, 029) |
| Data-flow and code review (white-box) | 3 (TC-APP-009, 010, 026) |
| Performance measurement | 1 (TC-APP-028) |

TC-APP-021 and 023 also use boundary values, and TC-APP-005 also covers branches. They are listed once, under their main
technique.

**Levels:** 5 unit, 7 integration, 9 API, 9 system.

**Automation:** 22 test cases are automated (14 in the Portal suite, 8 in the Engine API suite). TC-APP-028 was measured
with a Python script that sends the requests one at a time; the script is not in the repository. TC-APP-009, 011 and
014 were also run by hand on the running system, before they were automated.

**Not run (7):** TC-APP-008, 012, 013, 016, 027, 029 and 030.
- TC-APP-013 (the 7 × 7 transition matrix) is largely covered by the vendor's `ApplicationStateMachineTest` (25 tests,
  all passing), so it was given a lower priority.
- TC-APP-030 overlaps #29, which Salman filed and confirmed.
- The others are system tests to run on a fresh `--reset` start before the final tag.

## Automated tests

| Suite | Level | Tool | Tests | Passed | Failed | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `ApplicationRulesTest` | Integration (Portal, white-box) | JUnit 5, Spring Boot Test, Mockito (Engine stubbed) | 22 | 10 | 12 | Each failure is a defect: #38, #47, #48, #49, #50, #51, #52, #53, #54 |
| `ApplicationEngineRulesTest` | Unit (Portal, white-box) | JUnit 5, WireMock, Mockito | 5 | 1 | 4 | Failures are #39 (Engine down) and #40 (match-score cache) |
| `tests/api` `ApplicationsApiTest` | API (Engine, black-box) | JUnit 5, Java `HttpClient`, Jackson | 34 | 19 | 15 | Starts the Engine jar on a free port; failures are #2, #32, #33, #34, #35, #36, #37 |
| Portal full suite (vendor + ours) | Unit and integration | Maven, JUnit 5, JaCoCo | 239 | 204 | 35 | Run on `main` on 26/11/2026; 16 of the 35 are ours, the rest are Offers and Security tests |
| TC-APP-028 timing script | API (performance) | Python 3, `urllib` | 3 endpoints | 2 | 1 | `/rank` with 200 candidates: 1.7 s to 13 s depending on the data (limit 500 ms), reported in #21 |

Every failing test fails for the predicted reason. This was checked in the CI log before the results were recorded:
for example, *"The match score 60 is below the shortlist threshold 60."* The passing tests are the boundary values
that should pass (letters of 100 and 2,000 characters, the 5th GOOD application, scores 59 and 61, a PENDING
company). They show that the tests separate correct and incorrect behaviour, instead of failing on everything.

**How to run:**
- **Portal:** `cd portal; .\mvnw.cmd test "-Dtest=ApplicationRulesTest,ApplicationEngineRulesTest"`. These also run on
  every push in the GitHub *Tests* workflow.
- **Engine API:** `cd portal; .\mvnw.cmd -f ..\tests\api\pom.xml test "-Dtest=ApplicationsApiTest"`. Salman ran this
  suite on his machine on 26/11/2026 and got the same 34 / 19 / 15.

## Coverage

JaCoCo, from the CI reports of the two runs on `main` just before and just after our tests were merged (#45 and #46,
26/11/2026):

| Class | Lines before | Lines after | Branches before | Branches after |
| --- | --- | --- | --- | --- |
| `ApplicationService` | 74% (92/124) | 78% (97/124) | 53% (31/58) | 60% (35/58) |
| `EngineClient` | 87% (39/45) | 91% (41/45) | 50% (1/2) | 50% (1/2) |
| `MatchScoreService` | 91% (20/22) | 91% | 100% (4/4) | 100% |
| `EngineRequestMapper` | 96% (46/48) | 96% | 75% (3/4) | 75% |
| `RankingService` | 96% (26/27) | 96% | 80% (8/10) | 80% |
| `PostingService` | 71% (87/123) | 71% | 53% (58/110) | 53% |

**What this shows:** line coverage of the module was already high before our tests, and it barely moved. Yet our 16
failing Portal tests expose 11 defects (#38 to #40 and #47 to #54) in code the vendor's tests already executed:
- `MatchScoreService` had 100% branch coverage and the cache key was still wrong (#40).
- `EngineRequestMapper` had 96% line coverage and still sent the wrong minimum GPA (#51).

The vendor's tests ran these lines without checking the values that matter, so coverage was not a good measure of how
well this module was tested. Our tests add assertions on those values at the boundaries in the Requirements.
