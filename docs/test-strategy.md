# Test strategy

UniPlace acceptance testing · INFS4202 Fall 2026 · Team: Salman, Qudama, Yousef

| Version | Date | Status |
| --- | --- | --- |
| 0.1 | 21/11/2026 | Draft for M1, to be reviewed by the whole team |

## 1. System under test (our model)

```
 Browser (3 roles)
      │  HTML forms, Thymeleaf pages
      ▼
 ┌──────────────────────────── Portal :8080 (white-box) ────────────────────────────┐
 │ web/        controllers (Account, Application, Posting, Student, Company,       │
 │             Coordinator, Settings) + WebExceptionHandler                         │
 │ security/   Spring Security config, login attempts, current user                 │
 │ service/    business rules: ApplicationService + ApplicationStateMachine,        │
 │             OfferService, InterviewService, PostingService, AccessGuard,         │
 │             SettingsService, NotificationService, ReportService, ...             │
 │ engine/     EngineClient, EngineRequestMapper, MatchScoreService ───────────┐    │
 │ ai/         AiAssistantService, PromptBuilder, AiClient, AiResponseParser,  │    │
 │             FeedbackCache, SimulatedProvider ─────────────┐                 │    │
 │ repository/ Spring Data JPA ──► H2 file DB (portal/data)  │                 │    │
 └───────────────────────────────────────────────────────────┼─────────────────┼────┘
                                                             ▼                 ▼
                                      AI provider (OpenRouter / simulated)   Engine :8081 (black-box)
                                                                             /eligibility /match /rank
                                                                             /schedule/validate /allowance
```

- **Portal** owns the UI, the business rules, the database and access control. About 3,300 lines in
  `service/`, `engine/` and `ai/`, with a vendor suite of 25 test classes.
- **Engine** is stateless. It gets everything it needs in each request, and the Portal sends the Settings values
  (limits, supplement, cap, penalty) with every call. Engine bugs therefore show up in the Portal, and Portal bugs
  can hide behind a correct Engine. The integration point is in `engine/EngineRequestMapper` and `engine/EngineClient`.
- **AI Assistant** builds a prompt (`PromptBuilder`), calls a provider or the simulator, then parses (`AiResponseParser`) and
  caches (`FeedbackCache`) the result.
- **Main flows:** a student applies (Engine eligibility check, then the Engine match score); a recruiter shortlists, then
  schedules an interview (Engine slot validation), then issues an offer; the student accepts the offer (seats are taken,
  other applications are withdrawn, the posting may become FILLED); the Coordinator records the agreement date, which
  triggers an Engine allowance recalculation. Notifications are written to the Outbox at each step.

How we built this model: reading the Requirements and `engine/openapi.yaml`, reading the Portal source package by package,
running the system in all three roles, and probing the Engine with curl.

## 2. Risks and scope

We spend most effort where a defect would hurt most (impact) and where one is most likely (likelihood). Each is scored H, M or L.

