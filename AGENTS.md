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
8. Run challenge review ("딴지").
9. Commit with template and handoff note.
10. Open/update PR to `develop` with `gh pr`.
11. Resolve coderabbitai/codex review feedback with follow-up commits.
12. Merge PR to `develop` only after checks/reviews are satisfied.

## Branch Policy
- `main`: final production code only
- `develop`: integration branch for completed work
- work branch: `feature|fix|chore/<issue-number>-<slug>` from `develop`

## Verification Gates
- `./gradlew check`
- `./gradlew test`
- `./gradlew build` (when dependency/config/cross-module impact exists)

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
