# MVP v1 Work Unit Bootstrap Technical Spec

## Metadata
- Date: 2026-03-03
- Owner: Codex
- Status: In Progress
- Related issue/PR: Issue #6
- Domain baseline refs:
  - `docs/domain/source-of-truth.md`
  - `docs/domain/product-direction.md`
  - `docs/api/openapi-selfhosted-byom-v1.yaml`
  - `docs/domain/architecture-guardrails.md`

## 1. Background
The repository is currently a Spring Boot skeleton with no MVP API implementation.
An active technical spec and one delivery issue are required before implementation work starts.

## 2. Goals
- G1: Define one active spec that freezes MVP in-scope implementation order.
- G2: Establish executable AC-to-rule mapping and task IDs for iterative delivery.
- G3: Open one GitHub work-unit issue and create a feature branch from `develop`.

## 3. Non-Goals
- NG1: Implementing production API endpoints in this task.
- NG2: Introducing connectors other than Loki.
- NG3: Expanding MVP scope beyond OpenAPI/product-direction guardrails.

## 4. Scope
### In Scope
- Create active spec under `docs/specs/`.
- Define task IDs for upcoming work units.
- Create/confirm GitHub issue for this bootstrap work unit.
- Create feature branch from `develop` using issue number.

### Out of Scope
- Controller/service/repository implementation.
- Database schema migration beyond baseline setup discussion.
- PR creation and merge for downstream tasks.

### MVP Scope Gate
- Why this spec is in-scope for MVP:
  - It is a prerequisite to start contract-first implementation without drift.
- Which frozen decisions are respected:
  - Loki-only connector scope.
  - Java 21 + Spring Boot 3.x backend.
  - OpenAI/Gemini only for MVP LLM providers.
  - Slack/Email alert scope.

## 5. Constraints
- Technical constraints:
  - API behavior must follow `docs/api/openapi-selfhosted-byom-v1.yaml`.
  - Core must preserve canonical pipeline guardrails.
- Time/resource constraints:
  - Each task should be session-sized and commit-sized.
- Compatibility constraints:
  - No breaking changes against current OpenAPI MVP contract.

## 6. Acceptance Criteria (Definition of Done)
- [x] AC1: Active spec exists with goals/non-goals/scope/AC/rule mapping/task IDs.
- [x] AC2: Upcoming implementation tasks are explicitly enumerated with done signals.
- [x] AC3: GitHub work-unit issue exists and references this spec.
- [x] AC4: Feature branch from `develop` follows naming policy `feature/<issue-number>-<slug>`.

## 7. Rule-Base Mapping (Mandatory)
| AC ID | Rule Type (Unit/Integration/E2E/Build/Static) | Command | Pass Condition |
| --- | --- | --- | --- |
| AC1 | Static | `test -f docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md` | exit code 0 |
| AC1 | Static | `rg -q "^## 2\\. Goals$" docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md && rg -q "^## 3\\. Non-Goals$" docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md && rg -q "^## 6\\. Acceptance Criteria" docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md && rg -q "^## 8\\. Task Breakdown$" docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md` | exit code 0 |
| AC2 | Static | `rg "^\\| T-0[0-9]|^\\| T-1[0-1]" docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md` | task rows found |
| AC3 | Static/GitHub | `gh issue view 6 --json state --jq '.state' | rg -q '^OPEN$' && gh issue view 6 --json body --jq '.body' | rg -q 'docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md'` | exit code 0 |
| AC4 | Static/Git | `git merge-base --is-ancestor develop feature/6-mvp-bootstrap-spec-wu00` | exit code 0 |

## 8. Task Breakdown
| Task ID | Description | Done Signal | Required Tests | Risk |
| --- | --- | --- | --- | --- |
| T-00 | Bootstrap active spec + GitHub issue + branch | Spec file created, issue open, feature branch created | Static checks + `./gradlew check` + `./gradlew test` | Low |
| T-01 | Core skeleton + system endpoints (`/healthz`, `/readyz`, `/v1/system/info`) | Contract tests and basic auth path pass | Unit + web integration | Medium |
| T-02 | Project APIs (`POST/GET /v1/projects`) | Create/list project behaviors pass | Unit + integration | Medium |
| T-03 | Loki connector upsert/test APIs | Connector upsert/test success+failure paths pass | Unit + integration | Medium |
| T-04 | Ingest push path + idempotency + OTLP reserved behavior | `/v1/ingest/events` accepted with idempotency; OTLP reserved path handled | Unit + integration | High |
| T-04R | Pre-T05 OOP refactor (auth guard + ingest domain separation) | Shared auth validation, ingest domain policy/store separation, unit tests pass | Unit + integration + regression | High |
| T-05 | Incident listing/detail/reanalyze APIs | Incident read/reanalyze flows pass | Integration + regression | High |
| T-06 | LLM account APIs (API key + OAuth start/callback + list/delete) | OpenAI/Gemini account lifecycle passes | Unit + integration | High |
| T-07 | Analyzer chain (`rule -> llm -> fallback`) | Analyzer fallback behavior verified | Unit + integration | High |
| T-08 | Policy APIs + secret/redaction guardrails | Policy update and redaction guarantees verified | Unit + integration + security checks | High |
| T-09 | Alert channel APIs (Slack/Email) + audit log list | Channel config and audit retrieval pass | Unit + integration | Medium |
| T-10 | Reliability hardening + mandatory gate closure | Performance/reliability checks documented and gates green | `check`/`test`/`build` (if needed) | Medium |
| T-11 | PR review feedback loop and merge readiness | All blocking review comments resolved, PR ready for merge to `develop` | Full required gates | Medium |

Rule: one task should fit in one session and one commit.

## 9. TDD Plan (Mandatory)
- RED tests to write first:
  - For each task, define endpoint behavior from OpenAPI and write failing tests first.
- Expected RED failure signal:
  - Missing endpoint/bean/validation failures before implementation.
- GREEN implementation boundary:
  - Implement only behavior needed for current task AC.
- Refactor boundary:
  - Naming, modularization, and error handling improvements without behavior drift.

## 10. Test Strategy
- Unit:
  - DTO validation, service logic, policy and security helpers.
- Integration:
  - Controller contract tests and persistence interactions.
- Regression:
  - Incident deduplication, fallback analyzer path, and API error schema.

## 11. Decision Log
- Decision:
  - Use task-by-task contract-first delivery from OpenAPI path groupings.
  - Reason:
    - Minimizes scope drift and improves verification traceability.
  - Tradeoff:
    - More upfront spec overhead before feature code.

## 12. Open Questions
- Q1:
  - Persistence schema naming/versioning convention for early entities.
- Q2:
  - OAuth callback local dev strategy (stub vs full provider integration).
