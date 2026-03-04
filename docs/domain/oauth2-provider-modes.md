# OAuth2 Provider Mode Guide (OpenAI/Gemini)

## Purpose
`T-15` 기준으로 로컬 개발(stub)과 실제 provider(live) 전환 기준을 고정한다.

## Configuration Keys
- `logcopilot.llm.oauth.mode`: `stub` | `live`
- `logcopilot.llm.oauth.state-ttl`: OAuth state TTL (`PT10M` 기본)
- `logcopilot.llm.oauth.callback-base-url`: callback 절대 URL 기준값
- `logcopilot.llm.oauth.<provider>.client-id`
- `logcopilot.llm.oauth.<provider>.authorization-uri` (live)
- `logcopilot.llm.oauth.<provider>.stub-authorization-uri` (stub)
- `logcopilot.llm.oauth.<provider>.scopes`

`<provider>`는 `openai`, `gemini`만 허용한다.

## Mode Selection Rule
1. 로컬/테스트 환경:
  - `mode=stub`
  - 목적: 외부 OAuth provider 의존성 없이 start/callback/state 정책 검증
2. 스테이징/운영 환경:
  - `mode=live`
  - 목적: 실제 provider authorization endpoint를 사용해 redirect 흐름을 검증
  - 주의: MVP T-15에서는 provider token exchange/issuer 검증은 범위 밖이며, callback `code` 존재 여부와 state 정책만 검증한다.

## Callback Policy
- `state`는 항상 필수이며 TTL 만료 또는 재사용 시 `409 conflict`를 반환한다.
- provider가 `error`를 반환하면 `400 bad_request`로 매핑한다.
- `error`가 없으면 `code`가 필수이며 누락/공백은 `400 bad_request`다.

## Operational Notes
- `callback-base-url`은 외부 provider에 등록된 redirect URI와 반드시 일치해야 한다.
- mode 전환 시 `authorization-uri`와 `client-id`를 환경변수/시크릿으로 주입한다.
