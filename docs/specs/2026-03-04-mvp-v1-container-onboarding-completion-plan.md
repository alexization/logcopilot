# MVP v1 Container Onboarding Completion Plan (Post T-22)

## Metadata
- Date: 2026-03-04
- Owner: Codex
- Status: Draft
- Related issue/PR: TBD
- Previous spec:
  - `docs/specs/2026-03-04-mvp-v1-post-t11-delivery-plan.md`
- Domain baseline refs:
  - `docs/domain/source-of-truth.md`
  - `docs/domain/product-direction.md`
  - `docs/api/openapi-selfhosted-byom-v1.yaml`
  - `docs/domain/architecture-guardrails.md`

## 1. Background
T-22까지 완료되어 계약/API/기본 UI는 동작하지만, 실제 사용자 기준의 "컨테이너 실행 후 `/admin`에서 설정 완료 -> 운영 시작" 경로는 아직 미완성이다.

현재 운영 완료를 막는 주요 공백:
- Loki 연결 테스트/수집이 시뮬레이션/Noop 중심
- LLM OAuth/분석 경로가 stub 중심
- Alert가 설정 저장 중심이고 실발송 운영 검증 경로 미흡
- `/admin`에서 기존 설정 조회/복원 UX 부족
- 외부 설정(LLM OAuth provider 콘솔, SMTP/Slack, Docker env/volume/network) 절차 문서와 검증 루프 부족
- 운영자/수집 토큰의 발급/회전/폐기(수명주기) 관리 경로 부족
- 인시던트 상태 전이(ack/investigating/resolved/ignored) 운영 액션 부족
- startup preflight(필수 env/외부 의존성) 및 readiness 상세 점검 부족
- 운영 백업/복구/보존(retention) 정책과 실행 절차 부족

본 스펙은 "외부 앱 서버가 이미 존재하는 환경"에서 사용자가 LogCopilot Docker 컨테이너를 띄우고 `/admin`만으로 서비스 등록을 완료하는 것을 목표로 한다.

## 2. Goals
- G1: Docker 컨테이너 기동 후 `/admin`에서 Loki/LLM/Alert 설정만으로 첫 incident와 알림까지 확인 가능하게 한다.
- G2: Loki/LLM/Alert 핵심 경로를 하드코딩/시뮬레이션에서 운영 가능한 실연동으로 전환한다.
- G3: 비코드 영역(LLM OAuth provider 설정, SMTP/Slack 설정, Docker 런타임 설정)을 문서+검증 절차로 표준화한다.
- G4: 설정/운영 이력을 감사 로그로 추적 가능하게 하고 재시작 복원성을 유지한다.
- G5: 토큰 수명주기, 인시던트 상태 관리, preflight/backup 절차를 포함해 운영 가능한 서비스 완결성을 확보한다.

## 3. Non-Goals
- NG1: Loki 외 신규 커넥터(ELK/Logstash 등) 추가
- NG2: SSO/SAML, 멀티리전 HA, Kubernetes 기본 배포로 확장
- NG3: `main` 릴리즈 프로모션

## 4. Scope
### In Scope
- 컨테이너 최초 부팅 bootstrap 경로
- `/admin` 설정 조회/수정 UX 완성
- Loki connector test/pull 실연동
- LLM OAuth live/API key 실검증 및 LLM 분석 실경로
- Slack/Email 실발송 + test-send
- 감사로그 확장(설정/변경 이력 전반)
- Docker 실행 계약(env/volume/network/healthcheck) 문서화 + E2E 검증
- 토큰 발급/회전/폐기(운영자/API/ingest 범위) 경로
- 인시던트 상태 전이 API/UI(조사중/해결/무시) 및 감사 추적
- startup preflight(필수 설정/외부 연동 사전검증) + readiness 상세 체크
- 백업/복구/보존(retention) 운영 절차와 검증 시나리오

