# TDD Policy (RED -> GREEN -> REFACTOR)

## Core Rule
Do not write production code before creating a failing test for the target behavior.

## Execution Steps
1. RED
- Define behavior from spec AC.
- Write or update test first.
- Run test and confirm failure.

2. GREEN
- Implement minimal production code to pass RED tests.
- Avoid unrelated changes.

3. REFACTOR
- Improve structure/readability without changing behavior.
- Keep tests green while refactoring.

## Required Evidence Per Task
- RED evidence: which tests failed before implementation.
- GREEN evidence: which tests passed after implementation.
- REFACTOR note: what changed without behavior change.

## Coverage Requirement
- Target: at least 80% effective coverage for changed scope (unit + integration; e2e where relevant).
- If tooling for numeric coverage is not configured, document this gap and create follow-up task.

## Anti-Patterns
- Implement-first then retrofit tests
- Passing tests that do not assert acceptance criteria
- Large refactor mixed with feature code in one task
