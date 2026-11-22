# Test design

A spreadsheet `docs/test-cases.xlsx` may replace this file. At least 10 test cases for each of the three primary modules.

**Conventions:**
- **IDs** are `TC-<MODULE>-<NNN>`.
- **Levels:** API means a black-box Engine call; Integration means Portal service + H2 database, with the Engine stubbed; System means the running Portal in a browser.
- **Allowance requests** use the default Settings values unless a row says otherwise: transport supplement 300, monthly cap 5,000, penalty 5% per started week, penalty cap 20%.
- **Statement lines** follow the OpenAPI definition of `/allowance`, in this order: base, transport, cap adjustment, penalty, total.

## Test case matrix

### Offers & Allowance (OFF): owner Salman

| Test case ID | Requirement | Module | Level | Technique | Preconditions | Steps | Test data | Expected result | Actual result | Status | Automated test | Issue |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-OFF-001 | OFF-05 | Offers & Allowance | API | Equivalence partitioning (whole months, no extras) | Engine running | POST `/allowance` | 3000/month, 01/01/2027 to 31/03/2027, 20 h/week, 0 late days | total 9,000.00; lines: base, total | 9,000.00, lines base and total | Pass | `AllowanceApiTest.baseOnlyForWholeMonths` | |
| TC-OFF-002 | OFF-05 | Offers & Allowance | API | Boundary value analysis (weekly hours = 30) | Engine running | POST `/allowance` | as TC-OFF-001 with 30 h/week | transport line 900.00; total 9,900.00 | 9,000.00, no transport line (the supplement starts only at 31 h) | Fail | `AllowanceApiTest.transportSupplementFromThirtyHours` | |
| TC-OFF-003 | OFF-05 | Offers & Allowance | API | Boundary value analysis (weekly hours = 29 and 31) | Engine running | POST `/allowance` twice | as TC-OFF-001 with 29 h, then 31 h | 29 h: 9,000.00, no transport; 31 h: 9,900.00 | as expected | Pass | `AllowanceApiTest.transportSupplementAroundThreshold` | |
| TC-OFF-004 | OFF-05 | Offers & Allowance | API | Decision table (allowance + transport above the cap) | Engine running | POST `/allowance` | 4900/month, 3 whole months, 40 h/week | cap adjustment −600.00 (5,200 − 5,000 per month × 3); total 15,000.00 | 15,600.00, no cap adjustment line; the same happens with 5000/month (15,900.00) and with `monthlyCap` 3100 | Fail | `AllowanceApiTest.monthlyTotalIsCapped` | |
| TC-OFF-005 | OFF-05 | Offers & Allowance | API | Equivalence partitioning (partial month) | Engine running | POST `/allowance` | 3000/month, 01/01/2027 to 15/02/2027, 20 h | 1 month + 15/30 = 1.5 months; total 4,500.00 | 4,500.00 | Pass | `AllowanceApiTest.partialMonthCountsDaysOverThirty` | |
| TC-OFF-006 | OFF-05, OFF-07 | Offers & Allowance | API | Boundary value analysis (started weeks: 1, 7, 8 late days) | Engine running | POST `/allowance` three times | as TC-OFF-001, late days 1 / 7 / 8 | penalty 5% / 5% / 10%; totals 8,550.00 / 8,550.00 / 8,100.00 | as expected | Pass | `AllowanceApiTest.penaltyPerStartedWeek` | |
| TC-OFF-007 | OFF-05 | Offers & Allowance | API | Boundary value analysis (penalty cap: 28, 29, 60 late days) | Engine running | POST `/allowance` three times | as TC-OFF-001, late days 28 / 29 / 60 | penalty 20% each time (4, 5 and 9 started weeks, capped); total 7,200.00 | as expected | Pass | `AllowanceApiTest.penaltyIsCappedAtTwentyPercent` | |
| TC-OFF-008 | OFF-05 | Offers & Allowance | API | Boundary value analysis (month-end start dates) | Engine running | POST `/allowance` | 3000/month, 31/01/2027 to 28/02/2027 and 31/03/2027 to 30/04/2027, 20 h | by the OpenAPI rule ("when the next month has no such day, the month runs to the end of that next month") each period is exactly 1 month: 3,000.00 | 1.03 months, 3,100.00, in both cases | Fail | `AllowanceApiTest.monthStartingOnDayThirtyOne` | |
| TC-OFF-009 | OFF-05 | Offers & Allowance | API | Boundary value analysis (rounding half up) | Engine running | POST `/allowance` | 1000.03/month, 01/01/2027 to 15/01/2027 (0.5 month) | 500.015 rounded half up: 500.02 | 500.02 | Pass | `AllowanceApiTest.totalIsRoundedHalfUp` | |
| TC-OFF-010 | REL-02 | Offers & Allowance | API | Error guessing / robustness (non-functional) | Engine running | POST `/allowance` with a missing field, a wrong type, `null`, `{}` | omit `monthlyAllowance`; `"abc"`; `null`; empty object | HTTP 400, `{"error":"validation","field":"monthlyAllowance",...}` | HTTP 500 `{"error":"internal","message":"Unexpected error"}` in all four cases | Fail | `AllowanceApiTest.missingOrWronglyTypedFieldIs400` | |
| TC-OFF-011 | REL-02, OFF-05 | Offers & Allowance | API | Boundary value analysis (invalid domains) | Engine running | POST `/allowance` | allowance 5001 and −1; hours 9 and 41; late days −1; end before start; hours 20.5 | HTTP 400 naming the field each time | HTTP 400 naming the field | Pass | `AllowanceApiTest.outOfRangeValuesAre400` | |
| TC-OFF-012 | OFF-02 | Offers & Allowance | Integration | Statement coverage / code review of `OfferService.responseDeadline` | Application in INTERVIEW | Issue an offer at 15:00 | offer response hours = 72 (default) | respond-by = issue time + 72 h | issued at 15:00 on day D, respond-by = D+3 at 00:00 (57 h): the time of day is dropped (`responseDeadline` uses `toLocalDate()...atStartOfDay()`) | Fail | `OfferRulesTest.responseDeadlineIsSeventyTwoHoursAfterIssue` | |
| TC-OFF-013 | OFF-02 | Offers & Allowance | Integration | State transition (PENDING after deadline) | Pending offer whose deadline has passed; the scheduler has not run yet | Student accepts | | acceptance refused | accepted 73 h after issue: `accept()` checks only the status PENDING, not the deadline; offers expire only when the 10-minute job runs | Fail | `OfferRulesTest.offerCannotBeAcceptedAfterDeadline` | |
| TC-OFF-014 | OFF-03 | Offers & Allowance | Integration | State transition | Student has the offer plus 2 other active applications (SUBMITTED, SHORTLISTED) | Student accepts the offer | | both stay SUBMITTED / SHORTLISTED: `accept()` never withdraws other applications | Fail | `OfferRulesTest.acceptingWithdrawsOtherActiveApplications` | |
| TC-OFF-015 | OFF-03, NOT-01 | Offers & Allowance | Integration | State transition (last seat) | Posting with 1 seat; 2 other students hold active applications | Student accepts the offer | | posting FILLED, but the other 2 applications stay SUBMITTED / SHORTLISTED and no notification is recorded | Fail | `OfferRulesTest.fillingLastSeatRejectsRemainingApplications` | |
| TC-OFF-016 | OFF-01, APP-06 | Offers & Allowance | Integration | Branch coverage of `OfferService.issue` (state guard) | Application in SHORTLISTED (no interview) | Recruiter issues an offer | | offer issued, application moved SHORTLISTED → OFFERED: `issue()` sets the state directly without `ApplicationStateMachine` | Fail | `OfferRulesTest.offerOnlyForApplicationInInterview` | |
| TC-OFF-017 | OFF-04 | Offers & Allowance | Integration | Concurrency test (non-functional, reliability) | Posting with 1 seat; 2 students with pending offers | Both accept at the same moment (2 threads) | | both acceptances succeed in 10 of 10 rounds (no lock or version on `Posting`) | Fail | `OfferRulesTest.concurrentAcceptsOfLastSeat` | |
| TC-OFF-018 | NOT-04 | Offers & Allowance | Integration | Code review + state-based test | Pending offer inside the last 24 h of its window | Run the reminder job twice | | 2 reminders after 2 runs: `sendReminders()` never sets `reminderSent` | Fail | `OfferRulesTest.reminderIsRecordedOnce` | |
| TC-OFF-019 | ADM-01, MAINT-02, OFF-05 | Offers & Allowance | Integration | Data-flow review of `EngineRequestMapper.toAllowanceRequest` | Coordinator sets monthly cap = 4,000 | Student accepts an offer | | `monthlyCap` is null: `EngineRequestMapper.toAllowanceRequest` never sets it, so the Engine falls back to 5,000 | Fail | `OfferRulesTest.monthlyCapSettingIsSentToEngine` | |
| TC-OFF-020 | OFF-06, USA-04 | Offers & Allowance | System | Exploratory (charter: statement display) | Accepted offer | Open the application as the student, then as the coordinator | | statement visible to both; amounts as QAR with 2 decimals and thousands separators | statement visible to the student; amounts shown as "QAR 9000.00" with no thousands separator (the offer above shows "QAR 3,000.00") | Fail | manual | |

