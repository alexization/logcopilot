# MVP v1 Post-T11 Delivery Plan (Security/Validation/OAuth2/UI)

## Metadata
- Date: 2026-03-04
- Owner: Codex
- Status: Draft
- Related issue/PR: TBD
- Previous spec: `docs/specs/2026-03-03-mvp-v1-work-unit-bootstrap.md`
- Domain baseline refs:
  - `docs/domain/source-of-truth.md`
  - `docs/domain/product-direction.md`
  - `docs/api/openapi-selfhosted-byom-v1.yaml`
  - `docs/domain/architecture-guardrails.md`

## 1. Background
T-11까지 계약 중심 API 골격은 완료되었지만, 운영 안전성을 좌우하는 핵심이 아직 비어 있다.
- Spring Security/Validation/OAuth2 의존성 및 표준 보안 체계 미도입
- backend-embedded Admin UI 미구현
- Loki pull 실동작/커서 커밋 경로 미구현
- LLM 전송 전 redaction 강제, secret 로그 비노출, rate-limit/alert storm 제어 미완료

본 문서는 구현 우선순위를 다음 순서로 고정한다.  
보안/검증 기반 -> 인증/인가 일원화 -> OAuth2 안정화 -> Loki pull/커서 -> redaction/로그 보안 -> 과부하 제어 -> UI -> 영속화/암호화 -> 운영 게이트.

## 2. Goals
- G1: `jwt/security/oauth2/validation` 기반을 우선 도입해 인증/인가/검증 체계를 표준화한다.
- G2: Loki pull 실동작, push/pull 병합 파이프라인, 성공 시 cursor commit 계약을 충족한다.
- G3: LLM 전송 전 redaction 강제, secret 로그 비노출, ingest token hash 저장, secret 암호화 저장을 달성한다.
- G4: ingest rate-limit 및 alert storm 완화(cooldown/quiet-hours/rate-budget)를 도입해 과부하 리스크를 줄인다.
- G5: MVP 고정 범위의 Admin UI(backend-embedded)를 구현해 핵심 운영 흐름을 웹에서 수행 가능하게 한다.
- G6: OpenAPI 계약/에러 스키마 일관성 및 필수 게이트(`check/test/build`)를 유지한다.

## 3. Non-Goals
- NG1: Loki 외 신규 커넥터(ELK/Logstash 등) 추가.
- NG2: SSO/SAML, 멀티리전 HA, Kubernetes 배포 등 MVP 외 확장 범위.
- NG3: `main` 릴리즈 프로모션.

## 4. Scope
### In Scope
- 보안/검증/OAuth2 의존성 및 SecurityFilterChain 도입.
- 컨트롤러 수동 인증 검증 로직 제거 및 인증/인가 정책 일원화.
- OpenAI/Gemini OAuth2 start/callback/state 수명주기 강화.
- Loki pull collector + normalized pipeline merge + cursor commit 구현.
- LLM 전송 전 redaction 의무화 및 민감정보 로그 비노출 가드.
- ingest rate-limit 및 alert storm 제어.
- Admin UI 핵심 화면/액션 구현(프로젝트, 커넥터, LLM 계정, 정책, 알림, 인시던트, audit).
- SQLite 영속화, secret 암호화 저장, ingest token hash 저장, audit append-only 보장.

### Out of Scope
- 외부 IdP 기반 조직 SSO.
- 복잡한 워크플로 엔진/런북 자동 실행.
- 멀티 노드 분산 배포 최적화.

### MVP Scope Gate
- Why this plan is in-scope for MVP:
  - `product-direction`의 고정 결정(Loki-only, backend-embedded UI, OpenAI/Gemini, Slack/Email, SQLite 기본 저장)을 운영 가능 상태로 완성하는 필수 후속 작업이다.
- Which frozen decisions are respected:
  - Loki-only, Java 21 + Spring Boot 3.x, OpenAI/Gemini only, Slack/Email only.

## 5. Constraints
- Technical constraints:
  - OpenAPI 계약을 깨지 않아야 하며 에러 응답은 `ErrorResponse` 스키마를 유지해야 한다.
  - OTLP reserved(`501`) 계약을 유지한다.
  - Secret은 평문 저장 금지, ingest token은 hash 저장만 허용한다.
  - LLM analyzer 경로는 redaction을 거치지 않은 원문을 전송하면 안 된다.
  - pull/push ingest는 동일 canonical pipeline으로 합류해야 한다.