| # | Risk | Requirements | Impact | Likelihood | Why we think it is likely |
| --- | --- | --- | --- | --- | --- |
| R1 | A user reads or changes another user's or another company's data | USR-05, SEC-05 | H | H | Ownership checks (`AccessGuard.assertRecruiterOwns`, `assertStudentOwns`) are called by hand in individual controller and service methods, so a single missed call is enough |
| R2 | Wrong allowance amount (money) | OFF-05, OFF-07 | H | H | Complex formula: partial months, a supplement threshold, a cap, a penalty per started week and a penalty cap |
| R3 | Illegal application state transition, especially through a direct URL | APP-06, APP-08 | H | M | State is changed by several services (application, offer, interview) |
| R4 | Seats oversold, or a race on the last seat | OFF-03, OFF-04, POS-05 | H | M | Concurrency is rarely covered by vendor tests |
| R5 | The Portal treats Engine failure as success | REL-01 | H | H | In `EngineClient`, when the Engine is unreachable, `checkEligibility` returns "eligible" and `match` returns a score of 0, instead of refusing the operation |
| R6 | Security misconfiguration (CSRF, error details, XSS, secrets) | SEC-02, SEC-03, SEC-04, SEC-07 | H | H | `application.properties` sets `include-stacktrace=always`, and CSRF is disabled for `/coordinator/settings/**` |
| R7 | Application limits or duplicate applications bypassed | APP-02, APP-03, APP-05 | M | H | Double submit and refresh are rarely handled |
| R8 | Time rules off by one (deadline, 72 h, 180 days, interview hours) | POS-04, OFF-02, STU-04, INT-02 | M | H | Time zones (UTC vs. Qatar) and inclusive boundaries |
| R9 | The AI leaks personal data or follows prompt injection | AI-03, AI-04, AI-10 | H | M | Letters are free text sent to an LLM |
| R10 | Hard-coded thresholds instead of Settings | MAINT-02, ADM-01 | M | M | Easy to miss in code review |
| R11 | Slow list pages under volume | PERF-01..05 | M | M | No pagination, or N+1 queries |
| R12 | The specification itself is contradictory or untestable | all | M | H | Already seen: NOT-04 says 48 h but OFF-02 says 72 h; POS-04 vs POS-07; PERF-04 has no measurable target |

The code hotspots in R5 and R6 come from reading the source. They are leads to verify on the running system, not defects
yet.

**In scope:** every Portal function, all three roles, all five Engine endpoints, the AI Assistant, the vendor test suite, and the
Requirements document itself.

**Out of scope or limited:** COMP-01 on macOS and Linux (we test Chrome and Firefox on Windows only); real SMTP
delivery (we check the Outbox and the logs only, NOT-05); the Engine's internal code (we have no source).

## 3. Primary modules (three) and why

| Module | Owner | Requirements | Why this one |
| --- | --- | --- | --- |
| **Offers & Allowance** | Salman | OFF-01..07 (+ Engine `/allowance`) | Money and seats: highest impact (R2, R4); spans Portal and Engine, so both white-box and black-box apply |
| **Applications** | Qudama | APP-01..10 (+ Engine `/eligibility`, `/match`, `/rank`) | The core flow every other module depends on; a large state machine (R3, R7) |
| **Users & Access / Security** | Yousef | USR-01..05, SEC-01..07 | Security defects are Critical by definition, and access control cuts across every screen (R1, R6) |

Each module gets at least 10 designed test cases in `docs/test-cases.md`, mixing functional and non-functional tests and
black-box and white-box techniques, and each one is traced to a requirement ID. Secondary modules (POS, INT, STU, COM, NOT, ADM, AI)
are tested by risk, with fewer designed cases. The owners are listed in Section 5.

## 4. Test levels, techniques and tools, and why

### Levels

| Level | Target | Tool | Where | Why |
| --- | --- | --- | --- | --- |
| Unit (white-box) | Portal services and the state machine | JUnit 5, Mockito (already in the vendor suite) | `portal/src/test` | Reuses the vendor setup; JaCoCo gives branch coverage of exactly the code we test |
| Integration | Portal with H2 and a stubbed Engine | Spring Boot Test (`support/IntegrationTest`) | `portal/src/test` | Checks transactions, repositories and access control together |
| API (black-box) | Engine endpoints | JUnit 5 + Java `HttpClient`, in a small Maven project | `tests/api` | Runs with the Maven wrapper we already have, so nothing extra to install; easy to run in CI |
| System / exploratory | Portal in the browser, all roles | Manual, session-based charters; browser DevTools | Charters in `docs/` | Finds UI, usability and integration defects that scripted tests miss |
| Performance | List pages, Engine, concurrent users | k6 (single executable, no admin rights) | `tests/performance` | Scriptable, gives the p95 numbers PERF-01..03 ask for |
| AI evaluation | AI Assistant | Java single-file program (`java AiEval.java`) on `ai-eval/letters.json` | `tests/ai-eval` | Repeated runs and checks of properties that must always hold; the JDK is all it needs |

