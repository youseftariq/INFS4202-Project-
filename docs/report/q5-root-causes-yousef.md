# Q5. Root causes and recommendations: Users & Access / Security, Interviews, AI Assistant (Yousef)

Draft for the final report (section Q5). It covers the 12 issues Yousef filed (#2, #3, #4, #5, #20, #26, #27, #28, #57,
#58, #59, #60) and the six AI Assistant findings that are not yet filed (written as AI-03, AI-05, AI-06, AI-09, AI-10
and AI-11). The team merges it with the other modules' groups.

| Severity | Count | Issues |
| --- | --- | --- |
| Critical | 4 | #4, #5, #20, #26 |
| Major | 6 | #2, #27, #28, #58, #59, #60 |
| Minor | 2 | #3, #57 |

"Why it got past the vendor" is based on the vendor's own test suite in `portal/src/test`. For the web layer it has only
`SecurityTest` (6 tests: login page, anonymous redirect, right and wrong password, and a student refused on the settings
and the recruiter pages) and `FmtTest`. The Engine is a black box, so its causes are inferred from its answers and
marked as such.

## Summary

| Group | Root cause | Issues | Recommendation priority |
| --- | --- | --- | --- |
| RC1 | Access control is written by hand for each address, and some addresses were missed | #20, #26, #27 | P1 |
| RC2 | Security settings left at development values | #4, #28 | P1 (#4), P2 (#28) |
| RC3 | Text output is not escaped in one template | #5 | P1 |
| RC4 | One rule implemented in two places, and only one is complete | #3 | P3 |
| RC5 | Engine rules are wrong at the boundaries (inferred) | #2, #57, #58, #60 | P2 (#58, #60, #2), P3 (#57) |
| RC6 | The Portal sends times without a time zone, and the contract does not stop it | #59 | P1 |
| RC7 | The AI prompt is built from whole objects and was never reviewed | AI-03, AI-10, AI-11 | P1 (AI-03), P3 (AI-10, AI-11) |
| RC8 | The AI call has no time-out and the answer is only partly validated | AI-05, AI-06 | P2 |
| RC9 | The AI score is not controlled for repeatability or for instructions in the letter | AI-09 (and the AI-04 risk) | P2 |

## RC1. Access control is written by hand for each address, and some addresses were missed

**Defects:**
- #20: the Reports page and its CSV exports are open to any logged-in user.
- #26: any logged-in user can open another student's application by changing the number in the address.
- #27: any recruiter can download the CV and transcript of any student.

**Cause:** there is no single rule that says who may open what. `SecurityConfig` lists the Coordinator addresses one by
one (`/coordinator/settings/**`, `/coordinator/audit/**` and so on), leaves `/coordinator/reports/**` out of the list,
and ends with `.anyRequest().authenticated()`, so every address that is not listed is open to every logged-in user
(#20). Ownership is checked by calling `AccessGuard` by hand inside each controller method. `ApplicationController.view`
(`/applications/{id}`) does not call it, although `AccessGuard.assertCanView` exists (#26). `StudentController.download`
checks the owner for students but contains `|| user.is(Role.RECRUITER)`, which lets every recruiter through (#27). A
missing application number also answers "Not found" while a forbidden one answers "Access denied", so the two pages
reveal which numbers exist, which USR-05 does not allow.

**Why it got past the vendor:**
- `SecurityTest` tries only the settings page and the recruiter pages as a student. It never opens Reports, an
  application of another student, or a document.
- No vendor test covers `AccessGuard` or the download address. Every vendor test that opens a record does it as the
  owner.

**Recommendations:**
- **P1:** make the default "deny": add `/coordinator/**` for the Coordinator role as one rule, and replace
  `.anyRequest().authenticated()` with a list of the public and shared addresses. Fixes #20.
- **P1:** call `guard.assertCanView(user, application)` in `ApplicationController.view`, and in `download` let a recruiter
  through only when the document belongs to a student who applied to one of their company's postings. Fixes #26, #27.
- **P1:** answer a missing record and a forbidden record with the same page for users who may not see it. Closes the
  last part of USR-05.
- **P2:** keep an access-matrix test in CI that opens every address as every role (ours: `AccessControlTest`, `AccessGuardTest`).
- **P3:** consider method-level checks (`@PreAuthorize`) so that a new controller method is protected by default.

## RC2. Security settings left at development values

**Defects:**
- #4: error pages show the exception class name, the message and a full stack trace.
- #28: a form post to `/coordinator/settings` without a CSRF token is accepted.

**Cause:**
- `application.properties` sets `server.error.include-stacktrace=always`, `include-message=always` and
  `include-exception=true`. These are debugging values, and nothing replaces them for a normal run (#4).
- `SecurityConfig` has `csrf.ignoringRequestMatchers("/coordinator/settings/**")`. The exclusion also covers
  `/coordinator/settings/test-ai`. A page on another site could make a logged-in Coordinator's browser change the
  system settings (#28). The comment in the code gives no reason for the exclusion.

**Why it got past the vendor:** no vendor test reads the error settings or posts a form without a token. The defects
were found with requests that no normal test sends: an invalid address (`/applications/abc`) and a post without a token.

**Recommendations:**
- **P1:** set the three error properties to `never`, `never` and `false`, and show the generic "Something went wrong" page
  (fixes #4).
- **P2:** remove the CSRF exclusion. The forms built with `th:action` already carry the token; if a script call needs an
  exception, send the token in a header instead (fixes #28).
- **P2:** keep a configuration test for the error settings and a test that posts every form without a token (ours:
  `SecurityConfigurationTest`, `AccessControlTest.settingsFormNeedsACsrfToken`).

## RC3. Text output is not escaped in one template

**Defects:** #5 (HTML typed into the company name is interpreted by the browser on the postings page).

**Cause:** `postings/list.html` line 29 prints the company name with `th:utext`, which writes the text as raw HTML. Every
other cell uses `th:text`, which escapes it. A recruiter controls the company name, and every student sees it.

**Why it got past the vendor:** `FmtTest` tests the formatting helper, but no test renders a page with text that contains
HTML.

**Recommendations:**
- **P1:** use `th:text` for the company name. Fixes #5.
- **P2:** search all templates for `th:utext` and keep it only for text the application itself writes. This is the only use
  in the current templates.
- **P3:** add one rendering test that stores `<i>x</i>` in every text field a user can edit and checks that the page shows
  it literally (ours: `AccessControlTest.companyNameIsEscapedOnThePostingsList`).

## RC4. One rule implemented in two places, and only one is complete

**Defects:** #3 (changing a password does not enforce the digit rule).

**Cause:** the password rule is written in `UserService.checkPassword` (length of 10 and a digit) and used when a
Coordinator creates a user. `UserService.changePassword` does its own check and tests only the length. USR-02 says the
rule applies to both.

**Why it got past the vendor:** `UserServiceTest.weakPasswordsAreRefused` tests only the create path, and
`passwordChangeNeedsTheCurrentPassword` changes to `NewPassword9`, which contains a digit, so the missing check is never
reached.

**Recommendations:**
- **P3:** call `checkPassword` from `changePassword`, so there is one rule. Fixes #3.
- **P3:** test every rule of a requirement on every path that the requirement names (ours: `PasswordPolicyTest`).

## RC5. Engine rules are wrong at the boundaries (inferred)

**Defects:**
- #2: a student whose GPA equals the minimum GPA is refused.
- #57: a slot of exactly 120 minutes is refused.
- #58: an interview that starts exactly when another one of the same recruiter ends is refused as an overlap.
- #60: an interview that ends after 18:00 is accepted when it starts before 18:00.

**Cause (inferred from the Engine's answers; the code is not available):**
- Three of the four sit exactly on an edge of the rule: equality is treated as failure (a GPA equal to the minimum, a
  duration equal to the maximum, a start equal to an end). This suggests `>` and `<` where `>=` and `<=` were meant, or
  the reverse (#2, #57, #58).
- For #60, the answers show that a slot is refused only when it starts at or after 18:00 (17:00 to 18:01, 17:30 to 18:30 and 17:59 to 18:30 are accepted).
  The check looks at the start of the slot, and not at its end.

**Why it got past the vendor (inferred):** correct answers for typical values and wrong answers only at the edges suggest
the Engine was tested with typical values and without boundary tests. The Portal's `InterviewServiceTest` mocks the
Engine, so no Portal test calls the real `/schedule/validate`.

**Recommendations:**
- **P2:** make the end of the slot the value that is compared with 18:00 (#60), and treat touching slots as not
  overlapping, as the OpenAPI text says (#58). Fixes #58, #60.
- **P2:** accept a GPA equal to the minimum (#2). **P3:** accept a duration of exactly 120 minutes (#57).
- **P2:** run contract tests against the real Engine jar with the values at, just below and just above each limit (ours:
  `ScheduleApiTest`).

## RC6. The Portal sends times without a time zone, and the contract does not stop it

**Defects:** #59 (the Portal sends interview times without a time zone and the Engine reads them 3 hours late).

**Cause:** `EngineRequestMapper.toSlot(LocalDateTime start, LocalDateTime end)` puts the local times into the request as
they are. The Engine reads a time without an offset as UTC, and the Portal works in Asia/Qatar (UTC+3), so a slot at
10:00 is checked as 13:00. The effects are visible to the user: a slot at 15:00 to 16:00 is refused with "Interview must
end by 18:00", and a slot at 05:00 to 06:00 is accepted. The request types are `Object` in `EngineDtos.Slot`, so nothing in
the Portal says that an offset is required. The two sides agree on neither the type nor the zone.

**Why it got past the vendor:** no test calls `toSlot`, and `InterviewServiceTest` mocks the Engine and so never sees
the values it receives. Only a test that runs the Portal and the Engine together shows the shift.

**Recommendations:**
- **P1:** send an `OffsetDateTime` built with the configured zone (`uniplace.zone`), or have the Engine refuse a time
  without an offset. Fixes #59 (and, by that, the messages that show the times 3 hours late).
- **P2:** type the `Slot` fields as `OffsetDateTime` instead of `Object`, so that the compiler finds the next case.
- **P3:** add one test that runs the Portal's schedule form against the real Engine (ours: TC-INT-010 and TC-INT-011, run
  by hand).

## RC7. The AI prompt is built from whole objects and was never reviewed

**Findings:**
- AI-03: the prompt contains the student's full name, student number and GPA.
- AI-10: the prompt contains an internal rule ("recruiters reject applications that score below 60"), the pay and the
  length of the internship.
- AI-11: the instructions contradict each other, and the questions prompt does not name the format the parser needs.

**Cause:** `PromptBuilder.postingAndLetter` receives the whole `AiRequestContext` and starts its text with a "Student:" line
built from `ctx.student()`. Nobody decided which fields the model needs; the prompt takes what is at hand. The feedback
instructions were edited without being read as a whole: "1 to 5 strengths" is followed by "always give exactly three", and
the example answer has `"score": 8.5` while the scale is an integer from 0 to 100 (all 99 answers of our run have exactly 3 strengths). The questions
prompt ends with "Reply with a list of good interview questions", while `AiResponseParser.parseQuestions` expects a JSON
object with a `questions` array of 5 strings. With a real model that parse is likely to fail, and the recruiter then sees
"Assistant unavailable". This is a risk from reading the code and has not yet been shown with a real model.

**Why it got past the vendor:** there is no `PromptBuilderTest`. `SimulatedProvider`, which the tests use, does not read the
prompt, and `AiClientTest` checks headers and answers, not the text sent.

**Recommendations:**
- **P1:** remove the "Student:" line and pass only the posting title, the required and nice-to-have skills and the letter.
  Fixes AI-03.
- **P3:** remove the internal note and the pay and length sentence. Fixes AI-10.
- **P3:** rewrite the instructions once: one number of items, an integer example, a scale with the meaning of each band,
  and for the questions the number 5 and the JSON shape. Fixes AI-11.
- **P2:** add a `PromptBuilderTest` that checks that the prompt for a student with a known name, number and GPA contains
  none of them, and contains the criteria, the scale, the item counts and the format.

## RC8. The AI call has no time-out and the answer is only partly validated

**Findings:**
- AI-05: there is no time limit on the provider call.
- AI-06: the parser accepts scores and lists that are outside the requirement.

**Cause:**
- `AiClient` builds its `HttpClient` without a connect time-out and its request without a read time-out. `EngineClient` sets
  3 seconds and 10 seconds, so the pattern exists and was not applied here. Our run shows the effect: 97 of 99 calls took
  longer than 10 seconds and the slowest took 62 seconds, so a student would wait that long on the application form.
- `AiResponseParser.list(root, field, min, max)` receives a minimum and a maximum and never uses them. `parseFeedback`
  takes `score.asInt()` without a range check, so 150, -5 and 8.5 (read as 8) are accepted, and an empty list or a list of
  ten items is accepted. `parseQuestions` asks for exactly 5 and does not check it. AI-06 says that partial results are
  never shown.

**Why it got past the vendor:** `AiClientTest` uses WireMock but never delays the answer, and `AiResponseParserTest` has
four tests: a valid answer, an answer in a code fence, text that is not JSON, and five questions. There is no invalid-value
test.

**Recommendations:**
- **P2:** set a connect time-out and a 10-second read time-out on the provider call, and turn the failure into the existing
  `AiUnavailableException`, so that the form shows "Assistant unavailable" and the application can still be submitted
  (fixes AI-05, and REL-03).
- **P2:** check the score range 0 to 100 as a whole number, and the number of items in each list, and refuse the answer
  otherwise (fixes AI-06).
- **P3:** note that with a 10-second limit a slow free model will fail most of the time. The model used in production has
  to answer inside the limit, and the baseline run shows that the free model we tested does not.
- **P2:** test both with WireMock: a fixed delay of 11 seconds, and an answer with a score of 150 and with 6 items.

## RC9. The AI score is not controlled for repeatability or for instructions in the letter

**Findings:**
- AI-09: six letters return scores that differ by more than 10 between runs (L06 12, L07 15, L16 13, L17 30, L18 20, L20
  15).
- AI-04: the prompt gives no protection against instructions inside the letter (not seen as a failure in our run).

**Cause:**
- `AiClient.TEMPERATURE` is a constant 1.3, which is a high value and makes the answers vary more. The prompt asks for a
  score "that reflects its overall quality" and gives no criteria or bands, so the model has no fixed scale to repeat. The
  spread is largest for the two letters with embedded instructions (L17 30, L18 20). These two reasons are likely but not
  yet proved: we have not repeated the run with a low temperature.
- The letter is added to the same system message as the instructions, with no separator and without a sentence that says
  the letter is data and not an instruction. In our run the two letters with embedded instructions (L17, L18) were not
  scored up, so the model held, but nothing in the Portal guarantees it.

**Why it got past the vendor:** `SimulatedProvider` returns the same result for the same letter, so no test could see variation,
and no test sends a letter with instructions.

**Recommendations:**
- **P2:** lower the temperature (for example 0 to 0.3) and add a scoring scale with bands to the prompt, then repeat the
  evaluation (ours: `AiEval.java --temperature 0.2`) and keep the setting only if AI-09 holds. We have not yet run this.
- **P2:** put the letter in its own message between markers, and state that anything inside it is text to assess and not an
  instruction. Keep the injection letters in the evaluation (L17, L18) as a regression check.
- **P3:** consider AI-09 itself: the same letter will not give the same score twice from any real model. The requirement
  could be met by the 10-minute cache (AI-08) plus a lower temperature, or be reworded to a statistical limit, and the
  team should raise this in the requirements review.
