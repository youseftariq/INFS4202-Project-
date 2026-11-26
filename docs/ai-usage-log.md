# AI usage log

One entry per meaningful use.

| Date | Member | Tool and model | Purpose | Prompt summary | Kept | Modified (how) | Rejected (why) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-23 | Yousef | Claude (claude.ai chat) | Write white-box and regression tests (6 JUnit classes) | Asked for tests for LoginAttemptService, UserService, AccessGuard, and for the security defects | Tests that compiled and ran: 45 new, 10 fail on purpose (defect tests) | Fixed the test-case numbering to match my matrix | None |
| 24/11/2026 | Qudama | Claude (Anthropic), Opus 5.5 | Design the Applications (APP) test cases and probe the Engine's /eligibility, /match and /rank | Asked Claude to read the Requirements, the Portal code and the OpenAPI file, design at least 10 TC-APP cases with mixed techniques, and run the Engine checks | 30 test cases TC-APP-001 to 030, traceability rows, and Engine results for TC-APP-018 to 025 and 028 | Checked each case against the Requirements before keeping it | Portal rows not accepted as defects until I run them myself |

## Reflection
