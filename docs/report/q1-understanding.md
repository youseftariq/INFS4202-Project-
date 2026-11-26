# Q1. How did we understand the system?

> Team draft by Salman, from `docs/test-strategy.md` sections 1–2 and our issues.
> Qudama and Yousef: please check the rows for your endpoints and modules.

## Our model of UniPlace

UniPlace has three parts:
- **Portal** (white-box): a Spring Boot web application.
- **Engine** (black-box): a separate REST service.
- **AI provider:** an OpenAI-compatible service that the Portal calls for letter feedback, with a built-in simulator when
  no key is set.

```
 Browser: Student · Recruiter · Coordinator
      │  HTML forms (Thymeleaf), CSRF token on every POST
      ▼
 Portal :8080 ── web/ → service/ (business rules, state machine, access checks) → repository/ → H2 database
      │                    │
      │                    ├── engine/ EngineClient + EngineRequestMapper ──► Engine :8081 (stateless)
      │                    │                                                /eligibility /match /rank
      │                    │                                                /schedule/validate /allowance
      │                    └── ai/ PromptBuilder → AiClient → AiResponseParser ──► AI provider or simulator
      └── Outbox: every notification is recorded in the database (e-mail only when SMTP is set)
```

**The Portal** owns the UI, the business rules, the database, access control and the Settings:
- `ApplicationService` and `ApplicationStateMachine` hold the application rules.
- `OfferService` holds offers, seats and the allowance statement.
- `AccessGuard` and `SecurityConfig` hold the access rules.
- `SettingsService` holds the values the Coordinator can change.

**The Engine is stateless.** The Portal sends it everything it needs in each request, including the Settings values
(limits, transport supplement, cap and penalty rate). So an Engine defect shows up in the Portal, and a Portal defect
can hide behind a correct Engine.

## Main flows per role

| Role | Main flow | Engine calls |
| --- | --- | --- |
| Student | Completes the profile and uploads a CV and transcript → applies with a motivation letter → follows the application → accepts or declines an offer → sees the allowance statement | `/eligibility` and `/match` on every application |
| Recruiter | Publishes postings → shortlists candidates (a justification is needed below the threshold) → schedules interviews → issues an offer to an INTERVIEW application | `/rank` for the candidate list, `/schedule/validate` for each interview slot |
| Coordinator | Approves or suspends companies → edits Settings → records the date the signed placement agreement arrived (the allowance is recalculated with the late penalty) → views reports, the Outbox and the audit log | `/allowance` when an offer is accepted and when the agreement is recorded |

## How the Portal uses the Engine, and what we found at that seam

| Endpoint | What the Portal sends | What we found |
| --- | --- | --- |
| `/eligibility` | GPA, credits, standing, prior internships, minimums, documents | The posting's own minimum GPA and the prior internships are never sent (#51); when the Engine is down the student is treated as eligible (#39) |
| `/match` | Student skills, required and nice-to-have skills | The score is cached per posting, so every applicant gets the first applicant's score (#40) |
| `/rank` | Candidates (score, GPA, submission time) and seats | The total seats are sent instead of the seats still free (#54) |
| `/schedule/validate` | The new slot's start and end, plus the recruiter's and the student's existing slots | Times are sent without a time zone and read 3 hours late (#59); back-to-back slots are refused as overlapping (#58) |
| `/allowance` | Allowance, dates, weekly hours, late days and the Settings values | The monthly cap from Settings is never sent (#15); when the Engine is down an offer is still accepted with no allowance (#41) |

## What we inferred about the Engine

We have no Engine source code. We worked from `engine/openapi.yaml` and from the Engine's answers to our API tests:
- **It answers instantly for simple requests.** Eligibility and match take under 1 ms at the 95th percentile (TC-PERF-001).
- **Its errors sit on boundaries:**
  - A GPA equal to the minimum is refused (#2), and so is a transcript on its 180th day (#33).
  - The transport supplement starts at 31 hours instead of 30 (#13).
  - A 120-minute interview slot is refused (#57), and one that ends after 18:00 is accepted (#60).
  - A month that starts on the 31st counts as 1.03 months (#16).
- **It does not validate its input** as the contract promises:
  - It answers 500 for a missing field (#14).
  - It accepts a GPA above 4.00 or below 0 (#37).
- **Its formulas differ from the Requirements:**
  - The monthly cap is never applied (#12).
  - Skills are compared case-sensitively (#32).
  - An empty nice-to-have list adds 30 points (#34).
  - The GPA tie-break is ignored (#35).
  - `aboveCut` holds one candidate too many (#36).
- **Its ranking is expensive.** 200 candidates take 0.9 s to 3.2 s depending on the data, and 13 s when every candidate
  ties. The time grows much faster than the number of candidates (#21), which suggests the Engine does more than one
  sort.

## How we built this understanding

1. **Requirements and the contract.** We read the Requirements and `engine/openapi.yaml`, and listed the rules each
   module must follow. The requirements review alone found 3 specification defects (#19, #23, #24).
2. **Reverse engineering the Portal.** We read the source package by package and wrote each suspicious line down as a
   lead, for example the static fields in `SettingsService` and the swallowed exception in `tryComputeAllowance`. A lead
   only became an issue after a failing test or a reproduction on the running system.
3. **Exploration.** We ran the system in all three roles with the seeded accounts, always after `--reset`, because the
   demonstration data is placed around the day of the start.
4. **API probes.** We started with curl, then moved to JUnit tests that start the Engine jar themselves (`tests/api`).
5. **Measurements.** We used JaCoCo coverage to find untested paths (TC-OFF-031 found #41) and k6 to measure response
   times.
