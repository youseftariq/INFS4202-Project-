# Q4. Defects found: Users & Access / Security, Interviews, AI Assistant (Yousef)

Draft for the final report (section Q4). It lists the 12 issues Yousef filed, and the findings of the AI Assistant
evaluation that are not yet filed. The full reports are in GitHub Issues. No report has been closed as a duplicate or
as invalid.

## Totals

| Severity | Count | Points if valid |
| --- | --- | --- |
| Critical | 4 | 12 |
| Major | 6 | 12 |
| Minor | 2 | 2 |
| Enhancement | 0 | 0 |
| **Total** | **12** | **26** |

| Category | Count | Issues |
| --- | --- | --- |
| security | 6 | #4, #5, #20, #26, #27, #28 |
| functional | 5 | #2, #3, #57, #58, #60 |
| integration | 1 | #59 |

| Area | Count | Issues |
| --- | --- | --- |
| Users & Access / Security | 7 | #3, #4, #5, #20, #26, #27, #28 |
| Interviews (Engine `/schedule/validate` and its use by the Portal) | 4 | #57, #58, #59, #60 |
| Applications (found while testing, outside our modules) | 1 | #2 |

**Critical defects:**
- **#4:** an invalid address (for example `/applications/abc`) shows an error page with the exception class names and a full
  stack trace.
- **#5:** a company name is shown unescaped on the postings page that every student sees, so HTML typed by a recruiter is
  interpreted by the browser.
- **#20:** any logged-in user, including a student or a recruiter, can open the Coordinator's Reports page and download its
  CSV files.
- **#26:** any logged-in user can read another student's whole application (motivation letter, offer, match score) by
  changing the number in the address.

**How they were found:**
- **Automated tests:** 10 of the 12 issues have a failing automated test:
  - 7 from Portal tests: #3, #4, #5, #20, #26, #27, #28 (`PasswordPolicyTest`, `SecurityConfigurationTest`,
    `AccessControlTest`).
  - 3 from Engine API tests (`ScheduleApiTest`): #57, #58, #60.
- **Manual testing:** #59 (error guessing: the time zone, on the running Portal) and #2 (boundary value: a GPA equal to the
  minimum).
- **Techniques:** access matrix (#20, #26, #27); error guessing combined with a code review (#4, #5, #28); equivalence
  partitioning (#3); boundary value analysis (#2, #57, #58, #60); error guessing (#59).
- **Reproduced on the running system:** all 12, by clicking through the steps in the issue after `--reset`. #57, #58 and
  #60 are also reproduced with the Engine alone, and #57, #58 and #59 through the Portal's schedule form on
  26/11/2026.

## Issues

| Issue | Title | Category | Severity | Requirement | Discovered by |
| --- | --- | --- | --- | --- | --- |
| #3 | Changing a password does not enforce the "including a digit" rule | functional | Minor | USR-02 | Yousef |
| #4 | Error pages show exception class names and a full stack trace | security | Critical | SEC-03 | Yousef |
| #5 | Company name is displayed unescaped on the postings list | security | Critical | SEC-07 | Yousef |
| #20 | Reports page and CSV exports are open to students and recruiters | security | Critical | USR-05, SEC-05 | Yousef |
| #26 | Any logged in user can open another student's application by changing the id in the address | security | Critical | USR-05, SEC-05 | Yousef |
| #27 | A recruiter can download the documents (CV, transcript) of any student, including students who never applied to their company | security | Major | USR-05, SEC-05 | Yousef |
| #28 | CSRF protection is disabled for the Coordinator settings form | security | Major | SEC-02 | Yousef |
| #57 | Schedule: a slot of exactly 120 minutes is refused | functional | Minor | INT-01, INT-05 | Yousef |
| #58 | Schedule: back-to-back interviews of the same recruiter are refused as overlapping | functional | Major | INT-03, INT-05 | Yousef |
| #59 | Schedule: the Portal sends interview times without a time zone; the Engine reads them 3 hours late | integration | Major | INT-02, INT-05 | Yousef |
| #60 | Engine `/schedule/validate`: an interview that ends after 18:00 is accepted | functional | Major | INT-02, INT-05 | Yousef |
| #2 | Eligibility: a student whose GPA equals the minimum GPA is rejected | functional | Major | APP-04 | Yousef |

The severities are our own. The instructor confirms them when scoring R5.

## AI Assistant: findings not yet filed

The evaluation and a review of `PromptBuilder`, `AiClient` and `AiResponseParser` found six problems against the AI
requirements. None is filed yet. The severities are proposals. A finding becomes an issue only once it has steps that a
grader can repeat after `--reset`.

| Requirement | Finding | How it was found | Proposed severity | Status |
| --- | --- | --- | --- | --- |
| AI-09 | Six of 20 letters return scores that differ by more than 10 between runs (L06 12, L07 15, L16 13, L17 30, L18 20, L20 15) | Evaluation run, 99 answers | Major | Proved by the data in `tests/ai-eval/` |
| AI-05 | The Portal has no time-out for the provider call; 97 of 99 calls took longer than 10 s and the slowest took 62 s | Evaluation run and code review: `AiClient` sets no time-out, while `EngineClient` sets 3 s and 10 s | Major | Code and data agree; not yet shown with a delaying provider (`AiCapture.java`) |
| AI-03 | The prompt contains the student's full name, student number and GPA (`PromptBuilder.postingAndLetter`) | Code review | Critical | Not yet captured on the running Portal |
| AI-10 | The prompt contains an internal rule ("recruiters reject applications that score below 60") and the pay and length of the internship | Code review | Minor | Not yet captured on the running Portal |
| AI-11 | The instructions contradict each other: "1 to 5 strengths" and "always give exactly three"; the example answer has a score of 8.5 on an integer scale 0 to 100; the interview-questions prompt names neither the number of questions nor the JSON format that the parser needs | Code review | Minor | Not yet shown with a real model for the questions prompt |
| AI-06 | The parser accepts any score (150, -5, or 8.5, which becomes 8) and any number of items in a list, because the minimum and maximum it is given are not used | Code review | Major | Not yet shown on the running Portal |

**Checked and not found as a defect in this run:**
- AI-04 (instructions in a letter): the scores of the two letters with instructions stayed inside or below their bands,
  and no injected score was followed. This depends on the model, because the prompt has no protection (see Q5).
- AI-03 on the answer side: no answer repeated the phone number, e-mail, ID number or address of the letters with
  personal data.
- AI-07 and AI-08 (source shown, cache of 10 minutes) were not tested in this round.

## Open items

- File the six AI findings with steps, or drop the ones that cannot be reproduced. Until then they count as findings,
  not as defects.
- #2 was found while testing the application form and belongs to Applications; Qudama owns that module.