## Requirement to test traceability matrix

| Requirement | Test cases | Automated tests | Issues found | Covered? |
| --- | --- | --- | --- | --- |
| OFF-01 | TC-OFF-016 | `OfferRulesTest` | | Partly (allowance and dates are covered by vendor tests) |
| OFF-02 | TC-OFF-012, TC-OFF-013 | `OfferRulesTest` | | Yes |
| OFF-03 | TC-OFF-014, TC-OFF-015 | `OfferRulesTest` | | Yes |
| OFF-04 | TC-OFF-017 | `OfferRulesTest` | | Yes |
| OFF-05 | TC-OFF-001 to TC-OFF-009, TC-OFF-019 | `AllowanceApiTest`, `OfferRulesTest` | | Yes |
| OFF-06 | TC-OFF-020 | manual | | Yes |
| OFF-07 | TC-OFF-006 | `AllowanceApiTest` | | Partly (Portal recalculation not yet) |
| REL-02 | TC-OFF-010, TC-OFF-011 | `AllowanceApiTest` | | Yes, for `/allowance` |
| NOT-04 | TC-OFF-018 | `OfferRulesTest` | | Yes |
| ADM-01, MAINT-02 | TC-OFF-019 | `OfferRulesTest` | | Partly (monthly cap only) |
