# Verification Checklist

Use this checklist before closing each task.

## Requirement Fit
- [ ] Source-of-truth priority checked (`docs/domain/source-of-truth.md`).
- [ ] Task is in MVP scope (`docs/domain/product-direction.md`).
- [ ] No architecture invariant is violated (`docs/domain/architecture-guardrails.md`).
- [ ] Implementation matches current task goal.
- [ ] Non-goals were not accidentally implemented.
- [ ] Acceptance criteria for this task are all satisfied.
- [ ] Every AC is mapped to at least one executable rule.
- [ ] API changes (if any) were synced with `docs/api/openapi-selfhosted-byom-v1.yaml`.

## TDD / Tests
- [ ] RED confirmed: tests failed before production code changes.
- [ ] GREEN confirmed: tests pass after implementation.
- [ ] REFACTOR performed with tests still green.
- [ ] New behavior has tests.
- [ ] Changed behavior has updated tests.
- [ ] Negative or edge cases are covered.

## Fast Feedback Gates
- [ ] `./gradlew check` passed.
- [ ] `./gradlew test` passed.
- [ ] `./gradlew build` passed (when required).

## Challenge Review ("딴지")
- [ ] Challenge sub-agents ran in parallel on the latest commit snapshot.
- [ ] Side-effect/regression reviewer: PASS.
- [ ] Syntax/static quality reviewer: PASS.
- [ ] Task requirement/AC fit reviewer: PASS.
- [ ] Test correctness/completeness reviewer: PASS.
- [ ] All FAIL findings were fixed, then gates + sub-agents were re-run.
- [ ] Findings were resolved or tracked explicitly.

## Git as Execution History
- [ ] Commit includes task ID and technical rationale.
- [ ] Commit includes verification results.
- [ ] Commit includes next-step handoff for next session.

## GitHub Delivery
- [ ] GitHub issue exists for this work unit.
- [ ] Issue 본문이 한국어로 작성되었다.
- [ ] Feature branch was created from `develop`.
- [ ] `./scripts/verify-delivery-text.sh issue <issue-number>` passed.
- [ ] `./scripts/verify-delivery-text.sh commit HEAD` passed.
- [ ] PR was created only after all challenge sub-agents were PASS.
- [ ] PR targets `develop` and links issue (`Closes #...`).
- [ ] PR 제목/본문이 한국어로 작성되었다.
- [ ] `./scripts/verify-delivery-text.sh pr <pr-number>` passed.
- [ ] Issue/Commit/PR 본문에 깨진 문자(`�`) 또는 literal `\n` 노출이 없다.
- [ ] coderabbitai/codex feedback reviewed and handled.
- [ ] Follow-up commits were added for accepted review changes.
- [ ] Commit 메시지가 한국어로 작성되었다.
- [ ] PR checks are green before merge.
