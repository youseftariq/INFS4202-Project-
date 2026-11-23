# Test design

A spreadsheet `docs/test-cases.xlsx` may replace this file. At least 10 test cases for each of the three primary modules.

## Test case matrix

| Test case ID | Requirement | Module | Level | Technique | Preconditions | Steps | Test data | Expected result | Actual result | Status | Automated test | Issue |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |  |  |  |  |  |  |
| TC-SEC-001 | USR-02 | Users & Access / Security | System (manual, black box) | Equivalence partitioning | Fresh start with --reset; logged in as student.amal | 1. Open /account/password<br>2. Enter current, new and repeat password<br>3. Click Change password<br>4. Log out and log in with the new password | Current: Uniplace!2026; new and repeat: abcdefghijkl (12 letters, no digit) | The change is refused with a message about at least 10 characters including a digit | The page shows "Password changed."; login with abcdefghijkl works (a password without a digit is accepted) | FAIL | None yet| #3 |
| TC-SEC-002 | USR-03 | Users & Access / Security | System (manual, black box) | Error guessing | Fresh start with --reset | 1. On the login page type username student.noor (capitals) and a wrong password 6 times<br>2. Log in with the correct password | Username: STUDENT.NOOR; wrong password: wrongpass1; correct password: Uniplace!2026 | The account is locked after 5 failed attempts and the correct password is refused for 15 minutes | The message "This account is locked for 15 minutes after 5 failed attempts." appeared and the correct password was refused | PASS | None yet  | - |

## Requirement to test traceability matrix

| Requirement | Test cases | Automated tests | Issues found | Covered? |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |
