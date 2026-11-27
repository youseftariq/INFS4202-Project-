# Q5. Root causes and recommendations: Applications (Qudama)

Draft for the final report (section Q5). It covers the 18 issues Qudama found: #32 to #40 and #47 to #55. The team
merges it with the other modules' groups. Groups are numbered A1 to A7 (A for Applications) so that they do not clash
with the other sections' numbers.

| Severity | Count | Issues |
| --- | --- | --- |
| Critical | 3 | #38, #39, #40 |
| Major | 13 | #32, #33, #34, #35, #36, #37, #47, #48, #49, #50, #51, #53, #54 |
| Minor | 2 | #52, #55 |

"Why it got past the vendor" is based on the vendor's own tests in `portal/src/test`: `ApplicationServiceTest` (15),
`ApplicationStateMachineTest` (25), `EngineClientTest` (5), `EngineRequestMapperTest` (3), `MatchScoreServiceTest` (1),
`RankingServiceTest` (2) and `PostingServiceTest` (14). All of them pass. The Engine is a black box, so its causes are
inferred from its answers and marked as such.

## Summary

| Group | Root cause | Issues | Recommendation priority |
| --- | --- | --- | --- |
| A1 | State rules rewritten in each method instead of using the one shared definition | #38, #49, #50, #53 | P1 (#38, #49, #50), P2 (#53) |
| A2 | Inclusive limits coded as exclusive (off by one) | #47, #52; Engine: #33, #36 | P2 |
| A3 | Wrong or incomplete data sent to the Engine or cached from it | #40, #51, #54 | P1 (#40, #51), P2 (#54) |
| A4 | An Engine failure is turned into a "yes" | #39 | P1 |
| A5 | A String compared with an enum | #48 | P1 |
| A6 | Vendor tests that assert what the code does, not what the Requirements say | #55 (and it hid #39, #48, #49) | P2 |
| A7 | Engine matching, ranking and validation rules do not follow its own contract (inferred) | #32, #34, #35, #37 | P1 (#32, #34), P2 (#35, #37) |

## A1. State rules rewritten in each method instead of using the one shared definition

**Defects:**
- #38: an ACCEPTED application can be withdrawn (also REJECTED and WITHDRAWN ones).
- #49: WITHDRAWN applications count against the active-application limit.
- #50: a student cannot apply again after a REJECTED application.
- #53: the applications of a SUSPENDED company are not frozen.