**Alternatives considered:**
- **Postman/Newman for the Engine.** Rejected: it needs Node.js, and its tests are harder to review in pull requests.
- **JMeter instead of k6.** Kept as a fallback if k6 is blocked on lab machines.
- **Playwright for end-to-end tests.** Optional, only if time allows after M2.

### Techniques per primary module

| Module | Black-box | White-box |
| --- | --- | --- |
| Offers & Allowance | Boundary value analysis (BVA) on allowance (0, 5,000), weekly hours (29/30), the cap and penalty weeks (0, 1, 4, 5 started weeks; 20% cap); a decision table for the allowance lines; state transitions for offers (ISSUED, ACCEPTED, DECLINED, EXPIRED); a concurrency test on the last seat; time-based tests at 72 h | Branch coverage of `OfferService`; a review of rounding and month counting in `EngineRequestMapper` |
| Applications | Equivalence partitioning (EP) and BVA on letter length (99, 100, 2,000, 2,001) and the active limits (5/6 GOOD, 2/3 PROBATION); a full APP-06 transition matrix, including invalid transitions through direct URLs; a decision table for eligibility; an idempotency test (double submit, refresh) | Branch coverage of `ApplicationStateMachine` and `ApplicationService`; data flow of the match score from the Engine to the stored application |
| Users & Access / Security | A role × resource access matrix (direct URLs and changed IDs, i.e. IDOR); BVA on password length (9/10) and lockout attempts (4/5/6); session expiry; CSRF, XSS and file upload payloads; error page content | A review of `SecurityConfig`, `AccessGuard` and `LoginAttemptService`; checks that the code paths keep secrets out of logs and notifications |

**Cross-cutting techniques:**
- **Requirements review.** We look for contradictions, gaps and untestable statements, and report them with the `requirements` label.
- **Vendor test review.** We run `mvnw test` and examine each failure and each weak oracle (`tests` label).
- **Engine contract testing.** We compare each Engine response against `openapi.yaml`.

### Test environment

- **Machines:** Windows 11 with JDK 17 or newer, Chrome and Firefox (current versions).
- **System:** started with `.\start.cmd`, and with `.\start.cmd --reset` before reproducing any defect.
- **AI:** OpenRouter free models, one key per member. We record the model in the AI usage log.
- **Dates:** the demonstration data is placed around the first start, so tests and issues describe dates relatively
  (for example "a posting whose deadline is today").

## 5. Team organisation and Git workflow

### Roles

| Member | Primary module | Engine endpoints | Cross-cutting role |
| --- | --- | --- | --- |
| Salman | Offers & Allowance | `/allowance` | Performance testing (k6) |
| Qudama | Applications | `/eligibility`, `/match`, `/rank` | Git workflow and CI owner, reviews merge order |
| Yousef | Users & Access / Security | `/schedule/validate` (Interviews) | AI Assistant evaluation; repository owner (collaborators, settings) |

Everyone also explores the secondary modules, files every defect they find, and reviews the others' pull requests.

### Git workflow

- `main` always builds. Nobody pushes directly to `main`.
- Work happens on a branch named `<member>/<topic>`, for example `salman/off-allowance-tests`. Each branch is merged through
  a pull request that one teammate reviews. The *Tests* workflow runs on every push and pull request. The vendor suite
  may already fail on `main`; the rule is **no new failures**.
- **Commit messages:** imperative, with the test case or issue they relate to, for example
  `Add BVA tests for allowance cap (TC-OFF-004)` or `Add regression test for #12`.
- **Test case IDs:** `TC-<MODULE>-<NNN>`, where MODULE is OFF, APP, SEC, INT and so on. Every automated test names its test case and requirement
  in `@DisplayName`, for example `@DisplayName("TC-OFF-004 / OFF-05: monthly total is capped at 5,000")`.
- **Tag `final`** on `main` before the M3 deadline.

### Defect workflow