### Out of Scope
- 멀티노드 분산 아키텍처
- 신규 커넥터 도입
- 외부 SaaS 통합 확장(예: PagerDuty, Teams 등)

### MVP Scope Gate
- Why this spec is in-scope for MVP:
  - `product-direction`의 고정 결정(backend-embedded Admin UI, Loki-only, OpenAI/Gemini, Slack/Email, self-hosted Docker)을 실제 운영 완료 상태로 만드는 마무리 단계이다.
- Which frozen decisions are respected:
  - Loki-only, OpenAI/Gemini only, Slack/Email only, Docker self-hosted, SQLite 기본 저장소.

## 5. Constraints
- Technical constraints:
  - OpenAPI 계약과 에러 스키마 일관성을 유지한다.
  - OTLP endpoint reserved(`501`) 계약은 유지한다.
  - redaction-before-LLM, secret-at-rest 암호화, ingest token hash 저장 원칙을 지킨다.
  - pull/push ingest는 동일 canonical pipeline으로 병합되어야 한다.
- Time/resource constraints:
  - one task = one session = one commit 원칙 유지
- Compatibility constraints:
  - T-01~T-22 회귀를 허용하지 않는다.

## 6. Acceptance Criteria (Definition of Done)
- [ ] AC1: 빈 상태 Docker 컨테이너에서 `/admin` bootstrap 후 API 토큰/기본 프로젝트 설정이 가능하다.
- [ ] AC2: `/admin`에서 connector/llm/policy/alerts의 "현재값 조회 -> 수정 -> 저장" 흐름이 가능하다.
- [ ] AC3: Loki connector test/pull이 실제 Loki 응답 기반으로 동작하고 시뮬레이션 하드코딩이 제거된다.
- [ ] AC4: ingest 파이프라인이 `normalize -> redact -> fingerprint -> incident` 순서를 충족한다.
- [ ] AC5: LLM OAuth/API key가 live 기준으로 검증되고 incident reanalyze가 실제 LLM 경로를 사용한다.
- [ ] AC6: Slack/Email 설정 후 test-send 및 incident alert 실발송이 가능하다.
- [ ] AC7: 프로젝트/커넥터/LLM/정책/알림/인시던트 변경 이력이 감사로그에 기록된다.
- [ ] AC8: Docker 재시작 후 설정/상태 복원이 유지된다.
- [ ] AC9: 깨끗한 환경에서 컨테이너 E2E(설정 -> ingest -> incident -> alert) 시나리오가 통과한다.
- [ ] AC10: 비코드 설정 영역(LLM OAuth, SMTP, Slack, Docker env)의 설정 방법/검증 방법이 운영 문서로 제공된다.
- [ ] AC11: 운영자/API/ingest 토큰의 발급/회전/폐기/권한 범위 관리가 가능하고 `/admin`에서 실행 가능하다.
- [ ] AC12: 인시던트 상태 전이(OPEN -> INVESTIGATING -> RESOLVED/IGNORED)가 API/UI에서 가능하고 감사로그에 기록된다.
- [ ] AC13: startup preflight와 readiness 상세 체크가 필수 설정 누락/외부 연동 실패를 운영자가 식별 가능하게 제공한다.
- [ ] AC14: 백업/복구/보존(retention) 절차가 문서화되고 복구 리허설 테스트가 통과한다.

