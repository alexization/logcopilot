# End-to-End Flow

## 0) Spec Setup
- Check `docs/domain/source-of-truth.md` first.
- Then check `docs/domain/product-direction.md` and `docs/domain/architecture-guardrails.md`.
- Write technical spec first.
- Fix scope with:
  - Goals
  - Non-goals
  - Acceptance criteria
  - Task breakdown with IDs and done signals
- Map each acceptance criterion to executable rules (`AC -> rule -> command`).

## 1) Delivery Setup (Issue + Branch)
- Create/confirm one GitHub issue for this work unit (`gh issue`).
- Validate issue body template/integrity:
  - `./scripts/verify-delivery-text.sh issue <issue-number>`
- Sync local `develop` and create feature branch from it.
- Branch naming: `feature/<issue-number>-<slug>` (or `fix|chore`).

## 2) Session Kickoff (One Task Only)
- Pick one task ID from spec.
- Confirm:
  - What to do now
  - What not to do now
  - Task-level definition of done
  - MVP in-scope check passed
  - RED test plan for this task

## 3) RED (Tests First)
- Write or update tests before implementation.
- Run tests to confirm expected failure.
- Record failing evidence in session note.

## 4) GREEN (Minimal Implementation)
- Implement the smallest change that makes RED tests pass.
- Avoid broad refactors unless task scope requires them.

## 5) REFACTOR
- Improve readability, naming, failure handling, and design.
- Keep all tests green during refactor.
- Ensure implementation still matches spec and non-goals.

## 6) Verification
- Run hard gates:
  - `./gradlew check`
  - `./gradlew test`
  - `./gradlew build` when impact is cross-cutting
- If failed, generate a short bug report:
  - observed issue
  - expected behavior
  - likely root cause

## 7) Correction Loop (Repeat 5-10 Times If Needed)
- Apply minimal fix.
- Re-run failed checks first, then full required gates.
- Stop only when all task-level criteria are satisfied.

## 8) Parallel Sub-agent Challenge Gate (Mandatory Before PR)
- Run challenge sub-agents in parallel on the same latest commit snapshot.
- Required sub-agents:
  - side-effect/regression reviewer
  - syntax/static quality reviewer
  - task requirement/AC fit reviewer
  - test correctness/completeness reviewer
- Every sub-agent must return `PASS` or `FAIL` with evidence.
- Do not proceed unless all sub-agents return `PASS`.

## 9) Feedback Loop from Sub-agent Findings
- If one or more sub-agents return `FAIL`:
  - create short bug reports (observed/expected/likely root cause)
  - apply minimal fixes
  - rerun Step 6 hard gates
  - rerun Step 8 parallel sub-agents
- Repeat until all sub-agents are `PASS`.

## 10) Commit + Handoff
- One task, one commit.
- Use commit template and include verification results.
- Commit message must be written in Korean.
- Validate latest commit message template/integrity:
  - `./scripts/verify-delivery-text.sh commit HEAD`
- Record next-session handoff note:
  - current status
  - remaining risks
  - exact next task ID
  - RED -> GREEN evidence summary

## 11) PR to `develop` (`gh pr`)
- Open or update PR from feature branch to `develop`.
- PR body must include linked issue, tests, risk, and rollback notes.
- Issue/PR title and body must be written in Korean.
- Validate PR body template/integrity:
  - `./scripts/verify-delivery-text.sh pr <pr-number>`
- PR can be opened only after Step 8 and Step 9 are fully passed.

## 12) Review Feedback Loop (Mandatory)
- Collect automated reviews from coderabbitai, codex.
- Apply accepted feedback with additional commits.
- Respond on PR with change rationale.
- Repeat until blocking review comments/checks are resolved.

## 13) Merge + Close
- Merge PR into `develop` via `gh pr`.
- Confirm issue is closed (auto via `Closes #...` or manual close).

## Pseudocode
```text
ensure_issue_and_feature_branch()
verify_delivery_text(issue)
for each task in spec.tasks:
  clarify(task.goal, task.non_goal, task.done)
  write_red_tests()
  verify_red_fails()
  loop up to 10:
    implement_minimum_green()
    refactor_keep_green()
    verify_hard_gates()
    if not verify_passed:
      correct()
      continue
    run_parallel_subagents(
      side_effect_reviewer,
      syntax_reviewer,
      task_ac_reviewer,
      test_reviewer
    )
    if all_subagents_passed: break
    apply_fixes_from_findings()
  commit(task_id, tdd_evidence, rule_evidence, verification, next_step)
  verify_delivery_text(commit)
open_or_update_pr(base=develop)
verify_delivery_text(pr)
while reviews_or_checks_pending:
  apply_feedback_commit()
merge_pr_to_develop()
```
