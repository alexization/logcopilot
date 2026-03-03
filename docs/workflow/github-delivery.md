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
2. Create feature branch from `develop`
3. Implement subtasks with commits
4. Open PR to `develop`
5. Receive automated reviews (coderabbitai, codex)
6. Apply feedback with additional commits
7. Merge to `develop` when all checks/reviews are complete

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

## 4) PR Creation (`gh pr`)
Always open PR with:
- `base`: `develop`
- `head`: current feature branch
- linked issue
- summary, scope, tests, risks, and rollout notes
- PR 제목/본문은 한국어로 작성한다.

Example commands:
```bash
gh pr create --base develop --head <feature-branch> --title "<제목>" --body-file .github/PULL_REQUEST_TEMPLATE.md
gh pr view <pr-number> --comments
gh pr checks <pr-number>
```

## 5) Review Handling Loop (Mandatory)
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

## 6) Merge Rules
Merge target is `develop` only.

Required before merge:
- required checks green
- blocking review comments resolved
- PR body and linked issue updated

Example:
```bash
gh pr merge <pr-number> --merge --delete-branch
```

## 7) Issue/PR Closure
- Prefer closing via PR keywords (`Closes #<issue-number>`).
- If not auto-closed, close manually:
```bash
gh issue close <issue-number> --comment "Merged via PR #<pr-number>"
```

## 8) Release Branch Note
- Daily development integrates into `develop`.
- Production release promotion happens from `develop` to `main` in a separate release process.