## 7. Rule-Base Mapping (Mandatory)
| AC ID | Rule Type | Command | Pass Condition |
| --- | --- | --- | --- |
| AC1 | Integration | `./gradlew test --tests "*Bootstrap*Test"` | PASS |
| AC2 | UI/Contract | `./gradlew test --tests "*AdminUi*Test" --tests "*EndpointsContractTest"` | PASS |
| AC3 | Integration | `./gradlew test --tests "*Loki*IntegrationTest" --tests "*LokiPull*Test"` | PASS |
| AC4 | Unit/Integration | `./gradlew test --tests "*Ingest*Test" --tests "*Policy*Test" --tests "*Incident*Test"` | PASS |
| AC5 | Integration/Security | `./gradlew test --tests "*Llm*OAuth*Test" --tests "*Llm*Analyzer*Test"` | PASS |
| AC6 | Integration | `./gradlew test --tests "*Alert*IntegrationTest" --tests "*Alert*Dispatch*Test"` | PASS |
| AC7 | Integration | `./gradlew test --tests "*Audit*Test"` | PASS |
| AC8 | Persistence | `./gradlew test --tests "*SqlitePersistenceTest"` | PASS |
| AC9 | E2E | `./gradlew test --tests "*ContainerOnboardingE2ETest"` | PASS |
| AC10 | Static/Doc | `rg -n "OAuth|SMTP|Slack|Docker|설정 절차|검증 절차" docs/ops docs/specs` | required docs found |
| AC11 | Integration/Security | `./gradlew test --tests "*TokenLifecycle*Test" --tests "*Security*Test"` | PASS |
| AC12 | Integration/UI | `./gradlew test --tests "*Incident*Lifecycle*Test" --tests "*AdminUi*Test"` | PASS |
| AC13 | Integration/Operability | `./gradlew test --tests "*Preflight*Test" --tests "*Readiness*Test"` | PASS |
| AC14 | Integration/Runbook | `./gradlew test --tests "*BackupRestore*Test"` | PASS |
| AC1~14 | Gate | `./gradlew check && ./gradlew test && ./gradlew build` | exit code 0 |

## 8. Task Breakdown
| Task ID | Description | Done Signal | Required Tests | Risk |
| --- | --- | --- | --- | --- |
| T-23 | 컨테이너 최초 bootstrap + 토큰 수명주기 기반(발급/회전/폐기) 도입 | 빈 상태 `/admin`에서 초기 설정 + 토큰 관리 가능 | `*Bootstrap*Test` + `*TokenLifecycle*Test` | High |
| T-24 | `/admin` 설정 조회 API/UX 확장 (connector/llm/policy/alerts/token) | 재접속 시 기존값 로드/수정 가능 | `*AdminUi*Test` + contract | High |
| T-25 | Loki connector test 실연동화 | 하드코딩 sample 응답 제거 | `*Loki*IntegrationTest` | High |
| T-26 | Loki pull 실수집 + cursor 운영 가시성 | pull 성공/실패/커서 상태 추적 가능 | `*LokiPull*Test` | High |
| T-27 | canonical 파이프라인 redaction/fingerprint 완성 | incident/detail에 민감정보 원문 미노출 | `*Ingest*Test` + `*Policy*Test` | High |
| T-28 | LLM OAuth live/API key 검증 + 토큰 수명주기 | provider 연동 성공 및 오류 매핑 고정 | `*Llm*OAuth*Test` | High |
| T-29 | LLM analyzer 실연동(실제 provider 호출/실패 fallback) | reanalyze가 실LLM 응답/timeout/fallback 처리 | `*Llm*Analyzer*Test` | High |
| T-30 | Slack/Email 실발송 + test-send | `/admin`에서 채널 테스트와 본 발송 확인 | `*Alert*IntegrationTest` | High |
| T-31 | 인시던트 상태 전이 API/UI + 감사로그 확장 | 상태 전이/변경 이력 end-to-end 추적 가능 | `*Incident*Lifecycle*Test` + `*Audit*Test` | High |
| T-32 | startup preflight + readiness 상세 체크 + Docker 계약 문서 정리 | 필수 설정/연동 실패를 시작 시점에 식별 가능 | `*Preflight*Test` + smoke + docs checks | High |
| T-33 | 컨테이너 E2E 인수 테스트 구축 | clean env에서 onboarding -> ingest -> incident lifecycle -> alert 자동검증 | `*ContainerOnboardingE2ETest` | High |
| T-34 | 최종 hardening(backup/restore/retention 포함) + 운영 가이드 마무리 | 복구 리허설 통과 + 실패 대응 runbook 제공 | `*BackupRestore*Test` + gate + docs checks | High |

