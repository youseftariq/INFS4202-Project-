# AI usage log

One entry per meaningful use.

| Date | Member | Tool and model | Purpose | Prompt summary | Kept | Modified (how) | Rejected (why) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-23 | Yousef | Claude (claude.ai chat) | Write white-box and regression tests (6 JUnit classes) | Asked for tests for LoginAttemptService, UserService, AccessGuard, and for the security defects | Tests that compiled and ran: 45 new, 10 fail on purpose (defect tests) | Fixed the test-case numbering to match my matrix | None |
| 26/11 | Yousef | Claude (Sonnet 5.5) | Interviews: design TC-INT-001 to 012 and `ScheduleApiTest` for `/schedule/validate` | Asked for test cases and API tests for INT-01 to INT-05 from the Requirements and the OpenAPI text (boundaries, overlap, working days, time zone) | 12 test cases, 13 API tests, four defects (#57 to #60) | Requirement IDs corrected to INT-01 and INT-03 in the tests and the matrix; the new rows were inserted into the current `main` file (PR #61) | An older version of the interview cases, replaced by the corrected one |
| 24/11 and 26/11 | Yousef | Claude (Sonnet 5.5) | AI Assistant evaluation: `AiEval.java` and 20 reference letters with bands | Asked for a script that sends the Portal's prompt and temperature to a provider, 5 runs per letter, with checks for AI-01, 03, 04, 05, 06 and 09 | The script, `letters.json`, 99 valid answers and the summary | Script, summary and README corrected before the merge (PR #62) | The optional low-temperature experiment, not run |
| 26/11 | Yousef | Claude (Sonnet 5.5) | Review the Portal's AI code against AI-03 to AI-11 | Asked it to read `PromptBuilder`, `AiClient` and `AiResponseParser` against the requirements | Six findings: personal data in the prompt, internal note, contradictory instructions, no time-out, incomplete validation, score spread | Each finding labelled with how it was found and whether it was reproduced | The expectation that letters with instructions would raise the score: the run showed the model ignored them, so it is a risk, not a defect |
| 21/11 | Salman | Claude Code (Claude Opus 5.5) | Draft the M1 test strategy | Read the project documents and the Portal source; propose risks, primary modules, levels, techniques, tools, roles and Git workflow | Risk analysis, the three primary modules, test levels, techniques, tools and Git workflow as the base of `docs/test-strategy.md` | The team discussed Claude's proposal together and assigned the roles and modules based on each member's skills: Salman, Offers & Allowance + performance; Qudama, Applications + Git/CI; Yousef, Users & Access / Security + AI Assistant | Claude's suggested role assignment, replaced by the team's own decision |
| 22/11 | Salman | Claude Code (Claude Opus 5.5) | Find likely defects in the offer code | Review `OfferService` and `EngineRequestMapper` against OFF-01..07 and NOT-04 | Leads on deadline, auto-withdraw, state guard, seat locking, reminders, monthly cap | Every lead verified with a failing test and on the running system before it was reported | none |
| 22/11 | Salman | Claude Code (Claude Opus 5.5) | Engine `/allowance` API tests | Generate a JUnit project that starts the Engine and checks `/allowance` against OFF-05 and the OpenAPI rules (boundaries, cap, penalty, rounding, invalid input) | Test project, Engine start-up helper, 30 tests | Expected values checked by hand against OFF-05 | none |
| 22/11 | Salman | Claude Code (Claude Opus 5.5) | Portal white-box tests for offers | Generate integration tests with a controllable clock and a two-thread test for OFF-02, OFF-03, OFF-04, NOT-04, ADM-01 | 8 tests in `OfferRulesTest` | Each failure checked against the code and reproduced in the browser where possible | none |
| 22/11 | Salman | Claude Code (Claude Opus 5.5) | Reproduce the last-seat race on the running system | Script two students accepting the last seat at the same moment | `tests/repro/off-04-last-seat-race.sh` | Status check at the end fixed after the first run stopped early | none |
| 23/11 | Salman | Claude Code (Claude Opus 5.5) | Portal tests for the agreement date and offer notifications | Generate integration tests for OFF-07 (late days, refusals, no penalty before a date), the Engine being down during recalculation (REL-01), and NOT-01/NOT-02 notifications for issue, accept, decline and expiry | 11 tests in `OfferAgreementTest` | The Engine-down case was also checked on the running system | The first expectation (that the Engine-down case was a defect) was rejected: the test and the system both showed it is handled correctly |
| 23/11 | Salman | Claude Code (Claude Opus 5.5) | k6 performance scripts | Generate k6 scripts for PERF-01, PERF-02, PERF-03 and PERF-05 with the requirement limits as thresholds, logging in through the Portal's CSRF form | 3 scripts in `tests/performance/` | The first PERF-01 run was invalid (k6 reset cookies, so users were logged out); fixed with `noCookiesReset` and rerun. PERF-02 changed from 10 users to 1 user by default, with 10 as an option | The first PERF-01 timings (about 1 ms, measured on the login page) |
| 23/11 | Salman | Claude Code (Claude Opus 5.5) | Analyse the slow `/rank` and the Outbox | Time `/rank` for 10 to 500 candidates; count Outbox rows after generating more data | Scalability table for `/rank`; Outbox row and size counts | none | none |
| 24/11 | Salman | Claude Code (Claude Opus 5.5) | Reproduce #11 (late accept) on the running system | Find a way to reach a passed offer deadline without waiting 72 hours, then script issuing and accepting the offer | Steps and evidence posted on #11; TC-OFF-013 updated | The comment explains that the 1-hour setting only uses #10 to reach a passed deadline, so #11 is not read as a duplicate of #10 | none |
| 24/11 | Salman | Claude Code (Claude Opus 5.5) | Find and confirm a Settings defect | Review `SettingsService` against ADM-01; lower the good-standing limit to 1 and let a student apply | Lead on the cached limits, issue #29, TC-ADM-001 | Confirmed on the running system before filing; steps written as UI steps for the issue | none |
| 24/11 | Salman | Claude Code (Claude Opus 5.5) | Draft Q5 root causes for my defects | Group my 19 issues by root cause using the Portal code and the vendor's tests; for each group give the cause, why the vendor's tests missed it, and recommendations with a priority | 8 root-cause groups in `docs/report/q5-root-causes-salman.md` | Code facts and vendor test names checked against the source; Engine causes marked as inferred; an overstated claim about #16 toned down | none |
| 24/11 | Salman | Claude Code (Claude Opus 5.5) | Draft my reflection on AI use | Summarise where AI helped and where it was wrong in my work, using the AI log rows and the real cases from testing | Reflection under "Reflection → Salman" | First-person claims reworded so that they don't say I caught mistakes that Claude caught | none |
| 24/11 | Salman | Claude Code (Claude Opus 5.5) | Draft Q3 and Q4 for my module | Count my test cases by technique, level and type from `docs/test-cases.md`; summarise the suites, results and coverage; list my 19 issues with category, severity and requirement | `docs/report/q3-tests-salman.md`, `docs/report/q4-defects-salman.md` | Counts re-checked against the matrix; a wrong count of issues with a failing automated test (15) corrected to 14; risk IDs taken from the strategy | none |
| 24/11 | Qudama | Claude Code (Claude Opus 5.5) | Design the Applications (APP) test cases and probe the Engine's /eligibility, /match and /rank | Asked Claude to read the Requirements, the Portal code and the OpenAPI file, design at least 10 TC-APP cases with mixed techniques, and run the Engine checks | 30 test cases TC-APP-001 to 030, traceability rows, and Engine results for TC-APP-018 to 025 and 028 | Checked each case against the Requirements before keeping it | Portal rows not accepted as defects until I run them myself |
| 24/11 | Qudama | Claude Code (Claude Opus 5.5) | Draft defect reports #32 to #40 and their reproduction steps | Write issue text in the defect template from the Engine responses and from what I observed in the Portal | Issue structure, steps and expected results | Replaced predicted actual results with what I saw on screen; added my own screenshots | The first plan for #40 used an Engine outage; I re-ran it with the Engine running so that it is not a duplicate of #39 |
| 26/11 | Qudama | Claude Code (Claude Opus 5.5) | Address Salman's review of PR #31 and re-measure `/rank` | Fix TC-APP-009 to match the test actually run, complete the APP-04 traceability row, compare my `/rank` time with TC-PERF-002 | TC-APP-009 and APP-04 row corrections; the finding that `/rank` time depends on the data (13 s when all scores tie) | Checked the new timings myself before adding them to TC-APP-028 and #21 | None |
| 26/11 | Salman | Claude Code (Claude Opus 5.5) | Review Qudama's PR #31 and confirm the shortlist-threshold part of #29 | Check the code predictions in the TC-APP rows against the source; reproduce a shortlist-threshold change on the running system | Review comment on PR #31; #29 widened; TC-ADM-002 | The threshold part is credited to Qudama's TC-APP-030 in the issue; Q3 and Q4 numbers updated to match | none |
| 26/11 | Salman | Claude Code (Claude Opus 5.5) | Test the Engine-down accept path and report it | Use the JaCoCo gap (`tryComputeAllowance`) to design a fault-injection test; reproduce it on the running system with the Engine stopped; draft the issue and the root cause | TC-OFF-031, `OfferRulesTest.acceptWhileEngineDownIsRefused`, issue #41, RC9 in Q5 | Full suite re-run so the Q3 totals and coverage come from a real run (167 tests, `OfferService` 87%) | none |
| 26/11 | Qudama | Claude Code (Claude Opus 5.5) | Write the automated tests for the Applications module | Turn TC-APP-001 to 026 into JUnit tests (Portal, run in CI) and Engine API tests (tests/api), following the existing IntegrationTest and AllowanceApiTest patterns | `ApplicationRulesTest`, `ApplicationEngineRulesTest`, `ApplicationsApiTest` (27 Portal tests, 34 API cases) | Checked that every failing test fails for the predicted reason (CI log messages) before recording results | Did not mark the 8 new failures as defects until reproduced on the running system |
| 26/11 | Salman | Claude Code (Claude Opus 5.5) | Draft report sections Q1 and Q2 for the team | Draft Q1 (system model, flows, Portal–Engine seam, what we inferred about the Engine) and Q2 (risks, levels, roles, Git workflow, changes) from the strategy, the code, the GitHub history and all 50 issues | `docs/report/q1-understanding.md`, `docs/report/q2-process.md` | Claims checked against the source, the contract and GitHub: slot request fields taken from `openapi.yaml`, the SEC test-case count corrected to 19, and the PRs merged without review stated as they are | none |
| 26/11 | Qudama | Claude Code (Claude Opus 5.5) | Draft Q3, Q4 and Q5 for the Applications module | Count my test cases by technique, box, type and level; list my 18 issues; group them by root cause using the vendor's own tests to explain why each got past | `docs/report/q3-tests-qudama.md`, `q4-defects-qudama.md`, `q5-root-causes-qudama.md`; coverage before and after our tests from the CI JaCoCo reports of the #45 and #46 merges | Checked every issue title against GitHub and every code fact (quoted comment, comparisons, `ApplicationState.ACTIVE`) against the source | Removed a claim that the seed data never has an empty nice-to-have list: three seeded postings do |

## Reflection

### Yousef (Users & Access / Security, Interviews, AI Assistant)

**Where AI helped.**
- **Reading code against the requirements.** It found where the rules were missing: the Coordinator address list in `SecurityConfig` that leaves out `/coordinator/reports`, `ApplicationController.view` that never calls the access guard, `th:utext` in the postings list, and the CSRF exclusion for the settings form.
- **Test code.** It wrote the six security test classes (45 test runs, 10 fail on purpose), `ScheduleApiTest` and the evaluation script, around cases I had designed.

**Where AI was wrong.**
- **Requirement IDs.** The first `ScheduleApiTest` used the wrong IDs for the overlap and the working-day rules (INT-01 and INT-03). I corrected them in PR #61.
- **Engine causes.** It wrote causes for the Engine defects that nobody can know, because the Engine is a black box. They are now marked "inferred".
- **A predicted risk that did not happen.** From the code it expected that instructions inside a letter (AI-04) could raise the score. The 99-answer run showed that the model ignored them, so it is reported as a risk, not as a defect.

**How I checked it.**
- **Reproduce before filing.** The security issues were reproduced by clicking the steps after `--reset`, and each has a failing test.
- **Counts.** Counts were recomputed with a script and the files on GitHub were compared with the local copies after each commit.
- **Source.** Claims about the code were checked in the source files. Anything not run is labelled "not reproduced" or "not yet filed".

**What I learned.**
- **Strong and weak.** AI is strong at reading a lot of code and writing repetitive test code around a design I already have. It is weak at what it cannot see: the Engine's internals, and how a real model behaves.
- **Hypotheses.** Its output is a hypothesis. The evidence comes from a failing test or the running system.
- **Testing an AI feature.** It needs repeated runs and reference bands, not one expected value.

### Salman (Offers & Allowance, Settings, Performance)

**Where AI helped.**
- **Reading code fast.** Claude read `OfferService`, `EngineRequestMapper` and `SettingsService` against the requirements
  much faster than I could. All six offer leads it gave me (deadline, auto-withdraw, state guard, seat locking, reminders,
  monthly cap) turned into real defects (#6 to #10, #15, #17), and a later read found #29.
- **Repetitive test code.** It wrote the parts I would have spent the most time on: the Engine start-up helper, the
  controllable clock, the two-thread race test and the k6 login and threshold code.

**Where AI was wrong.**
- **The Engine-down case.** Claude predicted that recording an agreement date while the Engine is down would be a defect.
  The test and the running system both showed that the Portal handles it correctly, so I did not report it.
- **The first PERF-01 run.** It was invalid: k6 reset the cookies between iterations, so every "list page" request was
  really the login page (about 1 ms). The numbers looked excellent and were meaningless. It was caught by checking
  what the pages returned.
- **A reserved tag name.** A `check` tag name it chose is reserved in k6, which made two thresholds meaningless until it
  was renamed.
- **Engine causes.** In the Q5 draft it stated how the Engine handles a month starting on the 31st, although the Engine is
  a black box. It was corrected to say only what we observed before the draft was committed.
- **A deleted database.** It once deleted the Portal data while old copies of the Portal and Engine were still running.

**How I checked it.**
- **Reproduce before filing.** No issue was filed until it was reproduced on the running system or shown by a failing
  test, and preferably both. #11 was first shown only by a test, so I reproduced it on the system later. #15 can only be
  shown by a test, because the Engine ignores the cap anyway (#12).
- **Expected results by hand.** The expected values in the API tests were worked out by hand from OFF-05 and the OpenAPI
  rules, not taken from the AI.
- **Thresholds from the requirements.** The k6 pass/fail thresholds come from the PERF requirements and were set before
  the runs.

**What I learned.**
- **Hypotheses, not findings.** AI is strong where the work is broad and mechanical: reading a lot of code, and writing
  test code around a design I already have. It is weak at judging behaviour it cannot see, such as runtime state, the
  Engine's internals or what a tool really measured. Its predictions are hypotheses to test, not findings.
- **The test oracle has to be mine.** Next time I would write the expected results myself before asking for the test
  code, every time.

### Qudama (Applications)

**Where AI helped.**
- **Reading code against the requirements.** Claude read `ApplicationService`, `EngineRequestMapper`, `MatchScoreService` and `RankingService` against APP-01 to APP-10 and wrote its predictions into the "Not run" rows of my test cases. Salman checked them against the source, and every one became a real defect when it was run (#38 to #40, #47 to #54).
- **Probing the Engine.** It sent boundary requests to `/eligibility`, `/match` and `/rank` and compared the answers with the OpenAPI rules, which found #32 to #37.
- **Test code.** It wrote `ApplicationRulesTest`, `ApplicationEngineRulesTest` and `ApplicationsApiTest` around my test cases (27 Portal tests and 34 API cases). The Portal tests compiled on the first CI run.
- **Measurements.** It took coverage before and after our tests from the CI JaCoCo reports, and showed that `/rank` time depends on the data (1.7 s to 13 s for 200 candidates), which explained why my numbers and #21 did not match.
- **Learning Git.** I had not used GitHub before. It walked me through branches, commits, pull requests and resolving the merge conflict on #46.

**Where AI was wrong.**
- **A duplicate in the making.** Its first plan for #40 relied on an Engine outage, which would have made it a copy of #39. I re-ran it with the Engine running: two students, two different skill sets, the same score.
- **Overstating what it could do.** It said it could post issues for me, then GitHub refused (403), and I had to paste them myself.
- **Wording and numbers.** It paraphrased POS-04 in the draft of #47 instead of quoting it; it labelled one `/rank` data set "all different scores", which is impossible with 200 candidates and scores from 0 to 100; and Q3 first said 16 defects where the 16 failing tests expose 11. All were corrected before publishing.
- **A false claim in Q5.** It wrote that the seed data never has a posting without nice-to-have skills. Three postings have none, so the sentence was removed.
- **Editing files.** Its first script rewrote every row of `test-cases.md` and changed the line endings, including my teammates' rows. This was caught by checking the size of the change before committing.

**How I checked it.**
- **Reproduce before filing.** I reproduced #32 to #40 and #47 myself after `.\start.cmd --reset --jar`, in the browser or with PowerShell requests to the Engine, and attached screenshots. On 27/11 #48, #49, #50, #51 and #53 were also reproduced on the running system, in the browser on my computer with Claude carrying out the steps; #52 and #54 are shown only by failing automated tests, and Q4 states these two exceptions.
- **The right failure.** Each failing test was checked in the CI log to fail for the predicted reason, and the passing tests are the boundary values that should pass.
- **Against the sources.** Issue titles were compared with GitHub, requirement quotes with the Requirements document, and code claims with the source files. Engine causes are marked "inferred".

**What I learned.**
- **Coverage is not enough.** The vendor's tests already ran most of this module's code (`MatchScoreService` had 100% branch coverage) and still missed 11 defects, because they never checked the values at the boundaries.
- **Predictions are hypotheses.** AI reads code and writes test code much faster than I can, but a prediction only counts once a test or the running system shows it.
- **Next time.** I would reproduce every defect on the running system before filing it, not after, and work out the expected results myself before asking for the test code.