- Time/resource constraints:
  - 각 태스크는 한 세션/한 커밋 단위로 분할한다.
- Compatibility constraints:
  - 기존 완료 태스크(T-01~T-11)의 계약 테스트 회귀를 허용하지 않는다.

## 6. Acceptance Criteria (Definition of Done)
- [ ] AC1: 보안/검증/OAuth2 기본 의존성과 보안 설정이 도입되고 기존 계약 테스트가 회귀 없이 통과한다.
- [ ] AC2: `bearerAuth`/`ingestToken` 검증이 Spring Security 기반으로 일원화되고 컨트롤러 수동 검증이 제거된다.
- [ ] AC3: OpenAI/Gemini OAuth2 start/callback 흐름이 상태 검증/만료/재사용 방지 규칙으로 동작한다.
- [ ] AC4: Loki pull 수집이 canonical pipeline으로 병합되고, 저장 성공 시에만 cursor가 commit된다.
- [ ] AC5: LLM 전송 직전 redaction이 강제되며 secret/token/password가 로그에 기록되지 않는다.
- [ ] AC6: ingest rate-limit(429)과 alert storm 완화(cooldown/quiet-hours/rate-budget)가 동작한다.
- [ ] AC7: Admin UI에서 MVP 핵심 운영 플로우(생성/수정/조회/재분석)가 가능하다.
- [ ] AC8: SQLite 영속화 + secret 암호화 저장 + ingest token hash 저장 + audit append-only/project-scope가 충족된다.
- [x] AC9: 신뢰성/성능/SLO 증거(incident p95, analysis p95, ingest success rate)와 `check/test/build` 결과가 문서화된다.

## 7. Rule-Base Mapping (Mandatory)
| AC ID | Rule Type | Command | Pass Condition |
| --- | --- | --- | --- |
| AC1 | Static | `rg -n "spring-boot-starter-security|spring-boot-starter-validation|spring-boot-starter-oauth2-client|spring-boot-starter-oauth2-resource-server" build.gradle` | required deps found |
| AC1 | Build/Test | `./gradlew check && ./gradlew test` | exit code 0 |
| AC2 | Integration | `./gradlew test --tests "*Security*Test" --tests "*EndpointsContractTest"` | PASS |
| AC3 | Unit/Integration | `./gradlew test --tests "*LlmAccount*Test" --tests "*OAuth*Test"` | PASS |
| AC4 | Integration/Regression | `./gradlew test --tests "*Loki*Test" --tests "*Ingest*Test" --tests "*Incident*Test"` | PASS |
| AC5 | Security/Unit | `./gradlew test --tests "*Policy*Test" --tests "*LlmIncidentAnalyzer*Test" --tests "*Logging*Test"` | PASS |
| AC6 | Integration/Security | `./gradlew test --tests "*Ingest*RateLimit*Test" --tests "*Alert*Cooldown*Test"` | PASS |
| AC7 | Integration/E2E-lite | `./gradlew test --tests "*Ui*Test" --tests "*EndpointsContractTest"` | PASS |
| AC8 | Integration/Security | `./gradlew test --tests "*Persistence*Test" --tests "*Secret*Test" --tests "*Audit*Test"` | PASS |
| AC9 | Build/Perf | `./gradlew check && ./gradlew test && ./gradlew build` | exit code 0 |
| AC9 | Static/Doc | `rg -n "incident p95|analysis p95|ingest success rate|한계|리스크" docs/sessions/*.md docs/specs/*.md` | evidence found |