Rule: one task should fit in one session and one commit.

## 9. TDD Plan (Mandatory)
- RED tests to write first:
  - bootstrap, Loki 실연동, LLM OAuth live, alert send, container E2E 순서로 실패 테스트를 먼저 고정한다.
- Expected RED failure signal:
  - 시뮬레이션 응답/빈 pull/실발송 부재/설정 조회 누락/E2E 실패.
- GREEN implementation boundary:
  - 각 task의 직접 목표만 만족하는 최소 구현.
- Refactor boundary:
  - 설정 모델 정리, 책임 분리, 에러 메시지/관측성 개선 중심.

## 10. Test Strategy
- Unit:
  - 토큰/정책/분석기/설정 파서/검증 로직
- Integration:
  - Loki/LLM/Alert 실제 연동 경로와 오류 매핑
- Regression:
  - 기존 contract/UI/security/persistence 회귀
- E2E:
  - Docker 컨테이너 onboarding 시나리오

## 11. Configuration-First Delivery Policy (Mandatory)
본 스펙의 일부 작업은 코드 구현만으로 완료되지 않는다. 특히 아래 영역은 "설정 설명 + 검증 절차 + 코드 구현"을 함께 진행해야 한다.

- 비코드 설정 대상:
  - LLM OAuth provider 콘솔 설정(redirect URI, client id/secret, scope, consent)
  - SMTP relay 설정(계정, TLS, 발신 도메인, 포트)
  - Slack webhook/channel 설정
  - Loki endpoint/query/tenant/권한 범위 설정
  - Docker 런타임 설정(env, volume, network, secret 주입)
  - 백업 저장 위치/주기/복구 절차 설정

- 수행 원칙:
  - 코드 반영 전에 설정 전제조건과 운영자 입력값을 문서로 먼저 명시한다.
  - 각 설정 항목마다 "검증 커맨드/기대 결과/실패 시 조치"를 함께 제공한다.
  - 구현은 문서의 절차를 그대로 재현 가능한 형태로 진행한다.
  - task 완료 조건에는 코드 테스트뿐 아니라 설정 문서의 재현 검증을 포함한다.

- 문서 산출물(필수):
  - `docs/ops/container-onboarding.md`
  - `docs/ops/loki-connector-setup.md`
  - `docs/ops/llm-oauth-setup.md`
  - `docs/ops/alert-delivery-setup.md`
  - `docs/ops/backup-restore.md`
  - `docs/ops/go-live-checklist.md`
  - `docs/ops/env-template.md` (또는 `.env.example` 가이드)

## 12. Decision Log
- Decision:
  - Post T-22 단계는 "기능 추가"보다 "운영 완료 경로 폐쇄"를 우선한다.
  - Reason:
    - 사용자 성공 기준은 API contract 충족이 아니라 `/admin` 실사용 onboarding 완료이기 때문이다.
  - Tradeoff:
    - 외부 설정 문서와 검증 절차 작성으로 초기 속도는 느려지지만, 배포/운영 실패 확률을 크게 낮춘다.

## 13. Open Questions
- Q1:
  - OAuth live 모드 전환 시 secret 주입 전략(환경변수 vs secret file) 기본값을 무엇으로 할지.
- Q2:
  - Alert test-send API를 기존 endpoint 확장으로 갈지, 별도 endpoint로 분리할지.
- Q3:
  - 컨테이너 E2E 테스트를 CI 기본 게이트로 항상 실행할지 nightly로 분리할지.
- Q4:
  - 인시던트 lifecycle endpoint를 기존 `/v1/incidents/{id}` 확장으로 둘지 전이 전용 endpoint로 분리할지.
- Q5:
  - 백업/복구의 기본 단위를 SQLite 파일 스냅샷으로 고정할지, logical export를 병행할지.
