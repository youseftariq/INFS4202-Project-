# Q3. Test design and execution: Users & Access / Security, Interviews, AI Assistant (Yousef)

Draft for the final report (section Q3). Test cases: `docs/test-cases.md`. How to run the tests: `tests/README.md`.

## Why Users & Access / Security is a primary module

Users & Access / Security (USR-01 to USR-05, SEC-01 to SEC-07) carries the two risks of the strategy that can hurt real
people, and both are rated high for impact:
- R1: a user reads or changes another user's or another company's data. Ownership checks are written by hand in
  individual controllers, so one missed call is a hole.
- R6: security misconfiguration (CSRF, error details, unescaped text, secrets).

It is also the module every other module depends on: each use case starts with a login, and every page has to decide
who may open it. Interviews (INT-01 to INT-05) is covered at the Engine endpoint `/schedule/validate`, and the AI
Assistant (AI-01 to AI-11) by an evaluation script, because both are risks the strategy rates high (R8, R9).

## Test cases

33 test cases: 21 for the primary module (SEC) and 12 for Interviews (INT). The AI Assistant is tested by an evaluation
run (see below) and its test cases are not yet in the matrix.

| Module | Test cases | Black-box | White-box | Functional | Non-functional | Pass | Fail | Not run |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Users & Access / Security (SEC) | 21 | 11 | 10 | 11 | 10 | 10 | 9 | 2 |
| Interviews (INT) | 12 | 12 | 0 | 10 | 2 | 6 | 5 | 1 |
| **Total** | **33** | **23** | **10** | **21** | **12** | **16** | **14** | **3** |

**Classification used:**
- **White-box:** the test case was designed from the code (code review, code inspection, or a unit test of a class). A
  case that combines a black-box test with a code review counts as white-box. These are TC-SEC-004, 005, 010, 015, 016,
  017, 018, 019, 020 and 021.
- **Non-functional:** the test case verifies a security or reliability requirement (SEC, REL). These are TC-SEC-004,
  005, 006, 007, 008, 009, 010, 015, 016, 020, and TC-INT-008 and 012.

**Techniques:**

| Technique | Users & Access / Security | Interviews |
| --- | --- | --- |
| Boundary value analysis | 7 (TC-SEC-003, 011, 012, 015, 017, 018, 021) | 4 (TC-INT-001, 003, 004, 006) |
| Equivalence partitioning | 2 (TC-SEC-001, 013) | 3 (TC-INT-002, 005, 009) |
| Decision table or access matrix | 5 (TC-SEC-006, 007, 008, 009, 019) | 1 (TC-INT-007) |
| Error guessing, robustness, fault injection | 6 (TC-SEC-002, 004, 005, 010, 014, 020) | 3 (TC-INT-008, 011, 012) |
| Use case test | 0 | 1 (TC-INT-010) |
| Code inspection | 1 (TC-SEC-016) | 0 |

TC-SEC-015 and TC-SEC-018 combine boundary values with equivalence classes, so each is listed once, under boundary value
analysis. Every other test case appears in one row.

**Levels:** SEC has 17 system, 3 unit and 1 static (code review); INT has 9 API and 3 system. "System" in the matrix
names the behaviour that was tested on the running Portal; the automated check for most of these cases runs in
MockMvc with the Spring context, so it needs no browser. **Automation:** 25 test cases are automated (16 SEC, 9 INT), and
8 are run by hand: TC-SEC-013, 014, 016, 020, 021 and TC-INT-010, 011, 012 (TC-INT-010 and 011 need the Portal and the
Engine together).

**Requirement coverage.** Every requirement of the module has at least one test case, except USR-01 (three roles, login),
which has no dedicated case because every case logs in with a different role. SEC-04 (secrets in logs) and USR-04 (the
30-minute time-out) are designed but not yet run (TC-SEC-020, 021).

## Automated tests

