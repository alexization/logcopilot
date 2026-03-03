# LogCopilot Product Direction (MVP v1)

## Purpose
Keep implementation aligned with fixed MVP goals and prevent scope drift across AI sessions.

## Product Definition
Self-hosted log incident analysis tool for Loki environments:
- collect logs
- create incidents
- generate cause hypotheses/evidence/next actions
- notify via Slack/Email

## Target Users
- Backend/Platform engineers
- SRE/DevOps
- Engineering leads (decision makers)

## MVP Fixed Decisions (Do Not Change Without RFC)
1. Connector scope: Loki only
2. Deployment default: single-node self-hosted, Docker
3. Storage default: SQLite (external Postgres/MySQL optional)
4. Backend: Java 21 + Spring Boot 3.x
5. Admin UI: backend-embedded (VanillaJS default, Thymeleaf optional)
6. LLM providers in MVP: OpenAI and Gemini (OAuth + API key)
7. Alerts: Slack and Email only
8. Export policy default: Level 1 (BYOM allowed with masking)

## In Scope (MVP)
- Project management
- Loki connector register/test/pull
- Canonical normalization + redaction + fingerprint
- Incident auto-create/list/detail/reanalyze
- Rule analyzer + BYOM LLM analyzer with fallback
- OpenAI/Gemini account management (OAuth/API key)
- Slack/Email alert configuration
- Export/redaction policy and audit logs
- Health/ready/metrics-level operability support

## Out of Scope (MVP)
- Kubernetes deployment
- SSO/SAML full implementation
- Multi-region HA
- Auto runbook execution
- Local LLM fully isolated runtime
- ELK/Logstash native connector implementation

## Success Criteria
- First incident visible within 30 minutes after install
- End-to-end works in Loki-only environment
- Core policy settings manageable from Admin UI
- No critical data loss in 1k EPS scenario tests

## Delivery Direction
Implement in phased order:
1. Core foundation
2. Loki connector path
3. Ingest reliability
4. Incident core
5. Analyzer
6. Alerts
7. Policy/security
8. Hardening
