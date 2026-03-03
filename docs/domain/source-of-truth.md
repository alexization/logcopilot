# Source of Truth Priority

Use this order when documents conflict.

1. `docs/domain/product-direction.md`
2. `docs/api/openapi-selfhosted-byom-v1.yaml`
3. `docs/domain/architecture-guardrails.md`
4. `docs/workflow/github-delivery.md`
5. `docs/workflow/flow.md`
6. `docs/workflow/rule-base.md`
7. `docs/workflow/tdd.md`
8. Active task spec under `docs/specs/`

## Change Policy
RFC is required before changing frozen MVP decisions:
- connector scope (Loki-only)
- deployment default (single-node + docker run)
- storage default (SQLite)
- LLM support matrix (MVP: OpenAI/Gemini)
- alert channel scope (Slack/Email)