| Suite | Level | Tool | Tests | Defect tests | Notes |
| --- | --- | --- | --- | --- | --- |
| `web/AccessControlTest` | Integration (Portal, black-box) | JUnit 5, Spring Boot Test, MockMvc | 12 | 7 | Access matrix, CSRF and escaping; failures are #5, #20, #26, #27, #28 |
| `web/SecurityConfigurationTest` | Configuration check (white-box) | JUnit 5, reads `application.properties` | 2 | 2 | Reads the error settings; failures are #4 |
| `service/PasswordPolicyTest` | Integration (Portal service + H2) | JUnit 5, Spring Boot Test | 5 | 1 | Password rule at 9 and 10 characters, with and without a digit; failure is #3 |
| `security/LoginAttemptServiceTest` | Unit (white-box; service + H2) | JUnit 5, Spring Boot Test | 6 | 0 | 4, 5 and 6 failures, lock for 15 minutes, unknown user |
| `service/AccessGuardTest` | Unit (white-box; service + H2) | JUnit 5, Spring Boot Test | 10 | 0 | Decision table of role against record owner |
| `service/FileNameSanitiserTest` | Unit (white-box) | JUnit 5 | 4 | 0 | File name sanitising |
| `tests/api` `ScheduleApiTest` | API (black-box, Engine) | JUnit 5, Java `HttpClient`, Jackson | 13 | 3 | Starts the Engine jar on a free port; failures are #57, #58, #60 |
| `tests/ai-eval` `AiEval` | System (AI evaluation) | Java 17, no libraries | 20 letters x 5 runs | not applicable | See the next section |

The Portal classes together have 39 tests, and 10 of them are tagged `defect`. A defect test fails on purpose while its
issue is open, and the failure is the evidence. TC-INT-011 (issue #59, the time zone) has no automated test, because the
fault is in how the Portal builds the request, so it is shown only on the running system. The matrix gives the result
of every test case.

**How to run:**
- **Our Portal classes:** `cd portal; .\mvnw.cmd test "-Dtest=AccessControlTest,SecurityConfigurationTest,PasswordPolicyTest,LoginAttemptServiceTest,AccessGuardTest,FileNameSanitiserTest" "-Dmaven.test.failure.ignore=true"`.
- **Engine API:** `cd portal; .\mvnw.cmd -f ..\tests\api\pom.xml test "-Dtest=ScheduleApiTest"`.
- **AI evaluation:** `java tests/ai-eval/AiEval.java --base-url <url> --model <model id>`, with the key in the environment
  variable `AI_API_KEY`. See `tests/ai-eval/README.md`.

## AI Assistant: evaluation design and results

The assistant calls a language model, so its answers are not repeatable and cannot be tested with one expected value.
`AiEval.java` sends the Portal's own prompt (same text, temperature 1.3) to a provider and checks the answers.

| Item | Value |
| --- | --- |
| Letters | 20: 6 strong, 6 medium, 4 weak, 2 with embedded instructions (L17, L18), 2 with personal data (L19, L20) |
| Reference band | A score range for each letter, set by a reviewer (a judgement, not exact truth) |
| Runs | 5 per letter; 99 valid answers (L12 has 4, because the free model has a daily limit); 2 failed attempts were retried |
| Model | `nvidia/nemotron-3-ultra-550b-a55b:free` through OpenRouter, run on 24/11/2026 and 26/11/2026 |

| Requirement | Check | Result |
| --- | --- | --- |
| AI-01, AI-06 | The answer parses and has a score and 1 to 5 items in each list | 99 of 99 answers parsed. All 99 have exactly 3 strengths |
| AI-01 | The score is inside the reference band | 82 of 99 runs (83%). The 17 misses are all below the band, none above; medium letters L07 to L09 and L12 are scored low most often |
| AI-09 | The spread of the 5 scores of one letter is at most 10 | Broken by 6 letters of 20: L06 (12), L07 (15), L16 (13), L17 (30), L18 (20), L20 (15) |
| AI-05 | The answer arrives within 10 seconds | 97 of 99 calls took longer (average 19 s, slowest 62 s). This is a property of the free model, and it shows that the Portal needs a time-out |
| AI-04 | A letter with instructions is scored on its content | Held in this run: L17 scored 15 to 45 (band 10 to 45) and L18 scored 35 to 55 (band 50 to 74); no injected score was followed. The prompt has no protection, so this depends on the model |
| AI-03 | The feedback does not repeat personal data of the letter | Held in this run: no answer repeats the phone number, e-mail, ID number or address of L19 and L20. The check on the request the Portal sends is a separate test (see Q4) |

The AI-03, AI-10 and AI-11 requirements are about what the Portal sends, not about what the model answers, so they are
tested by inspecting the prompt in `PromptBuilder` and, on the running Portal, with `AiCapture.java`, a fake provider that
prints every request. The findings are in Q4 and Q5.

## Open items in this section

- TC-SEC-020 (secrets in logs), TC-SEC-021 (30-minute time-out) and TC-INT-012 (Engine down while scheduling) are
  designed but not yet run.
- Test cases for AI-01 to AI-11 are not yet in `docs/test-cases.md`; the evaluation results above are their evidence.
- Code coverage (JaCoCo) is not yet measured for the security classes.
- Public holidays (INT-02) are not checked by the Engine or by the Portal, so they cannot be tested.