**Cause:** the domain already has the right definitions. `ApplicationState.ACTIVE` is exactly APP-02's list
(SUBMITTED, SHORTLISTED, INTERVIEW, OFFERED), and `ApplicationStateMachine.assertCanMove` holds APP-06's transitions.
Even the demo-data seeder and `CompanyService` use `isActive()`. But `ApplicationService` writes its own version of
each rule, and each version is wrong:
- `withdraw()` sets WITHDRAWN directly, without calling the state machine (#38).
- `countActive()` counts every state except REJECTED and ACCEPTED, so WITHDRAWN counts (#49).
- The duplicate check in `submit()` ignores only WITHDRAWN applications, so REJECTED blocks a new one (#50).
- `assertNotFrozen()` checks only PENDING, not "anything but APPROVED" (#53).

**Why it got past the vendor:**
- `studentCanWithdrawASubmittedApplication` tries only the allowed case. No test withdraws from a final state.
- `secondActiveApplicationToTheSamePostingIsRefused` tries only a SUBMITTED earlier application, never a REJECTED one.
- `withdrawnApplicationsRemainInHistoryCount` asserts the wrong count (see A6).
- `pendingCompanyCannotPublish` covers PENDING. No test suspends a company.
- `ApplicationStateMachineTest` tests the state machine on its own, not the services that should call it.

**Recommendations:**
- **P1:** make `withdraw()` go through `ApplicationStateMachine.assertCanMove`, like `transition()` does. Fixes #38.
- **P1:** count and check active applications with `ApplicationState.ACTIVE` (for example
  `countByStudentAndStateIn(student, ACTIVE)`). Fixes #49 and #50.
- **P2:** freeze applications whenever the company is not APPROVED. Fixes #53.
- **P2:** test each rule for every value of the enum it depends on (a parameterised test over all states, or all company
  statuses), not only for the one value the code was written for. Our `finalApplicationCannotBeWithdrawn` and
  `applicationsOfANonApprovedCompanyAreFrozen` do this.

## A2. Inclusive limits coded as exclusive (off by one)

**Defects:**
- #47: a posting refuses applications on its deadline day, while the list still shows it as Open.
- #52: a match score equal to the shortlist threshold needs a justification.
- Engine (inferred): #33 refuses a transcript on its 180th day, and #36 marks seats + 1 candidates as within seats.

**Cause:** every one of these requirements says "until", "at least" or "for 180 days", which include the limit itself.
The code uses the exclusive comparison:
- `PostingService.acceptsApplications` uses `today.isBefore(deadline)` (#47). The postings list uses
  `!deadline.isBefore(today)`, so the two pages disagree on the same day.
- `ApplicationService.shortlist` uses `score <= threshold` to decide that a justification is needed (#52).
- The Engine's answers show the same pattern for day 180 (#33) and for the cut (#36). Yousef's #2 (GPA equal to the
  minimum) is the same mistake in `/eligibility`.

**Why it got past the vendor:** the vendor's tests choose values far from the limit:
- `openPostingAcceptsApplicationsBeforeItsDeadline` uses tomorrow, and `postingDoesNotAcceptApplicationsAfterItsDeadline`
  uses yesterday. The deadline day itself is never tested.
- The shortlist tests use scores 85 and 35, never 60.

**Recommendations:**
- **P2:** change the comparisons to the inclusive form, and use one helper for "is the posting open" in both the service
  and the list page. Fixes #47 and #52.
- **P2:** ask the Engine vendor to fix the day-180 and cut boundaries. Fixes #33 and #36.
- **P2:** for every rule worded "at least", "at most", "until" or "within", test the limit itself and one step on each
  side (boundary value analysis). Our `ApplicationRulesTest` and `ApplicationsApiTest` do this for these rules.

## A3. Wrong or incomplete data sent to the Engine or cached from it

**Defects:**
- #40: the match score is cached per posting, so every applicant after the first gets the first applicant's score.
- #51: the eligibility check uses the default minimum GPA from Settings instead of the posting's, and never sends the
  prior internships.
- #54: the candidate ranking sends the total seats instead of the seats still free.

**Cause:** the Portal builds the Engine requests from the wrong source, or leaves a field out:
- `MatchScoreService.key()` builds the cache key from the posting id and the required skills only, but the score depends
  on the student's skills too (#40).
- `EngineRequestMapper.toEligibilityRequest` reads `DEFAULT_MIN_GPA` from Settings instead of `posting.getMinGpa()`, and
  no line sets `priorInternships`, so it is always 0 (#51).
- `RankingService.rank` sends `posting.getSeats()` instead of `posting.seatsLeft()` (#54).

Each request is valid JSON, so the Engine answers normally and nothing looks wrong.

**Why it got past the vendor:**
- `MatchScoreServiceTest.repeatedRequestForTheSameSkillsUsesTheCache` checks that the cache is used for the same skills,
  never that different skills are not served from it.
- `EngineRequestMapperTest.eligibilityRequestCarriesAcademicDataAndPostingMinimums` asserts the minimum credits, but not
  the minimum GPA or the prior internships, despite its name.
- `RankingServiceTest` uses a posting with 1 seat and none taken, so total and free seats are the same.

**Recommendations:**
- **P1:** put the student's skills in the cache key, or remove the cache (the Engine answers `/match` in under 3 ms, see
  TC-APP-028). Fixes #40.
- **P1:** send the posting's minimum GPA and the student's prior internships. Fixes #51.
- **P2:** send the seats still free. Fixes #54.
- **P2:** for every field of every Engine request, keep one test that sets a distinct value at the source and checks
  that exact value in the request (our `eligibilityRequestUsesThePostingAndTheStudent` and
  `rankingUsesTheSeatsStillFree` do this).

## A4. An Engine failure is turned into a "yes"

**Defects:** #39 (when the Engine is unreachable, an ineligible student is accepted with match score 0).

**Cause:** this was a design choice, not a slip. `EngineClient.checkEligibility` catches `EngineUnavailableException` and
returns `EligibilityResult.ok()`, with the comment "the check is skipped, so that students are not blocked".
`EngineClient.match` returns a score of 0 on the same exception, and the Portal stores it as if the Engine had computed
it. REL-01 requires the opposite: refuse the operation and never treat a student as eligible. Salman's #41 (accepting an
offer while the Engine is down) has the same cause, so the two groups should be merged in the report.

**Why it got past the vendor:** the vendor tested the design, not the requirement.
`EngineClientTest.engineUnavailableDegradesGracefully` asserts that the answer is *eligible* when the Engine is down.

**Recommendations:**
- **P1:** let `EngineUnavailableException` reach the controller and show the REL-01 message; never store a score the
  Engine did not return. Fixes #39 (and, with Salman's recommendation, #41).
- **P2:** review every `catch` around an Engine or AI call with one question: does the caller still believe the
  operation succeeded? Fix the vendor test above at the same time (see A6).

## A5. A String compared with an enum

**Defects:** #48 (a PROBATION student gets the GOOD limit of 5 instead of 2).

**Cause:** `ApplicationService.activeLimitFor` tests `"PROBATION".equals(student.getStanding())`. `getStanding()`
returns the `Standing` enum, and a String is never equal to an enum, so the PROBATION branch can never run. Java
compiles it because `equals` accepts any object.

**Why it got past the vendor:** `probationStudentUsesStandardLimit` uses a PROBATION student and asserts the limit 5, so
it passes on the defect (see A6). Static analysis was not run: common tools flag comparing unrelated types
(IntelliJ's "equals() between objects of inconvertible types", Error Prone's `EqualsIncompatibleType`).

**Recommendations:**
- **P1:** compare with `student.getStanding() == Standing.PROBATION`. Fixes #48.
- **P3:** add static analysis to the CI workflow, failing the build on this kind of check.

## A6. Vendor tests that assert what the code does, not what the Requirements say

**Defects:** #55 (two vendor tests assert values that contradict APP-02).

**Cause:** some vendor tests were written by running the code and copying its result into the assertion:
- `probationStudentUsesStandardLimit` expects 5 where APP-02 says 2 (hides #48).
- `withdrawnApplicationsRemainInHistoryCount` expects 5 where APP-02 says 1 (hides #49).
- The same pattern appears in `EngineClientTest.engineUnavailableDegradesGracefully`, which expects *eligible* when the
  Engine is down (hides #39).

A test like this cannot find the defect it covers, and it fails when the code is fixed, which pushes the developer
to keep the defect.

**Why it got past the vendor:** the test names describe what the code does ("uses standard limit", "remain in history
count", "degrades gracefully"), not the rule. Nothing links a test to a requirement, so nobody compared the expected
values with APP-02 or REL-01.

**Recommendations:**
- **P2:** correct the three tests so that they assert the Requirements; they will fail until #48, #49 and #39 are fixed.
  Fixes #55.
- **P2:** start every test name with the requirement it checks, as our tests do (`TC-APP-005 / APP-02: ...`). Review the
  expected values against that requirement, not against the code's output.

## A7. Engine matching, ranking and validation rules do not follow its own contract (inferred)

**Defects:**
- #32: skills are compared case-sensitively ("Java" does not match "java").
- #34: an empty nice-to-have list adds 30 points instead of 0.
- #35: the GPA tie-break is ignored in the ranking.
- #37: a GPA outside 0 to 4 and an unknown standing are accepted instead of answering 400.

**Cause (inferred from the answers):**
- Skills are compared with exact string equality, with no trimming or case folding (#32).
- The nice-to-have share is computed as "everything matched" when the list is empty, instead of 0 (#34).
- The ranking sorts by score and then by submission time, leaving out GPA (#35).
- Input validation exists for some fields (credits, `minGpa`, seats) but was not written for `gpa` and `standing` (#37).

Each is a rule stated in the Engine's own OpenAPI description, so the Engine does not meet its published contract.

**Why it got past the vendor:** no Engine tests are delivered. The Portal's tests stub the Engine with fixed answers, so
they could never notice a wrong Engine result.

**Recommendations:**
- **P1:** fix #32 and #34, because they make match scores wrong for many students, and the scores decide shortlisting
  (APP-07).
- **P2:** fix #35 and #37.
- **P2:** run a contract test suite against every Engine build (ours: `tests/api/ApplicationsApiTest`, 34 cases from the
  OpenAPI rules), and make it part of accepting a new Engine version.
