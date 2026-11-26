# Q5. Root causes and recommendations: Offers & Allowance, Settings, Performance (Salman)

Draft for the final report (section Q5). It covers the 20 issues Salman found: #6 to #19, #21 to #24, #29 and #41.
The team merges it with the other modules' groups.

| Severity | Count | Issues |
| --- | --- | --- |
| Critical | 2 | #6, #7 |
| Major | 11 | #8, #9, #10, #11, #12, #13, #14, #15, #21, #29, #41 |
| Minor | 7 | #16, #17, #18, #19, #22, #23, #24 |

"Why it got past the vendor" is based on the vendor's own test suite in `portal/src/test` (147 tests, all passing).
The Engine is a black box, so its causes are inferred from its answers and marked as such.

## Summary

| Group | Root cause | Issues | Recommendation priority |
| --- | --- | --- | --- |
| RC1 | Check-then-act on shared data without concurrency control | #6 | P1 |
| RC2 | The offer lifecycle bypasses the state machine and stops at the main effect | #7, #8, #9, #17 | P1 |
| RC3 | Deadlines handled as calendar dates and enforced only by a batch job | #10, #11 | P1 |
| RC4 | Settings do not reach the code that uses them | #15, #29 | P2 |
| RC5 | Engine allowance rules wrong at the boundaries; input not validated (inferred) | #12, #13, #14, #16 | P1 (#12, #13), P2 (#14, #16) |
| RC6 | No performance budget in design or testing | #21, #22 | P2 |
| RC7 | Formatting helper not used everywhere | #18 | P3 |
| RC8 | Requirements not reviewed for consistency and measurability | #19, #23, #24 | P2 |
| RC9 | An Engine failure is swallowed instead of refusing the operation | #41 | P2 |

## RC1. Check-then-act on shared data without concurrency control

**Defects:** #6 (two students accept the last seat; the posting is oversold).

**Cause:** `OfferService.accept()` reads `seatsLeft()` and then increments `seatsTaken` in a separate step. `Posting` has no
version column and the row is not locked, so two requests that run at the same time both see one seat left.

**Why it got past the vendor:** every vendor test is single-threaded. `OfferServiceTest.acceptingTakesASeatAndStoresTheStatement`
uses a posting with 2 seats and one student, so it can never reach the last-seat case.

**Recommendations:**
- **P1:** add optimistic locking (`@Version` on `Posting`) or lock the posting row (`SELECT … FOR UPDATE`) in `accept()`,
  and refuse the second acceptance with the OFF-04 message. Fixes #6.
- **P1:** add a database constraint so that a student can hold only one ACCEPTED application. Fixes #6 and also guards #7.
- **P2:** keep a two-thread test in CI (ours: `OfferRulesTest.concurrentAcceptsOfLastSeat`).

## RC2. The offer lifecycle bypasses the state machine and stops at the main effect

**Defects:**
- #7: accepting does not withdraw the student's other applications, so a second placement can be accepted.
- #8: filling the last seat does not reject or notify the remaining applicants.
- #9: an offer can be issued to a SHORTLISTED application.
- #17: the offer reminder is recorded again every 10 minutes.

**Cause:** the Portal has a state machine (`ApplicationService.transition()`), but `OfferService.issue()` sets the state
directly with `setState(OFFERED)`, so the "only after INTERVIEW" rule is never checked (#9). The accept use case
implements only its main effect: the application becomes ACCEPTED and a seat is taken. The side effects that APP-04 and
OFF-03 require in the same sentence (withdraw the student's other applications; reject the rest when the posting
fills) are missing (#7, #8). In the same way, `sendReminders()` records the reminder but never sets `reminderSent`, so
the next run sends it again (#17).

**Why it got past the vendor:**
- `ApplicationStateMachineTest` tests the state machine on its own, not the services that should use it.
- `OfferServiceTest.offerIsIssuedAfterAnInterview` only tries the allowed case (INTERVIEW).
- The accept test has one student with one application on a 2-seat posting, so it never checks other applications or a
  full posting.
- No test runs the reminder job twice.

**Recommendations:**
- **P1:** make every state change go through `ApplicationService.transition()`, and stop services from calling `setState`
  directly. Fixes #9.
- **P1:** implement accept as one transaction with all its effects: withdraw the other active applications, and when the
  last seat is taken, mark the posting FILLED, reject the remaining applications and notify them. Fixes #7, #8.
- **P2:** set `reminderSent` when the reminder is recorded. Fixes #17.
- **P2:** for every use case, write one test per effect listed in the requirement, not only the main one.

## RC3. Deadlines handled as calendar dates and enforced only by a batch job

**Defects:**
- #10: the response deadline is cut to midnight, so students get less than 72 hours (59 hours for an offer issued at 13:06).
- #11: an offer can be accepted after its deadline.

**Cause:** `OfferService.responseDeadline()` computes
`issuedAt.toLocalDate().plusDays(hours / 24).atStartOfDay()`. It drops the time of day and rounds the hours down to whole
days, instead of `issuedAt.plusHours(hours)` (#10). `accept()` checks only that the offer is PENDING and never compares the
current time with `respondBy`. Only the maintenance job, which runs every 10 minutes, expires overdue offers (#11).

**Why it got past the vendor:** the vendor offer tests never move the clock and never check the deadline value. The only
test with a fixed clock is `EngineRequestMapperTest`, which does not test offers.

**Recommendations:**
- **P1:** compute the deadline as `issuedAt.plusHours(hours)`. Fixes #10.
- **P1:** refuse accept and decline when `now(clock)` is after `respondBy`, and keep the job only to mark overdue offers
  EXPIRED. Fixes #11.
- **P2:** test deadlines with a controllable clock, exactly at and just after the deadline (ours: `OfferRulesTest` with
  `MutableClock`).

## RC4. Settings do not reach the code that uses them

**Defects:**
- #15: the monthly cap from Settings is never sent to the Engine.
- #29: changing the active-application limits or the shortlist threshold has no effect until the Portal restarts.

**Cause:**
- `EngineRequestMapper.toAllowanceRequest()` copies the transport supplement and the penalty settings, but never sets
  `monthlyCap` (#15).
- `SettingsService.maxActiveGood()`, `maxActiveProbation()` and `shortlistThreshold()` keep their values in `static` fields that are filled at
  first use (the limits at start-up) and never cleared when a setting is saved (#29).

In both cases the Settings page and the audit log show the new value, so the Coordinator cannot see that it is ignored.

**Why it got past the vendor:**
- `EngineRequestMapperTest` tests the eligibility and match requests but has no test for the allowance request.
- `OfferServiceTest` mocks the Engine with `when(engine.allowance(any()))`, so the request content is never checked.
- `SettingsServiceTest.storedValueReplacesTheDefault` checks a change only for `offerResponseHours`, which is not cached.

**Recommendations:**
- **P2:** set `monthlyCap` in `toAllowanceRequest()`. Fixes #15.
- **P2:** remove the static caches, or clear them in `SettingsService.set()`. Fixes #29.
- **P2:** for each setting, add a test that changes it and checks the behaviour that depends on it (ADM-01). Assert the
  content of every Engine request (ours: `OfferRulesTest.monthlyCapSettingIsSentToEngine`).

## RC5. Engine allowance rules wrong at the boundaries; input not validated (inferred)

**Defects:**
- #12: the monthly cap is never applied.
- #13: the transport supplement is skipped at exactly 30 weekly hours.
- #14: `/allowance` returns 500 for a missing or wrongly typed field.
- #16: a month that starts on the 31st is counted as 1.03 months.

**Cause (inferred from the Engine's answers; the code is not available):**
- Each wrong result sits on a boundary of OFF-05 or the OpenAPI rules: the supplement starts above 30 hours instead of
  at 30, which suggests `>` was used instead of `>=` (#13).
- The cap is ignored even when it is sent (#12).
- A period that starts on the 31st is counted as one month plus one day (1.03 months), so the month-end rule of the
  contract is not implemented (#16).
- Invalid input reaches the calculation and fails there with an exception instead of being refused with 400 (#14).

**Why it got past the vendor (inferred):** correct answers for typical values (whole months, 20 or 40 hours, totals below
the cap) and wrong answers only at the edges suggest the Engine was tested with typical values and no boundary-value or
invalid-input tests. The Portal mocks the Engine in all its tests, so no Portal test calls the real `/allowance`.

**Recommendations:**
- **P1:** apply the cap per month and start the supplement at 30 hours, as OFF-05 says. Fixes #12, #13.
- **P2:** validate requests against the OpenAPI schema and answer 400 with the field name (fixes #14); handle month-end
  start dates as the contract states (fixes #16).
- **P2:** run contract tests against the real Engine jar with boundary and invalid values (ours: `tests/api`,
  `AllowanceApiTest`).

## RC6. No performance budget in design or testing

**Defects:**
- #21: `/rank` takes 0.9 s for 200 candidates (limit 0.5 s) and 13 s for 500.
- #22: the Coordinator Outbox is not paginated.

**Cause:**
- **#21:** ranking time grows much faster than the number of candidates: 5 times the candidates (100 to 500) take about 120
  times as long. Ranking only needs one sort by score, GPA and submission time, which grows almost linearly. The Engine
  code is not available, so the algorithm itself is inferred.
- **#22:** `CoordinatorController.outbox()` accepts a `page` parameter but ignores it. It calls the repository's unpaged
  query and wraps the result in one `PageImpl`, instead of the paginated `NotificationService.all(page)` that already
  exists.

**Why it got past the vendor:**
- The vendor suite has no performance or volume tests, and the seed data is small, so neither problem shows in normal use.
- `NotificationServiceTest` tests the paginated service method, while the controller does not use it.

**Recommendations:**
- **P2:** rank with a single sort on (score, GPA, submission time), and measure 200 and 500 candidates. Fixes #21.
- **P2:** use `NotificationService.all(page)` in the Outbox controller. Fixes #22.
- **P3:** run the k6 scripts (`tests/performance`) with the PERF thresholds before each release, with seed data plus 1,000
  applications.

## RC7. Formatting helper not used everywhere

**Defects:** #18 (allowance statement amounts have no thousands separator).

**Cause:** `applications/view.html` prints statement lines with `|QAR ${l.amount}|`, while the offer section on the same
page uses the `@fmt.money` helper.

**Why it got past the vendor:** `FmtTest` tests the helper, but no test checks how the templates render amounts.

**Recommendation:**
- **P3:** use the money helper in every template that shows an amount, and add one rendered-page check for USA-04.
  Fixes #18.

## RC8. Requirements not reviewed for consistency and measurability

**Defects:**
- #19: NOT-04 assumes a 48-hour response window, but OFF-02 says 72 hours from Settings.
- #23: PERF-04 is not testable.
- #24: PERF-01 and PERF-02 do not say under what load the 95th percentile is measured.

**Cause:** NOT-04 hard-codes a number that OFF-02 and ADM-01 make configurable. PERF-04 has no measurable target, and
PERF-01 and PERF-02 fix the data volume but not the number of concurrent users.

**Why it got past the vendor:** each requirement reads well on its own; the problems only show when requirements are
compared with each other, or when someone tries to write a pass/fail test.

**Recommendations:**
- **P2:** NOT-04 should refer to "the response window (OFF-02)" instead of 48 hours. Fixes #19.
- **P2:** give PERF-04 a number (for example 1 s at the 95th percentile with 10 users), and add the number of concurrent
  users to PERF-01 and PERF-02. Fixes #23, #24.
- **P3:** review new requirements with a checklist: no hard-coded values that Settings control, and a number plus a load
  for every non-functional requirement.

## RC9. An Engine failure is swallowed instead of refusing the operation

**Defects:** #41 (accepting an offer while the Engine is down succeeds with no allowance and no message).

**Cause:** `OfferService.accept()` first takes the seat and marks the offer and the application ACCEPTED. Only then does
it call `tryComputeAllowance()`, which catches `EngineUnavailableException`, sets the allowance to null and only writes a
warning to the log. The same exception is handled correctly elsewhere: recording the agreement date while the Engine
is down shows the error and changes nothing (TC-OFF-024). The accept path chose to "try" instead.

**Why it got past the vendor:** `OfferServiceTest` always stubs the Engine to return an allowance
(`when(engine.allowance(any())).thenReturn(…)`), so no test runs `accept()` with the Engine down. Our coverage report
showed `tryComputeAllowance` as the only uncovered error path in `OfferService`, which is how we found it.

**Recommendations:**
- **P2:** in `accept()`, compute the allowance before changing any state, or let `EngineUnavailableException` propagate
  so that the transaction rolls back, and show its message to the student. Fixes #41.
- **P3:** for every call to the Engine, add a test where the mocked Engine throws and assert that nothing changed (ours:
  `OfferRulesTest.acceptWhileEngineDownIsRefused`).
