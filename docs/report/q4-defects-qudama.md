# Q4. Defects found: Applications (Qudama)

Draft for the final report (section Q4). It lists the 18 issues Qudama found. The full reports are in GitHub Issues.
No report has been closed as a duplicate or as invalid.

## Totals

| Severity | Count | Points if valid |
| --- | --- | --- |
| Critical | 3 | 9 |
| Major | 13 | 26 |
| Minor | 2 | 2 |
| Enhancement | 0 | 0 |
| **Total** | **18** | **37** |

| Category | Count | Issues |
| --- | --- | --- |
| functional | 9 | #32, #33, #34, #35, #36, #47, #48, #49, #52 |
| integration | 5 | #37, #39, #40, #51, #54 |
| state | 3 | #38, #50, #53 |
| tests | 1 | #55 |

**Critical defects:**
- **#38:** an ACCEPTED application can be withdrawn, and the offer stays "Accepted". The same missing check also lets
  REJECTED and WITHDRAWN applications be withdrawn (added to #38, same root cause).
- **#39:** when the Engine is unreachable, an ineligible student's application is accepted with a match score of 0.
- **#40:** the match score is cached per posting, so every applicant after the first gets the first applicant's score.

**Found in my test cases but filed by teammates (not counted here):** #2 (GPA equal to the minimum, Yousef; my
TC-APP-018), #21 (`/rank` performance, Salman; my TC-APP-028 added the data-dependence measurements as a comment) and
#29 (Settings limits need a restart, Salman; my TC-APP-030).

**How they were found:**
- **Exploratory and state-based testing on the running system:** #38 (withdrawing from a stale page after accepting),
  #39 (stopping the Engine during use) and #40 (two students on one posting).
- **Engine API tests (black-box):** #32 to #37, each first found with direct requests to the running Engine, then
  automated in `ApplicationsApiTest`.
- **Portal automated tests (`ApplicationRulesTest`):** #47 to #54. All except #52 and #54 were then
  confirmed on the running system.
- **Review of the vendor test suite:** #55. Two vendor tests assert the wrong values, which hides #48 and #49.

**Reproduction:**
- **On the running system:** #32 to #40 and #47 (after `.\start.cmd --reset --jar`, in the browser or with PowerShell
  requests to the Engine), and on 27/11/2026 #48, #49, #50, #51 and #53, in the browser after a fresh `--reset`. The
  steps and results are in a comment on each of these issues.
- **By a failing automated test only:** #52 and #54. These are the two exceptions to the cover-page declaration:
  - **#52** needs an application whose match score is exactly the threshold (60). No seeded student and posting give
    that score, and Engine defects #32 and #34 change scores, so it is shown by the test, which sets the score directly.
  - **#54** shows on the ranking page together with Engine defect #36 (one candidate too many within seats), so the
    screen cannot separate the two. The test captures the request the Portal sends to the Engine.
- **#55** is a defect in the vendor's tests, shown by reading and running them.

## Issues

| Issue | Title | Category | Severity | Requirement | Discovered by |
| --- | --- | --- | --- | --- | --- |
| #32 | Engine /match compares skills case-sensitively: "Java" does not match "java" | functional | Major | APP-04, `/match` contract | Qudama |
| #33 | Engine /eligibility refuses a transcript on its 180th day of validity | functional | Major | STU-04, APP-04 | Qudama |
| #34 | Engine /match adds 30 points when the posting has no nice-to-have skills | functional | Major | APP-04, `/match` contract | Qudama |
| #35 | Engine /rank ignores the GPA tie-break: on equal scores the lower GPA is ranked first | functional | Major | APP-09 | Qudama |
| #36 | Engine /rank marks one candidate too many as within seats (aboveCut has seats + 1 ids) | functional | Major | APP-09 | Qudama |
| #37 | Engine /eligibility accepts a GPA above 4.00 or below 0 and an unknown standing instead of answering 400 | integration | Major | REL-02 | Qudama |
| #38 | An ACCEPTED application can be withdrawn by submitting the Withdraw form, leaving the offer "Accepted" | state | Critical | APP-08, APP-06 | Qudama |
| #39 | When the Engine is unreachable, an ineligible student's application is accepted with match score 0 | integration | Critical | REL-01, APP-04 | Qudama |
| #40 | Match score is cached per posting: every applicant gets the first applicant's score | integration | Critical | APP-04 | Qudama |
| #47 | A posting refuses applications on its deadline day while the list still shows it as Open | functional | Major | POS-04, APP-01 | Qudama |
| #48 | A student on PROBATION gets the GOOD limit of 5 active applications instead of 2 | functional | Major | APP-02, STU-03 | Qudama |
| #49 | WITHDRAWN applications count against the active-application limit | functional | Major | APP-02 | Qudama |
| #50 | A student cannot apply again to a posting after a REJECTED application | state | Major | APP-03 | Qudama |
| #51 | Eligibility check ignores the posting's minimum GPA and the student's prior internships | integration | Major | APP-04, POS-01 | Qudama |
| #52 | A match score equal to the shortlist threshold requires a justification | functional | Minor | APP-07 | Qudama |
| #53 | Applications of a SUSPENDED company are not frozen | state | Major | COM-02 | Qudama |
| #54 | Candidate ranking uses the total seats instead of the seats still free | integration | Major | APP-09 | Qudama |
| #55 | Two vendor tests assert values that contradict APP-02 and hide two defects | tests | Minor | APP-02 | Qudama |

The severities are our own. The instructor confirms them when scoring R5.
