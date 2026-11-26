# Q2. What process did we define, and did we follow it?

> Team draft by Salman, from `docs/test-strategy.md`, the GitHub history and the issues.
> **Before the PDF, the team must:**
> - Yousef: add the AI evaluation numbers (row "AI evaluation" and the "Use of AI" section).
> - Qudama: check the CI paragraph and add the known-failures list.
> - Decide the open point in "Defect workflow" (the second-member confirmation).
> - Fix the labels on #3 (no priority) and #28 (no labels at all), and assign Yousef's issues to Yousef. The template
>   requires both.

The full strategy, with its dated change log, is in `docs/test-strategy.md`.

## Risks and scope

We planned by risk: impact × likelihood, each scored H, M or L. The strategy lists twelve risks. The ones that drove
the most effort were:
- **Access to other users' data (R1).** Ownership checks are called by hand in each method.
- **A wrong allowance (R2).** A complex formula decides the money.
- **Illegal state transitions (R3)** and **a race on the last seat (R4).** Concurrency is rarely covered by vendor tests.
- **The Portal treating an Engine failure as success (R5).**
- **Security misconfiguration (R6).** We saw stack traces, and CSRF is disabled for one form.
- **The specification itself (R12).**

These risks are where most of our defects came from:
- R1 and R6 led to #4, #5, #20, #26, #27 and #28.
- R2 led to #12, #13, #15 and #16.
- R3 and R4 led to #6, #7, #9, #38 and #53.
- R5 led to #39 and #41.
- R12 led to #19, #23 and #24.

**In scope:** every Portal function, all three roles, all five Engine endpoints, the AI Assistant, the vendor tests and
the Requirements. **Out of scope or limited:** other operating systems (COMP-01), real e-mail delivery (we checked the
Outbox) and the Engine's internal code.

**Primary modules:**

| Module | Owner | Why |
| --- | --- | --- |
| Offers & Allowance | Salman | Money and seats; spans the Portal and the Engine |
| Applications | Qudama | The core flow; the largest state machine |
| Users & Access / Security | Yousef | Security defects are Critical by definition |

Each module has at least 10 designed test cases, as required: OFF 31, APP 30 and SEC 19.

## Levels, techniques and tools, and why

| Level | Tool | Where | Why | What we ran |
| --- | --- | --- | --- | --- |
| Unit and integration (white-box) | JUnit 5, Mockito, Spring Boot Test, WireMock, JaCoCo | `portal/src/test` | Reuses the vendor setup; JaCoCo shows the code we did not reach | 92 team tests in 10 classes, next to the 147 vendor tests |
| API (black-box) | JUnit 5 with Java `HttpClient`; the tests start the Engine jar themselves | `tests/api` | Runs with the Maven wrapper we already had, so nothing to install | `AllowanceApiTest` (30), `ApplicationsApiTest` (34) |
| System and exploratory | Browser, curl, session charters | issues, `tests/repro` | Finds what scripted tests miss; a script reproduces the last-seat race | each system defect reproduced after `--reset` before filing |
| Performance | k6 v2.4.0 | `tests/performance` | One executable, no admin rights; thresholds are the PERF limits | 3 scripts: 7 test cases, 4 pass, 2 fail, 1 blocked |
| AI evaluation | `AiEval.java`, a Java single-file program | `tests/ai-eval` | Repeated runs on the `ai-eval` letters against properties that must always hold | *[Yousef: runs, model, results]* |

**Alternatives we rejected:**
- **Postman/Newman:** it needs Node.js, and its tests are harder to review in a pull request.
- **JMeter:** kept as a fallback for k6, but not needed.
- **Playwright:** left out, because the system tests ran by hand and with scripts.

**Techniques used** (they are counted per module in Q3):
- **Black-box:** boundary value analysis, equivalence partitioning, decision tables, state transition tests,
  error guessing, concurrency tests, exploratory charters, load and volume tests, and requirements review.
- **White-box:** statement and branch coverage, data-flow review (how the Portal builds each Engine request), and
  code review.

## Roles

| Member | Primary module | Engine endpoints | Cross-cutting role |
| --- | --- | --- | --- |
| Salman | Offers & Allowance | `/allowance` | Performance (k6); wrote the strategy |
| Qudama | Applications | `/eligibility`, `/match`, `/rank` | Git workflow and CI |
| Yousef | Users & Access / Security | `/schedule/validate` | AI Assistant evaluation; repository owner |

## How we worked in Git

- **Branches and pull requests.** Work happened on `<member>/<topic>` branches (for example `salman/off-tests` and
  `qudama/app-automated-tests`) and reached `main` only through pull requests. We merged 10 pull requests:
  - 8 had at least one teammate's approval before the merge.
  - #31 was merged after a review comment but before its points were fixed. The fixes came in #45.
  - #43 and #44 were merged without a review.

  Reviews found real problems. For example, the review of #31 checked eight code-review predictions against the source,
  found that the shortlist threshold is cached like the limits (so #29 was widened instead of filing a duplicate), and
  found a mismatch in TC-APP-009.
- **CI.** The *Tests* workflow runs the Portal suite on every push and pull request. It has been red since the first
  defect tests were merged, on purpose: each failing test exposes a filed defect (on `main`: 239 tests, 35 deliberate
  failures). So we changed the rule from "no new failures" to "no failing test that is not on the known-failures list"
  (change log, 26/11).
- **Templates and conventions:**
  - The issue template is `.github/ISSUE_TEMPLATE/defect.md`, and the labels come from `.github/labels.json`.
  - Test case IDs are `TC-<MODULE>-<NNN>`, and every automated test names its test case and requirement in
    `@DisplayName`.
  - Commit messages are in the imperative and name the test case or issue.
  - The AI usage log has one row per meaningful use.
- **Defect workflow:**
  1. Every system defect was reproduced after `.\start.cmd --reset` before it was filed, with steps that use relative
     dates.
  2. Each issue has one category, one severity and one priority label, and the member who found it as assignee.
  3. Where possible, a failing automated test is linked.

  *[Open point: the strategy also asks a second member to confirm each defect with a "Reproduced" comment. Either we do
  it for the Critical and Major issues before the deadline, or we record here why it was replaced by pull-request
  review.]*

## What we changed along the way, and why

The strategy's change log (section 8) records 9 dated changes after the first draft. The most important ones:
- **New level and new evidence:**
  - Reproduction scripts were added for defects a person can't trigger by hand (22/11).
  - A failing automated test is accepted as evidence when the running system hides the defect behind another one (22/11).
- **Measurement choices:** PERF-02 is measured with 1 user by default, because the requirement names no load (23/11).
  We reported that gap as a specification defect (#24).
- **Wider scope where the code led us:**
  - Settings (ADM-01) became a tested module after a code review found cached values (24/11).
  - JaCoCo gaps were used to design new tests, which found #41 (26/11).
- **Rules that keep the issues clean:**
  - A finding with the same root cause widens the existing issue instead of creating a duplicate (26/11).
  - A *Not run* test case may hold a code-review prediction, which must be confirmed before filing (24/11).
  - The CI rule now uses a known-failures list (26/11).
