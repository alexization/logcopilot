# GitHub Delivery Workflow (`gh` only)

## Branching Strategy
- `main`: production release branch
- `develop`: integration branch for completed work
- `feature/*`: working branches created from `develop`

Do not develop directly on `main` or `develop`.

## Language Rule (Mandatory)
- GitHub Issue, PR, Commit message must be written in Korean.

## Work Unit Lifecycle
1. Create issue
2. Validate issue body template/text integrity
3. Create feature branch from `develop`
4. Implement subtasks with commits + verification gates
5. Validate commit body template/text integrity
6. Run parallel sub-agent challenge gate before PR
7. Fix findings and repeat until all sub-agents pass
8. Open PR to `develop`
9. Validate PR body template/text integrity
10. Receive automated reviews (coderabbitai, codex)
11. Apply feedback with additional commits
12. Merge to `develop` when all checks/reviews are complete

## 1) Issue Creation (`gh issue`)
Create one issue per work unit.

Recommended branch naming:
- `feature/<issue-number>-<slug>`
- `fix/<issue-number>-<slug>`
- `chore/<issue-number>-<slug>`

Example commands:
```bash
gh issue create --title "<제목>" --template "work-unit.md"
gh issue view <issue-number>
./scripts/verify-delivery-text.sh issue <issue-number>
```

## 2) Branch Creation (from `develop`)
```bash
git switch develop
git pull origin develop
git switch -c feature/<issue-number>-<slug>
```

## 3) Commit Rules
- Commit by subtask/task ID.
- Every commit message references issue and task.
- Include verification evidence (`check/test/build`) in commit body.
- Commit 제목/본문은 한국어로 작성한다.
- After commit, validate message template/text integrity:
  - `./scripts/verify-delivery-text.sh commit HEAD`

## 4) Delivery Text Integrity Gate (Mandatory)
Validate delivery artifacts with:
- `./scripts/verify-delivery-text.sh issue <issue-number>`
- `./scripts/verify-delivery-text.sh commit HEAD`
- `./scripts/verify-delivery-text.sh pr <pr-number>`

Validation includes:
- required template section presence
- no literal `\n` in body
- no broken replacement character (`�`)

## 5) Pre-PR Parallel Sub-agent Gate (Mandatory)
Run the following challenge sub-agents in parallel on the latest commit snapshot:
- side-effect/regression reviewer
- syntax/static quality reviewer
- task requirement/AC fit reviewer
- test correctness/completeness reviewer

Rules:
- Each sub-agent must return `PASS` or `FAIL` with evidence.
- If any sub-agent is `FAIL`, apply minimal fix and re-run:
  - required gates (`./gradlew check`, `./gradlew test`, `./gradlew build` when required)
  - all pre-PR sub-agents
- PR creation is blocked until all sub-agents are `PASS`.

## 6) PR Creation (`gh pr`)
Always open PR with:
- `base`: `develop`
- `head`: current feature branch
- linked issue
- summary, scope, tests, risks, and rollout notes
- PR 제목/본문은 한국어로 작성한다.
- After opening PR, validate PR body:
  - `./scripts/verify-delivery-text.sh pr <pr-number>`

Example commands:
```bash
gh pr create --base develop --head <feature-branch> --title "<제목>" --body-file .github/PULL_REQUEST_TEMPLATE.md
gh pr view <pr-number> --comments
gh pr checks <pr-number>
./scripts/verify-delivery-text.sh pr <pr-number>
```

## 7) Review Handling Loop (Mandatory)
After PR is open:
1. Collect feedback from coderabbitai, codex.
2. Classify each comment:
   - must-fix
   - optional improvement
   - reject with rationale
3. Apply accepted fixes with follow-up commits.
4. Reply on PR with what changed and why.
5. Re-run checks and repeat until clear.

A work unit is not complete until this loop is finished.

## 8) Merge Rules
Merge target is `develop` only.

Required before merge:
- required checks green
- blocking review comments resolved
- PR body and linked issue updated

Example:
```bash
gh pr merge <pr-number> --merge --delete-branch
```

## 9) Issue/PR Closure
- Prefer closing via PR keywords (`Closes #<issue-number>`).
- If not auto-closed, close manually:
```bash
gh issue close <issue-number> --comment "Merged via PR #<pr-number>"
```

## 10) Release Branch Note
- Daily development integrates into `develop`.
- Production release promotion happens from `develop` to `main` in a separate release process.
