# Q4. Defects found: Offers & Allowance, Settings, Performance (Salman)

Draft for the final report (section Q4). It lists the 20 issues Salman found. The full reports are in GitHub Issues.
No report has been closed as a duplicate or as invalid.

## Totals

| Severity | Count | Points if valid |
| --- | --- | --- |
| Critical | 2 | 6 |
| Major | 11 | 22 |
| Minor | 7 | 7 |
| Enhancement | 0 | 0 |
| **Total** | **20** | **35** |

| Category | Count | Issues |
| --- | --- | --- |
| state | 5 | #6, #7, #8, #9, #11 |
| functional | 5 | #10, #12, #13, #16, #17 |
| requirements | 3 | #19, #23, #24 |
| configuration | 2 | #15, #29 |
| performance | 2 | #21, #22 |
| integration | 2 | #14, #41 |
| ui | 1 | #18 |

**Critical defects:**
- **#6:** two students can accept the last seat at the same time, so the posting is oversold.
- **#7:** accepting an offer does not withdraw the student's other applications, so a student can accept a second
  placement.

**How they were found:**
- **Automated tests:** 15 issues have a failing automated test:
  - 9 from Portal integration tests (`OfferRulesTest`): #6, #7, #8, #9, #10, #11, #15, #17, #41.
  - 4 from Engine API tests (`AllowanceApiTest`): #12, #13, #14, #16.
  - 2 from k6 thresholds: #21, #22.
- **Manual testing:** #18 (exploratory) and #29 (code review, then confirmed on the running system).
- **Requirements review:** #19, #23 and #24.
- **Reproduced on the running system:** every Portal defect except #15, either in the browser or with a script.
  #15 cannot be told apart from #12 on the running system, because the Engine ignores the cap anyway, so its failing
  test is the evidence.

## Issues

| Issue | Title | Category | Severity | Requirement | Discovered by |
| --- | --- | --- | --- | --- | --- |
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
| #21 | Engine /rank takes 0.9 s for 200 candidates (limit 0.5 s) and 13 s for 500 | performance | Major | PERF-02 | Salman |
| #22 | Coordinator Outbox is not paginated: every notification is on one page | performance | Minor | PERF-05 | Salman |
| #23 | Specification: PERF-04 is not testable (no time limit or load) | requirements | Minor | PERF-04 (specification) | Salman |
| #24 | Specification: PERF-01 and PERF-02 do not say under what load the p95 is measured | requirements | Minor | PERF-01, PERF-02 (specification) | Salman |
| #29 | Changing the active-application limits or the shortlist threshold in Settings has no effect until the Portal restarts | configuration | Major | ADM-01, APP-02, APP-07 | Salman |
| #41 | Accepting an offer while the Engine is down succeeds with no allowance and no message | integration | Major | REL-01, OFF-06 | Salman |

The severities are our own. The instructor confirms them when scoring R5.
