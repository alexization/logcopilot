# AGENTS.md

## Purpose
Execution table of contents for AI work in this repository.
Domain knowledge is documented under `docs/domain/`.

## Read Order (Mandatory)
1. `docs/domain/source-of-truth.md`
2. `docs/domain/product-direction.md`
3. `docs/api/openapi-selfhosted-byom-v1.yaml`
4. `docs/domain/architecture-guardrails.md`
5. `docs/workflow/github-delivery.md`
6. `docs/workflow/flow.md`
7. `docs/workflow/rule-base.md`
8. `docs/workflow/tdd.md`
9. Active spec under `docs/specs/`
10. Templates/checklists under `docs/templates/` and `docs/checklists/`

## Execution Loop (Index)
1. Create or confirm GitHub issue with `gh issue`.
2. Create feature branch from `develop`.
3. Pick one task ID from active spec.
4. Confirm MVP scope and non-goals.
5. Follow RED -> GREEN -> REFACTOR.
6. Execute rule-base mapping (`AC -> rule -> command`).
7. Run verification gates.
8. Run parallel challenge sub-agents ("딴지") for:
   - side-effect/regression risk
   - syntax/static quality
   - task/AC requirement fit
   - test correctness/completeness
9. If any sub-agent fails, fix and repeat Step 7-8 until all pass.
10. Commit with template and handoff note.
11. Open/update PR to `develop` with `gh pr`.
12. Resolve coderabbitai/codex review feedback with follow-up commits.
13. Merge PR to `develop` only after checks/reviews are satisfied.

## Branch Policy
- `main`: final production code only
- `develop`: integration branch for completed work
- work branch: `feature|fix|chore/<issue-number>-<slug>` from `develop`

## Verification Gates
- `./gradlew check`
- `./gradlew test`
- `./gradlew build` (when dependency/config/cross-module impact exists)

## Delivery Text Integrity Gate (Mandatory)
- Validate Issue/Commit/PR text with `./scripts/verify-delivery-text.sh`.
- Validate template section completeness and text integrity:
  - no literal `\n` in body
  - no broken replacement character (`�`)
- Recommended commands:
  - `./scripts/verify-delivery-text.sh issue <issue-number>`
  - `./scripts/verify-delivery-text.sh commit HEAD`
  - `./scripts/verify-delivery-text.sh pr <pr-number>`

## Pre-PR Sub-agent Gate (Mandatory)
- Do not create or update PR until all challenge sub-agents return PASS.
- Sub-agents must run in parallel and use the same latest commit snapshot.
- Required pass domains:
  - side-effect/regression risk
  - syntax/static quality
  - task/AC requirement fit
  - test correctness/completeness
- Any FAIL requires returning to implementation + verification loop.

## Templates
- Spec: `docs/templates/tech-spec.template.md`
- Session note: `docs/templates/session-task.template.md`
- Commit: `docs/templates/commit-message.template.md`
- Issue: `.github/ISSUE_TEMPLATE/work-unit.md`
- PR: `.github/PULL_REQUEST_TEMPLATE.md`
- Verification checklist: `docs/checklists/verification-checklist.md`

## Change Control
If work conflicts with domain guardrails or MVP freeze, stop and raise RFC before implementation.

## Language Policy
- GitHub Issue, PR, Commit 메시지는 한국어로 작성한다.
