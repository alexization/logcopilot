# Rule-Base Policy

## Why
AI output is probabilistic. Requirement accuracy must be enforced by executable rules, not prompt confidence.

## Core Rule
Every acceptance criterion (AC) must be mapped to at least one executable verifier.

Mapping format:
- `AC-ID -> Rule Type -> Command -> Expected Result`

Example:
- `AC1 -> Integration Test -> ./gradlew test --tests "*OrderApiIntegrationTest" -> PASS`
- `AC2 -> Build Gate -> ./gradlew check -> PASS`

## Rule Types
1. Test rules: unit/integration/e2e
2. Build/check rules: Gradle verification gates
3. Static rules: lint/security/architecture constraints (when configured)
4. Delivery text rules: issue/commit/pr template/integrity checks

Delivery text example:
- `ACx -> Delivery Text -> ./scripts/verify-delivery-text.sh issue <issue-number> -> PASS`
- `ACx -> Delivery Text -> ./scripts/verify-delivery-text.sh commit HEAD -> PASS`
- `ACx -> Delivery Text -> ./scripts/verify-delivery-text.sh pr <pr-number> -> PASS`

## Hard Gates for This Repository
- Mandatory: `./gradlew check`
- Mandatory: `./gradlew test`
- Conditional: `./gradlew build` when dependency/config/cross-module impact exists

If a new static analysis tool is introduced, it must run under `check`.

## Failure Handling
If any mapped rule fails:
1. Create short bug report (observed/expected/root cause).
2. Apply minimal correction.
3. Re-run failed gate first, then full mandatory gates.
4. Do not close task until all mapped rules pass.

## Completion Criteria
Task closure requires:
- All mapped rules executed
- All mandatory gates passed
- Results recorded in commit message or session note