## 8. Task Breakdown
| Task ID | Description | Done Signal | Required Tests | Risk |
| --- | --- | --- | --- | --- |
| T-12 | 보안/검증/OAuth2 의존성 + 보안 기본 구성 | starter 추가 + SecurityFilterChain 기본 동작 + 회귀 없음 | Unit + integration + contract regression | High |
| T-13 | JWT/Bearer + ingest token 인증/인가 일원화 | 수동 `BearerTokenValidator` 호출 제거 + 401/403 정책 일관성 확보 | Unit + integration | High |
| T-14 | Validation 표준화(`@Valid`) 및 에러 응답 정규화 | Bean Validation 통합 + 기존 4xx 계약 유지 | Unit + contract regression | Medium |
| T-15 | OAuth2(OpenAI/Gemini) 실연동 안정화 | start/callback/state TTL/재사용 방지/오류 매핑 시나리오 PASS | Unit + integration | High |
| T-16 | Loki pull collector + cursor commit + push/pull 병합 | pull 수집 후 canonical pipeline 합류, 성공 시 cursor commit 검증 | Integration + regression | High |
| T-17 | Redaction-before-LLM + secret 로그 비노출 가드 | LLM 전송 전 redaction 강제, 민감정보 로그 유출 테스트 PASS | Security + unit + integration | High |
| T-18 | ingest rate-limit + alert storm 제어 | 429/쿨다운/quiet-hours/rate-budget 동작 검증 | Integration + reliability | High |
| T-19 | Admin UI 기반 + 인증 경계 정립 | 공통 레이아웃/API 유틸/보안 헤더/CSRF-CORS 정책 반영 | UI integration + security tests | High |
| T-20 | Admin UI 핵심 운영 화면 구현 | 프로젝트/커넥터/LLM/정책/알림/인시던트/audit UI 동작 | UI integration + regression | High |
| T-21 | SQLite 영속화 + 시크릿 보호 저장소 전환 | 재시작 복원 + secret 암호화 + ingest token hash + audit append-only 검증 | Integration + persistence + security | High |
| T-22 | 운영성/SLO/하드 게이트 종료 | 신뢰성/성능 측정 증거 + `check/test/build` green + merge-ready | check/test/build + perf evidence | High |

Rule: one task should fit in one session and one commit.

## 9. Detailed Steps by Task

### T-12
1. `build.gradle`에 security/validation/oauth2 starter를 추가한다.
2. SecurityFilterChain 기본 정책(`/healthz`, `/readyz` 공개, 나머지 보호)을 정의한다.
3. 인증 실패/인가 실패를 `ErrorResponse`로 통일한다.
4. 계약 테스트 전수 회귀를 실행한다.

### T-13
1. 컨트롤러 수동 bearer/ingest 검증 지점을 제거한다.
2. `bearerAuth`와 `ingestToken`을 필터 체인에서 분리 검증한다.
3. endpoint 패턴별 접근 제어 규칙을 확정한다.
4. 401/403/404 경계 테스트를 보강한다.

### T-14
1. 요청 DTO에 Bean Validation annotation을 적용한다.
2. 수동 null/blank 검사 중복 로직을 제거한다.
3. validation 에러를 OpenAPI `ValidationError` 형태로 통일한다.
4. 기존 contract test를 재실행해 상태코드/스키마 유지 여부를 확인한다.

### T-15
1. OpenAI/Gemini OAuth2 client registration/properties를 정리한다.
2. state 생성/검증/만료/재사용 방지를 단일 정책으로 통합한다.
3. callback 에러(누락/만료/충돌/provider 오류) 매핑을 고정한다.
4. 로컬 개발(stub)과 실제 provider 전환 기준을 문서화한다.

### T-16
1. Loki pull worker(스케줄/주기 실행)와 cursor 저장 모델을 구현한다.
2. pull 결과를 `CanonicalLogEvent`로 정규화해 push ingest와 동일 파이프라인에 병합한다.
3. incident 반영/저장 성공 후에만 cursor를 commit한다.
4. 중복/실패/재시도 시 cursor 일관성 회귀 테스트를 추가한다.

### T-17
1. LLM analyzer 직전 redaction 필터를 강제 적용한다.
2. secret/token/password 등 민감정보가 로그에 남지 않도록 로깅 정책을 고정한다.
3. redaction 누락 시 요청 차단 또는 fallback 처리 정책을 확정한다.
4. 보안 회귀 테스트(유출 패턴 검사)를 추가한다.

### T-18
1. ingest 경로에 요청량 제한과 429 응답 정책을 적용한다.
2. alert 전송 경로에 cooldown/quiet-hours/rate-budget 정책을 적용한다.
3. alert storm 시나리오 테스트를 추가한다.
4. 운영자 설정값 변경 경로(UI/API)와 기본값을 문서화한다.

