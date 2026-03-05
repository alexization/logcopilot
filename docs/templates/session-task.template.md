# Session Task Note - <Task ID>

## Metadata
- Date: YYYY-MM-DD
- Issue: #<issue-number>
- Branch: `feature/<issue-number>-<slug>`
- Task ID: T-XX
- Related spec: `docs/specs/<file>.md`

## 1. Intent Check
- Do now:
- Do not do now:
- Done means:
- Source-of-truth order checked (`docs/domain/source-of-truth.md`):
- MVP scope gate (`docs/domain/product-direction.md`):
- AC-to-rule mapping to execute:

## 2. Plan (Short)
1. 
2. 
3. 

## 3. TDD Evidence
- RED tests written first:
- RED failure output summary:
- GREEN pass output summary:
- REFACTOR summary:

## 4. Implementation Notes
- Key file changes:
- Why this approach:

## 5. Verification
- Commands:
  - `./gradlew check`
  - `./gradlew test`
  - `./gradlew build` (if required)
  - `./scripts/verify-delivery-text.sh issue <issue-number>`
  - `./scripts/verify-delivery-text.sh commit HEAD`
  - `./scripts/verify-delivery-text.sh pr <pr-number>` (PR 단계에서)
- Results:

## 6. Challenge Review ("딴지")
- Run mode: parallel sub-agents on latest commit snapshot
- Side-effect/regression reviewer: PASS | FAIL
- Syntax/static quality reviewer: PASS | FAIL
- Task requirement/AC fit reviewer: PASS | FAIL
- Test correctness/completeness reviewer: PASS | FAIL
- Findings summary:
- Resolution summary:
- Re-run evidence after fix (gates + sub-agents):

## 7. Commit Draft
Use `docs/templates/commit-message.template.md`.

## 8. Next Handoff
- Remaining risk:
- Next task ID:
- Suggested first check for next session:
