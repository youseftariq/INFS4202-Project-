<!--
TEAM CHECKLIST: delete this block before making the PDF.
Done: Yousef's AI log rows and reflection (#67, #68, #69); Qudama's reflection (#70); the known-failures list (#73);
#72 in the totals (51 issues); names and student IDs on the cover; the three test-only exceptions in Q4.
Still open:
- Cover page: add the team name and the final tag date.
- Qudama: re-run #48, #49, #50, #51 and #53 yourself and update your reflection. It says Claude carried out those
  reproductions, while the cover declares every defect was "reproduced by us".
- AI findings AI-03, AI-05, AI-06, AI-09, AI-10, AI-11 are not filed yet: file them with steps a grader can repeat, or
  keep them out of the defect totals (they are out of the totals below).
- "Reproduced" confirmations (strategy, defect workflow step 3): do them for the Critical and Major issues, or keep the
  sentence in Q2 that says they were not done.
- Check every number against GitHub and main on the day; then make the PDF (at most 15 pages + appendices),
  save it as docs/final-report.pdf, merge, add the instructor as collaborator, tag `final`.
-->

# UniPlace acceptance testing: final report

| Field | Value |
| --- | --- |
| Course | INFS4202 Software Testing & Quality Assurance, Fall 2026 |
| Project | UniPlace acceptance testing |
| Team name | *[team name]* |
| Members | Salman Ghazi, 60300519 · Qudama al Dulaimi, 60306911 · Yousef Natsheh, 60307770 |
| Repository | https://github.com/youseftariq/INFS4202-Project- (private; the instructor is a collaborator) |
| Final tag and date | `final`, *[dd/MM/yyyy]* |
| Primary modules | Offers & Allowance (Salman) · Applications (Qudama) · Users & Access / Security (Yousef) |
| Declaration | We declare that this work is our own, that every AI-assisted part is recorded in our AI usage log, and that every reported defect was reproduced by us on the running system. |

**In numbers:**
- 103 designed test cases.
- 92 team tests in the Portal suite, 77 Engine API tests, 3 k6 load scripts and an AI evaluation of 99 runs.
- **51 defects** reported in GitHub Issues: 9 Critical, 31 Major, 11 Minor, none closed as duplicate or invalid.

---

## Q1. How did we understand the system?

**Our model.** UniPlace has three parts:
- **The Portal** (Spring Boot, white-box) owns the UI, the business rules, the H2 database, access control and the
  Settings.
- **The Engine** (REST service on port 8081, black-box) answers five questions: `/eligibility`, `/match`, `/rank`,
  `/schedule/validate` and `/allowance`.
- **An AI provider** scores motivation letters. A built-in simulator is used when no key is set.

The Engine is stateless: the Portal sends everything in each request, including the Settings values. An Engine defect
therefore shows up in the Portal, and a Portal defect can hide behind a correct Engine. That is why we tested the
Portal–Engine seam on both sides.

```
 Browser: Student · Recruiter · Coordinator
      │  HTML forms, CSRF token on every POST
      ▼
 Portal :8080 ── web/ → service/ (rules, state machine, AccessGuard) → repository/ → H2
      │             ├── engine/ EngineClient + EngineRequestMapper ──► Engine :8081 (stateless)
      │             └── ai/ PromptBuilder → AiClient → AiResponseParser ──► AI provider / simulator
      └── Outbox: every notification is recorded (e-mail only when SMTP is set)
```

**Main flows:**

| Role | Flow | Engine calls |
| --- | --- | --- |
| Student | Profile and documents → apply with a letter → follow the application → accept or decline an offer → allowance statement | `/eligibility`, `/match` |
| Recruiter | Postings → shortlist (a justification is needed below the threshold) → schedule interviews → issue an offer | `/rank`, `/schedule/validate` |
| Coordinator | Approve companies → Settings → record the signed agreement date (the allowance is recalculated) → reports, Outbox, audit log | `/allowance` |

**The Portal–Engine seam.** Each endpoint had a defect in how the Portal calls it:

| Endpoint | Defects in how the Portal calls it |
| --- | --- |
| `/eligibility` | The posting's minimum GPA and the prior internships are never sent (#51); an unreachable Engine means "eligible" (#39) |
| `/match` | The score is cached per posting, so every applicant gets the first score (#40) |
| `/rank` | The total seats are sent instead of the free seats (#54) |
| `/schedule/validate` | Times are sent without a time zone and read 3 hours late (#59) |
| `/allowance` | The monthly cap from Settings is never sent (#15); an offer is accepted with no allowance when the Engine is down (#41) |

**What we inferred about the Engine** from `engine/openapi.yaml` and its answers (we have no source):
- **Simple calls are fast:** under 1 ms at p95.
- **Its errors sit on boundaries:** a GPA equal to the minimum (#2), the 180th transcript day (#33), exactly 30 weekly
  hours (#13), a 120-minute slot (#57), an end after 18:00 (#60) and a month starting on the 31st (#16).
- **It does not validate input** as its contract promises: #14, #37.
- **Several formulas differ from the Requirements:** the cap (#12), case-sensitive skills (#32), an empty nice-to-have
  list (#34), the GPA tie-break (#35) and `aboveCut` (#36).
- **Ranking cost grows much faster than the input:** 200 candidates take 0.9 s to 3.2 s, and 13 s when all tie (#21).

**How we built this understanding:**
1. Reading the Requirements and the OpenAPI contract. This alone found 3 specification defects (#19, #23, #24).
2. Reading the Portal package by package, and turning each suspicious line into a lead.
3. Exploring all three roles with the seed accounts, always after `--reset`.
4. Probing the Engine with curl, then with JUnit tests that start the jar themselves.
5. Measuring with JaCoCo (to find untested paths) and k6 (response times).

A lead became an issue only after a failing test or a reproduction on the running system.

## Q2. What process did we define, and did we follow it?

The strategy is in `docs/test-strategy.md`. It has a dated change log of 9 changes after the first draft (21/11/2026).

**Risks and scope.** We scored twelve risks by impact × likelihood. The six that drove most effort are also where most of
our defects came from:

| Risk | Defects it led to |
| --- | --- |
| R1: access to other users' data | #20, #26, #27 |
| R2: a wrong allowance | #12, #13, #15, #16 |
| R3/R4: illegal state transitions and the last-seat race | #6, #7, #9, #38, #53 |
| R5: an Engine failure treated as success | #39, #41 |
| R6: security misconfiguration | #4, #5, #28 |
| R12: the specification itself | #19, #23, #24 |

**In scope:** every Portal function, all roles, all Engine endpoints, the AI Assistant, the vendor tests and the
Requirements. **Out of scope:** other operating systems, real e-mail delivery and the Engine's code.

**Levels, techniques and tools:**

| Level | Tool | Why |
| --- | --- | --- |
| Unit and integration, white-box (`portal/src/test`) | JUnit 5, Mockito, Spring Boot Test, WireMock, JaCoCo | Reuses the vendor setup; coverage shows what was never run |
| API, black-box (`tests/api`) | JUnit 5 + `HttpClient`; the tests start the Engine jar | Runs with the Maven wrapper we already had, so nothing to install |
| System and exploratory (`tests/repro`) | Browser, curl, scripts | Finds what scripted tests miss; a script reproduces the last-seat race |
| Performance (`tests/performance`) | k6 v2.4.0 | One executable; thresholds are the PERF limits |
| AI evaluation (`tests/ai-eval`) | `AiEval.java` | Repeated runs against properties that must always hold |

**Alternatives we rejected:** Postman/Newman (it needs Node.js, and its tests are hard to review), JMeter (kept as a
fallback) and Playwright (not needed).

**Techniques:**
- **Black-box:** boundary values, equivalence partitioning, decision tables and an access matrix, state transitions,
  error guessing and fault injection, concurrency, exploratory charters, load and volume, and requirements review.
- **White-box:** coverage, data-flow review and code review.

**Roles:**
- **Salman:** Offers & Allowance, `/allowance`, performance; wrote the strategy.
- **Qudama:** Applications, `/eligibility`, `/match`, `/rank`, Git and CI.
- **Yousef:** Users & Access / Security, `/schedule/validate`, the AI evaluation; repository owner.

**Git workflow, and how well we followed it:**
- **Branches and reviews.** Work ran on `<member>/<topic>` branches and reached `main` only through pull requests.
  - Most pull requests had a teammate's approval before merging.
  - #31 was merged before its review points were fixed; the fixes came in #45.
  - #43 and #44 were merged without a review.

  Reviews found real problems. For example, the review of #31 checked eight code predictions against the source and
  widened #29 instead of letting a duplicate be filed.
- **CI.** The *Tests* workflow runs the Portal suite on every push. It is red on purpose: on `main`, 239 tests run and
  35 fail, each one exposing a filed defect. So we changed "no new failures" to "no failing test that is not on the
  known-failures list". The list (`docs/test-strategy.md`, *Known failures on `main`*) maps each of the 35 failing runs
  to its test case and issue, so a red run can be checked against it.
- **Conventions:**
  - The issue template is `.github/ISSUE_TEMPLATE/defect.md`, and the labels come from `labels.json`.
  - Test case IDs are `TC-<MODULE>-<NNN>`, and every automated test names its test case and requirement in
    `@DisplayName`.
  - Commit messages are in the imperative and name the test case or issue.
  - The AI usage log has one row per meaningful use.
- **Defect workflow:**
  1. Reproduce on a fresh `--reset` start, with relative dates.
  2. Add one category, one severity and one priority label, and the finder as assignee.
  3. Link a failing test where possible.

  *[The strategy also asks a second member to confirm each defect; state here whether this was done.]*

**What we changed, and why** (all dated in the change log):
- **New level:** reproduction scripts, for defects a person can't trigger by hand.
- **New evidence rule:** a failing test is accepted when the running system hides the defect behind another one.
- **Load choice:** PERF-02 is measured with 1 user, because the requirement names no load (#24).
- **Wider scope:** Settings became a tested module after a code review.
- **Coverage-driven tests:** JaCoCo gaps were used to design new tests (they found #41).
- **Duplicates:** a finding with the same root cause widens the existing issue.
- **Predictions:** a *Not run* test case may hold a code-review prediction, which must be confirmed before filing.
- **CI:** the known-failures rule above.

## Q3. How did we design and run our tests?

**Why these three primary modules:**
- **Offers & Allowance:** money and seats, across the Portal and the Engine.
- **Applications:** the core flow and the largest state machine.
- **Users & Access / Security:** every page depends on it, and its defects are Critical by definition.

**Test cases** (`docs/test-cases.md`): 103 in total. Each primary module has at least 21, against the minimum of 10.

| Module | Cases | Black-box | White-box | Functional | Non-functional | Pass | Fail | Not run / blocked |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Offers & Allowance (OFF) | 31 | 25 | 6 | 26 | 5 | 17 | 14 | 0 |
| Applications (APP) | 30 | 26 | 4 | 25 | 5 | 4 | 20 | 6 |
| Users & Access / Security (SEC) | 21 | 11 | 10 | 11 | 10 | 10 | 9 | 2 |
| Interviews (INT) | 12 | 12 | 0 | 10 | 2 | 6 | 5 | 1 |
| Performance (PERF) | 7 | 7 | 0 | 0 | 7 | 4 | 2 | 1 |
| Settings (ADM) | 2 | 0 | 2 | 2 | 0 | 0 | 2 | 0 |
| **Total** | **103** | **81** | **22** | **74** | **29** | **41** | **52** | **10** |

The AI Assistant is tested by the evaluation below. Its checks are not yet rows in the matrix.

**Automated tests** (commands in `tests/README.md`):

| Suite | Level | Tool | Tests | Passed | Failed | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Portal suite on `main` (vendor + ours) | Unit, integration | Maven, JUnit 5, JaCoCo | 239 | 204 | 35 | 147 vendor tests, all pass; the 35 failures are our defect tests |
| Offers: `OfferRulesTest`, `OfferAgreementTest` | Integration, white-box | Spring Boot Test, Mockito, controllable clock | 20 | 11 | 9 | Race test with two threads; Engine-down test |
| Applications: `ApplicationRulesTest`, `ApplicationEngineRulesTest` | Integration, unit | Spring Boot Test, Mockito, WireMock | 27 | 11 | 16 | |
| Security: `AccessControlTest` + 5 classes | Integration, unit, configuration | Spring Boot Test, MockMvc | 45 | 35 | 10 | Access matrix, CSRF, escaping |
| Engine API: `AllowanceApiTest` | API, black-box | JUnit 5, `HttpClient` | 30 | 20 | 10 | |
| Engine API: `ApplicationsApiTest` | API, black-box | JUnit 5, `HttpClient` | 34 | 19 | 15 | |
| Engine API: `ScheduleApiTest` | API, black-box | JUnit 5, `HttpClient` | 13 | 10 | 3 | |
| k6: 3 scripts | Performance | k6 v2.4.0 | 16 thresholds | 14 | 2 | 50 users for 5 minutes: 0 errors |
| AI evaluation | System | `AiEval.java` | 20 letters × 5 runs | see "Use of AI" | | 99 valid answers |

**Coverage** (JaCoCo):
- **`OfferService`:** 57% → 87% of lines, after our tests.
- **The `service` package:** 72.4% → 77.5%.
- **Applications** barely moved (`ApplicationService` 74% → 78%), yet its tests found 11 defects in lines the vendor
  tests already ran. For example, `MatchScoreService` had 100% branch coverage and still the wrong cache key (#40).

Coverage told us where nobody had looked, not how well a line was tested.

## Q4. What did we find?

51 issues, all open, none closed as duplicate or invalid.

| Severity | Count | Points if valid |
| --- | --- | --- |
| Critical | 9 | 27 |
| Major | 31 | 62 |
| Minor | 11 | 11 |
| **Total** | **51** | **100** |

| Category | Count | Category | Count |
| --- | --- | --- | --- |
| functional | 20 | security | 6 |
| integration | 8 | requirements | 3 |
| state | 8 | configuration | 2 |
| performance | 2 | ui, tests | 1 each |

**Critical defects:**
- #4: stack traces on error pages.
- #5: an unescaped company name.
- #6: the last seat accepted twice.
- #7: a second placement can be accepted.
- #20: the Reports page and CSV files are open to every role.
- #26: another student's application can be read by changing the id.
- #38: an ACCEPTED application can be withdrawn.
- #39: an unreachable Engine means "eligible".
- #40: one cached match score for every applicant.

**By member:**
- **Salman:** 20 (2 Critical, 11 Major, 7 Minor).
- **Qudama:** 19 (3, 14, 2).
- **Yousef:** 12 (4, 6, 2).

**Not in the totals:** six AI Assistant findings, which are not yet filed (see "Use of AI").

**Shown only by a failing test:** every other defect was reproduced on the running system after `--reset`, except these
three:
- **#15:** the Engine ignores the cap anyway (#12), so the screen looks the same.
- **#52:** no seeded application has a match score of exactly 60.
- **#54:** the ranking page shows it together with #36.

Each of these issues names the test that captures what the Portal does.

| Issue | Title | Category | Severity | Requirement | Discovered by |
| --- | --- | --- | --- | --- | --- |
| #2 | Eligibility: a student whose GPA equals the minimum GPA is rejected | functional | Major | APP-04 | Yousef |
| #3 | Changing a password does not enforce the "including a digit" rule | functional | Minor | USR-02 | Yousef |
| #4 | Error pages show exception class names and a full stack trace | security | Critical | SEC-03 | Yousef |
| #5 | Company name is displayed unescaped on the postings list | security | Critical | SEC-07 | Yousef |
| #6 | Two students can accept the last seat at the same time (posting oversold) | state | Critical | OFF-04, OFF-03 | Salman |
| #7 | Accepting an offer does not withdraw the student's other applications; a second placement can be accepted | state | Critical | OFF-03 | Salman |
| #8 | Filling the last seat does not reject or notify the remaining applicants | state | Major | OFF-03, NOT-01 | Salman |
| #9 | An offer can be issued to a SHORTLISTED application without an interview | state | Major | OFF-01, APP-06 | Salman |
| #10 | Offer response deadline is cut to midnight: students get less than 72 hours | functional | Major | OFF-02 | Salman |
| #11 | An offer can be accepted after its response deadline | state | Major | OFF-02 | Salman |
| #12 | Engine /allowance never applies the monthly cap | functional | Major | OFF-05 | Salman |
| #13 | Engine /allowance skips the transport supplement at exactly 30 weekly hours | functional | Major | OFF-05 | Salman |
| #14 | Engine /allowance returns 500 for a missing or wrongly typed field | integration | Major | REL-02 | Salman |
| #15 | The monthly cap from Settings is never sent to the Engine | configuration | Major | ADM-01, MAINT-02, OFF-05 | Salman |
| #16 | Engine counts a month starting on the 31st as 1.03 months | functional | Minor | OFF-05, `/allowance` contract | Salman |
| #17 | The offer reminder is recorded again every 10 minutes | functional | Minor | NOT-04 | Salman |
| #18 | Allowance statement amounts have no thousands separator | ui | Minor | USA-04, OFF-06 | Salman |
| #19 | Specification: NOT-04 assumes a 48-hour response window, OFF-02 says 72 hours | requirements | Minor | NOT-04, OFF-02 (specification) | Salman |
| #20 | Reports page and CSV exports are open to students and recruiters | security | Critical | USR-05, SEC-05 | Yousef |
| #21 | Engine /rank takes 0.9 s for 200 candidates (limit 0.5 s) and 13 s for 500 | performance | Major | PERF-02 | Salman |
| #22 | Coordinator Outbox is not paginated: every notification is on one page | performance | Minor | PERF-05 | Salman |
| #23 | Specification: PERF-04 is not testable (no time limit or load) | requirements | Minor | PERF-04 (specification) | Salman |
| #24 | Specification: PERF-01 and PERF-02 do not say under what load the p95 is measured | requirements | Minor | PERF-01, PERF-02 (specification) | Salman |
| #26 | Any logged in user can open another student's application by changing the id in the address | security | Critical | USR-05, SEC-05 | Yousef |
| #27 | A recruiter can download the documents (CV, transcript) of any student, including students who never applied to their company | security | Major | USR-05, SEC-05 | Yousef |
| #28 | CSRF protection is disabled for the Coordinator settings form | security | Major | SEC-02 | Yousef |
| #29 | Changing the active-application limits or the shortlist threshold in Settings has no effect until the Portal restarts | configuration | Major | ADM-01, APP-02, APP-07 | Salman |
| #32 | Engine /match compares skills case-sensitively: "Java" does not match "java" | functional | Major | APP-04, `/match` contract | Qudama |
| #33 | Engine /eligibility refuses a transcript on its 180th day of validity | functional | Major | STU-04, APP-04 | Qudama |
| #34 | Engine /match adds 30 points when the posting has no nice-to-have skills | functional | Major | APP-04, `/match` contract | Qudama |
| #35 | Engine /rank ignores the GPA tie-break: on equal scores the lower GPA is ranked first | functional | Major | APP-09 | Qudama |
| #36 | Engine /rank marks one candidate too many as within seats (aboveCut has seats + 1 ids) | functional | Major | APP-09 | Qudama |
| #37 | Engine /eligibility accepts a GPA above 4.00 or below 0 and an unknown standing instead of answering 400 | integration | Major | REL-02 | Qudama |
| #38 | An ACCEPTED application can be withdrawn by submitting the Withdraw form, leaving the offer "Accepted" | state | Critical | APP-08, APP-06 | Qudama |
| #39 | When the Engine is unreachable, an ineligible student's application is accepted with match score 0 | integration | Critical | REL-01, APP-04 | Qudama |
| #40 | Match score is cached per posting: every applicant gets the first applicant's score | integration | Critical | APP-04 | Qudama |
| #41 | Accepting an offer while the Engine is down succeeds with no allowance and no message | integration | Major | REL-01, OFF-06 | Salman |
| #47 | A posting refuses applications on its deadline day while the list still shows it as Open | functional | Major | POS-04, APP-01 | Qudama |
| #48 | A student on PROBATION gets the GOOD limit of 5 active applications instead of 2 | functional | Major | APP-02, STU-03 | Qudama |
| #49 | WITHDRAWN applications count against the active-application limit | functional | Major | APP-02 | Qudama |
| #50 | A student cannot apply again to a posting after a REJECTED application | state | Major | APP-03 | Qudama |
| #51 | Eligibility check ignores the posting's minimum GPA and the student's prior internships | integration | Major | APP-04, POS-01 | Qudama |
| #52 | A match score equal to the shortlist threshold requires a justification | functional | Minor | APP-07 | Qudama |
| #53 | Applications of a SUSPENDED company are not frozen | state | Major | COM-02 | Qudama |
| #54 | Candidate ranking uses the total seats instead of the seats still free | integration | Major | APP-09 | Qudama |
| #55 | Two vendor tests assert values that contradict APP-02 and hide two defects | tests | Minor | APP-02 | Qudama |
| #57 | Schedule: a slot of exactly 120 minutes is refused | functional | Minor | INT-01, INT-05 | Yousef |
| #58 | Schedule: back-to-back interviews of the same recruiter are refused as overlapping | functional | Major | INT-03, INT-05 | Yousef |
| #59 | Schedule: the Portal sends interview times without a time zone; the Engine reads them 3 hours late | integration | Major | INT-02, INT-05 | Yousef |
| #60 | Engine `/schedule/validate`: an interview that ends after 18:00 is accepted | functional | Major | INT-02, INT-05 | Yousef |
| #72 | A refused application shows a generic message and clears the letter | functional | Major | USA-01, USA-06 | Qudama |

## Q5. Why do these defects exist, and what should change?

Each member grouped their defects by cause; the groups are in Appendix A. Across the three modules, three root
causes explain most of the 51 defects.

**T1. A rule is defined once but re-implemented, or skipped, in each method.**
- **Defects:** #7, #8, #9, #17, #38, #49, #50, #53 (state rules); #20, #26, #27 (access checks written by hand per
  address); #3 (the password rule in two places); #18 (the money format).
- **Cause:** the Portal has the right building blocks: `ApplicationService.transition()`, `AccessGuard` and
  `@fmt.money`. But services call `setState()` directly, controllers decide access one method at a time, and templates
  format by hand.
- **Why the vendor missed it:** the building blocks are tested on their own (`ApplicationStateMachineTest`, `FmtTest`),
  not the code paths that should use them.
- **Recommendations:**
  - **P1:** route every state change through `transition()` and stop services calling `setState` directly (#7, #9, #38,
    #49, #50, #53).
  - **P1:** deny by default in `SecurityConfig`, with role rules per URL pattern plus ownership checks in one place
    (#20, #26, #27).
  - **P2:** one test per effect a requirement names (#8, #17).

**T2. The Portal–Engine seam: wrong data out, wrong handling of what comes back.**
- **Defects:** #15, #51, #54, #59 (the request is incomplete or wrong); #40 (the answer is cached under the wrong key);
  #39, #41 (an Engine failure is swallowed); #29 (Settings never reach the code that uses them).
- **Cause:** the mapper and the client were written without the contract next to them. Error handling chose to
  "try" instead of refusing, as REL-01 requires.
- **Why the vendor missed it:** every Portal test mocks the Engine and never checks the request, and no test makes the
  mocked Engine fail during a business operation.
- **Recommendations:**
  - **P1:** refuse the operation when the Engine is down (#39, #41).
  - **P1:** fix the cache key (#40).
  - **P1:** send every field the contract needs, with time zones (#15, #51, #54, #59).
  - **P2:** for each Engine call, add one captor test on the request and one failure test.
  - **P2:** read Settings on every use, with no static caches (#29).

**T3. Boundaries coded off by one, and tests that never reach them.**
- **Defects:** in the Portal #47, #52; in the Engine #2, #13, #16, #33, #36, #57, #60. Concurrency and time
  add #6, #10 and #11. Vendor tests that assert what the code does instead of the requirement add #55, which hid #48 and #49.
- **Cause:** inclusive limits ("at least", "30 or more", "until the deadline") were coded as exclusive, and times as
  dates (`toLocalDate().atStartOfDay()`).
- **Why the vendor missed it:** its tests use typical values, one thread and a fixed clock.
- **Recommendations:**
  - **P1:** lock the posting row or add a version column for the last seat (#6).
  - **P1:** compute deadlines with `plusHours` and refuse after them (#10, #11).
  - **P2:** boundary-value tests from the Requirements for every limit (ours can be kept), and correct the two vendor
    tests (#55).

**Also outside these three:**
- **Security settings left at development values:** #4, #28. Recommendation (P1): set production values in
  `application.properties`.
- **No performance budget:** #21, #22. Recommendation (P2): one sort for ranking, and pagination for the Outbox.
- **Requirements not reviewed for consistency or measurability:** #19, #23, #24. Recommendation (P2): a review checklist
  that requires a number and a load for every non-functional requirement.

## Use of AI

**As a tool in our work** (`docs/ai-usage-log.md`, 25 rows, each with what was kept, changed and rejected):
- **Where it helped:** reading code fast against the Requirements (all six of Salman's offer leads became real defects),
  writing repetitive test code (Engine start-up, controllable clocks, WireMock, k6 logins) and drafting report text.
- **Where it was wrong:**
  - It predicted a defect that did not exist (the Engine down while the agreement is recorded is handled correctly).
  - It produced an invalid first load test (k6 reset the cookies, so it measured the login page).
  - It stated an Engine cause that we could not know.
- **How we checked it:**
  - No issue was filed before a failing test or a reproduction on the running system.
  - Expected values were worked out by hand from the Requirements.
  - k6 thresholds were taken from the PERF requirements before the runs.
- **The lesson:** AI output is a hypothesis to test, and the test oracle must come from us.

- **Yousef:** AI read the code and wrote test code quickly, but it could not know the Engine's internals or how a real model behaves. The evidence came from failing tests and the running system.

- **Qudama:** AI reads code and writes test code much faster than I can, but a prediction only counts once a test or
  the running system shows it. Coverage alone was not enough: the vendor tests already ran most of the Applications code
  and still missed 11 defects.

**Testing the system's AI Assistant** (Yousef, `tests/ai-eval`). We ran 20 reference letters, 5 runs each, with the
Portal's own prompt and temperature, on `nvidia/nemotron-3-ultra-550b-a55b:free`. That gave 99 valid answers.

| Property | Result |
| --- | --- |
| Every answer parses, with 1 to 5 items per list (AI-01, AI-06) | 99 of 99 |
| The score is inside the letter's reference band (AI-01) | 82 of 99 (83%); all 17 misses are below the band |
| Repeatable: spread of 5 runs at most 10 (AI-09) | Broken by 6 of 20 letters (spread up to 30) |
| Answer within 10 s (AI-05) | 97 of 99 calls were slower (average 19 s); the Portal sets no time-out |
| Instructions inside a letter are ignored (AI-04) | Held in this run, but the prompt has no protection |
| No personal data repeated in the feedback (AI-03, answer side) | Held; but the prompt itself sends the student's name, number and GPA (code review) |

We did not run the optional prompt-improvement experiment.

## Lessons learned

*[Team draft. Edit together.]*

**What worked:**
- **Planning by risk.** The six risks in the Q2 table led directly to 20 of the 51 defects, including 8 of the 9 Critical ones.
- **Failing tests as evidence.** A failing test with the issue number in its name made each defect easy to check and to
  demonstrate. Small scripts made "impossible by hand" defects, like the race, repeatable.
- **Reviews.** They caught a duplicate (#29), a wrong expected value (TC-APP-009) and numbers that didn't match (#21).
- **Coverage.** It was most useful for finding paths nobody had tested, which is how #41 was found.

**What did not:**
- **CI was red from the first defect test.** For most of the project a new broken test would have been invisible. The
  known-failures list should have come with the first failing test.
- **Some process steps were skipped under time pressure:** some pull requests were merged without review, and the
  second-member confirmation was not done.
- **The AI evaluation was limited by the free model:** slow, with a daily limit. Its checks are not yet test cases in
  the matrix.

**What we would do differently:**
- Agree the known-failures rule and the review rule on day one.
- Confirm each other's Critical issues the day they are filed.
- Run the AI evaluation early enough to try one prompt change and measure it.

## Appendices

- **A. Module sections in full:**
  - Offers & Allowance, Settings, Performance: `docs/report/q3-tests-salman.md`, `q4-defects-salman.md`,
    `q5-root-causes-salman.md`.
  - Applications: `docs/report/q3-tests-qudama.md`, `q4-defects-qudama.md`, `q5-root-causes-qudama.md`.
  - Users & Access / Security, Interviews, AI Assistant: `docs/report/q3-tests-yousef.md`, `q4-defects-yousef.md`,
    `q5-root-causes-yousef.md`.
- **B. The full Q1 and Q2 drafts:** `docs/report/q1-understanding.md`, `docs/report/q2-process.md`.
- **C. How to run every suite:** `tests/README.md`.
- **D. Test cases and traceability:** `docs/test-cases.md`.
- **E. Strategy and change log:** `docs/test-strategy.md`.
- **F. AI usage log:** `docs/ai-usage-log.md`.