### T-19
1. backend-embedded UI 구조(`static` 또는 `templates`)를 확정한다.
2. 공통 레이아웃/내비게이션/API client/에러 처리 패턴을 구현한다.
3. UI 인증 모델(cookie session vs token bridge), CSRF/CORS, 보안 헤더 정책을 고정한다.
4. 모바일/데스크톱 반응형 및 접근성 기본 검증을 수행한다.

### T-20
1. 프로젝트/커넥터/LLM 계정 화면을 구현한다.
2. 정책/알림 설정 화면을 구현한다.
3. 인시던트 목록/상세/재분석 화면을 구현한다.
4. audit 조회(cursor/limit) 화면과 실패 UX를 구현한다.

### T-21
1. SQLite 영속화 계층(스키마/리포지토리/초기화)을 도입한다.
2. 프로젝트/커넥터/LLM/정책/알림/audit/커서를 in-memory에서 전환한다.
3. secret 암호화 저장, ingest token hash 저장, 감사 append-only를 강제한다.
4. 재시작 복원/데이터 무결성/보안 회귀 테스트를 추가한다.

### T-22
1. `check/test/build` 전체 게이트를 실행한다.
2. `incident p95 < 60s`, `analysis p95 < 120s`, `ingest success rate >= 99.5%` 측정 결과를 기록한다.
3. 한계/잔여 리스크와 완화 계획을 문서화한다.
4. PR 리뷰 피드백 루프(codex/coderabbitai) 완료 상태를 확인한다.

## 10. TDD Plan (Mandatory)
- RED tests to write first:
  - 보안/검증/OAuth/pull-cursor/rate-limit/UI 각 태스크별 계약 위반 케이스를 먼저 실패로 고정.
- Expected RED failure signal:
  - 401/403/422/429/409 코드 불일치, OAuth state 규칙 위반, cursor commit 조건 위반, redaction 누락, UI 회귀.
- GREEN implementation boundary:
  - 현재 태스크의 목표 행동을 만족하는 최소 코드만 반영.
- Refactor boundary:
  - 책임 분리/명명/중복 제거 중심으로 수행하고 계약 동작은 유지.

## 11. Test Strategy
- Unit:
  - 토큰 검증, state 정책, validation 규칙, redaction/secret 보호 로직.
- Integration:
  - 보안 체인, OAuth callback, pull+push 병합 파이프라인, cursor commit, DB 영속화 경로.
- Regression:
  - `*EndpointsContractTest` 전수 + `*Ui*Test` 핵심 플로우 + 보안 회귀(로그 유출/암호화/해시).
- Reliability/Performance:
  - ingest 부하/중복/제한 시나리오, incident/analyze 지연 시간 측정.

## 12. Security Must-Haves (Mandatory)
- Secret(API key/OAuth token/SMTP/Slack credential)은 저장 시 암호화한다.
- ingest token은 원문 저장 금지, hash 저장만 허용한다.
- redaction 처리 전 원문 로그를 LLM으로 전달하지 않는다.
- 애플리케이션 로그/감사로그에 secret 원문이 포함되지 않아야 한다.
- UI/API 경계에 CSRF/CORS/보안 헤더 정책을 명시한다.
- 보안 관련 실패는 기능 완성보다 우선 수정한다.

## 13. Decision Log
- Decision:
  - 기능 확장 전에 보안/인증/검증 기반과 Loki pull/커서 계약을 먼저 완성한다.
  - Reason:
    - 정상 동작(End-to-End)과 보안 안전성을 동시에 만족하는 최소 경로이기 때문이다.
  - Tradeoff:
    - 초기 단계에서 UI 가시성보다 기반/보안 작업 비중이 크다.

## 14. Open Questions
- Q1:
  - Admin UI 인증 모델을 쿠키 세션 기반으로 할지, 토큰 브릿지 방식으로 할지 최종 선택.
- Q2:
  - 시크릿 암호화 키 관리(단일 환경변수 vs 키 회전 지원)를 MVP에서 어디까지 포함할지.
- Q3:
  - Loki pull worker 실행 모델(단일 스레드 폴링 vs 큐 기반)을 MVP에서 어느 수준까지 구현할지.
