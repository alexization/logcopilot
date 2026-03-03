# LogCopilot Architecture Guardrails

## Purpose
Define non-negotiable technical invariants so implementation stays extensible and stable.

## Core Invariants
1. Core pipeline consumes `CanonicalLogEvent` only.
2. Core modules (`incident/analyzer/alert`) must not import connector-specific implementations.
3. Connector-specific parsing/auth/mapping must terminate inside connector modules.
4. Pull and push ingest paths must merge into one normalized pipeline.
5. Connector additions must not break existing API contracts or table schemas.

## Canonical Contract
Required fields:
- `event_id`
- `timestamp`
- `service`
- `severity`
- `message`

Optional fields:
- `trace_id`
- `error_code`
- `stack_trace`
- `attributes`

## Pipeline Contract
1. Collect (Loki pull or ingest push)
2. Validate idempotency
3. Normalize + redact + fingerprint
4. Aggregate incidents
5. Analyze (`rule -> llm -> fallback`)
6. Compose report
7. Dispatch alerts
8. Commit cursor after successful persistence

## API Contract Guardrails
1. OpenAPI is the contract source of truth.
2. API code change requires OpenAPI update in same task.
3. OTLP ingest endpoint is reserved in MVP and may return `501`.
4. Error responses must follow standardized `ErrorResponse` schema.

## Security Guardrails
1. Secrets (API keys/OAuth tokens/SMTP password) must be encrypted at rest.
2. Ingest token must be stored as hash only.
3. Redaction is mandatory before LLM transfer.
4. Secret values must never be written to logs.
5. Audit logs are append-only and project-scoped.

## Reliability/Performance Targets
- Incident creation p95 < 60s
- Analysis report p95 < 120s
- Ingest success rate >= 99.5%
- Duplicate ingest must not create duplicate incidents

## Risk Watch Items
- Regex ReDoS in custom redaction rules
- SQLite lock contention at high EPS
- Fingerprint collision causing wrong merges
- Alert storms without cooldown/rate budget/quiet hours
- Contract drift between OpenAPI and implementation