1. Reproduce the defect on a fresh start (`.\start.cmd --reset`) before filing.
2. File it the same day with the *Defect report* template. Add exactly one category label, one severity label and one priority label,
   and assign it to whoever found it. Quote the requirement ID.
3. A second member confirms the defect within 2 days by reproducing it and commenting "Reproduced". Severity is agreed then.
4. A defect that turns out to be a duplicate or not a defect is closed with a comment and the `duplicate` or `invalid` label.
5. Where possible, add an automated test that fails because of the defect, and link it in the issue.

### AI use

- We use AI to understand code, generate test ideas, data and scripts, and draft documents.
- Every output we keep is checked against the Requirements and the running system, then recorded in
  `docs/ai-usage-log.md` with what was kept, changed or rejected.

## 6. Entry and exit criteria, measures

**Entry:**
- The system starts with `.\start.cmd`.
- The vendor suite has been run once, and its results and coverage have been recorded as our baseline.

**Exit (for M3):**
- **Test cases:** at least 10 for each primary module, all executed.
- **Automated tests:** at least 2 levels, white-box Portal and black-box Engine, runnable with the commands in `tests/README.md`.
- **Defects:** at least 10 reproduced defects in GitHub Issues.
- **AI Assistant:** tested with repeated runs.
- **Report:** final report written.

**Measures we report:**
- Test cases per module, by technique and by type.
- Pass and fail counts per suite.
- JaCoCo line and branch coverage of `service/` (MAINT-01 asks for 70%).
- Defects by category and severity, and the number of duplicate and invalid reports.
- Engine p95 and page p95 response times.

## 7. Schedule

| When | Goal |
| --- | --- |
| Week 12 (now) | Everyone sets up and explores all roles; baseline run of the vendor suite; strategy draft |
| Week 13, M1 | Present this strategy; test cases for the primary modules started; first Engine API tests |
| Week 14, M2 | Test cases complete; unit and API suites running in CI; first defects filed; AI log and AI runs started |
| Week 15, M3 | Defect hunting, performance and AI evaluation results, root-cause analysis, final report, tag `final`, presentation |

## 8. Changes along the way

| Date | What changed | Why |
| --- | --- | --- |
| 21/11/2026 | First draft (v0.1) | Preparation for M1 |
| 22/11/2026 | Added a reproduction level: scripts in `tests/repro/` that drive the running system with curl (first: `off-04-last-seat-race.sh`) | A race between two students accepting the last seat cannot be produced by hand in a browser (#6) |
| 22/11/2026 | Defect workflow: a defect may be filed with a failing automated test as its evidence when the running system cannot show it | #15 is hidden on the system by #12 (the Engine ignores the cap anyway); #11 needed a 72-hour wait. #11 was later reproduced on the system (24/11) |
| 22/11/2026 | Offers & Allowance white-box: a data-flow review of `EngineRequestMapper.toAllowanceRequest` instead of "a review of rounding and month counting" | The mapper does no rounding or month counting (the Engine does); the data-flow review found that `monthlyCap` is never sent (#15) |
| 23/11/2026 | Performance: PERF-02 is measured with 1 user by default and 10 users as an option (`-e VUS=10`) | PERF-02 names no load (reported as #24); 1 user is the most generous reading, and `/rank` fails even then (#21) |
| 24/11/2026 | Settings (ADM-01) tested as a secondary module with its own test cases (TC-ADM-001, later TC-ADM-002) | A code review of `SettingsService` found that the active-application limits are cached until a restart (#29) |
| 26/11/2026 | Defect workflow: a new finding with the same root cause as a filed issue widens that issue instead of creating a new one | The shortlist threshold is cached in the same way as the limits (predicted by TC-APP-030); duplicates count once, so #29 now covers both |
| 26/11/2026 | Techniques: JaCoCo coverage gaps are used to design new tests | The only uncovered error path in `OfferService` (`tryComputeAllowance`) led to TC-OFF-031, which found #41 |
