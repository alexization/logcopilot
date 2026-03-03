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

## 8) Agent Drop / Challenge Review
- Independent critic reviews:
  - requirement fit
  - regression risk
  - missing tests
  - security/operability concerns
- If any critical concern remains, return to Step 7.

## 9) Commit + Handoff
- One task, one commit.
- Use commit template and include verification results.
- Commit message must be written in Korean.
- Record next-session handoff note:
  - current status
  - remaining risks
  - exact next task ID
  - RED -> GREEN evidence summary

## 10) PR to `develop` (`gh pr`)
- Open or update PR from feature branch to `develop`.
- PR body must include linked issue, tests, risk, and rollback notes.
- Issue/PR title and body must be written in Korean.

## 11) Review Feedback Loop (Mandatory)
- Collect automated reviews from coderabbitai, codex, ecc.
- Apply accepted feedback with additional commits.
- Respond on PR with change rationale.
- Repeat until blocking review comments/checks are resolved.

## 12) Merge + Close
- Merge PR into `develop` via `gh pr`.
- Confirm issue is closed (auto via `Closes #...` or manual close).

## Pseudocode
```text
ensure_issue_and_feature_branch()
for each task in spec.tasks:
  clarify(task.goal, task.non_goal, task.done)
  write_red_tests()
  verify_red_fails()
  loop up to 10:
    implement_minimum_green()
    refactor_keep_green()
    verify_hard_gates()
    if verify_passed:
      challenge_review()
      if review_passed: break
    correct()
  commit(task_id, tdd_evidence, rule_evidence, verification, next_step)
open_or_update_pr(base=develop)
while reviews_or_checks_pending:
  apply_feedback_commit()
merge_pr_to_develop()
```
