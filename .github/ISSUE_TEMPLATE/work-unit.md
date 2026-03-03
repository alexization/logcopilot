---
name: "작업 단위"
about: "기능/개선 단위 이슈 (하나의 작업 단위, 하나의 PR)"
title: ""
labels: ["work-unit"]
---

## 작업 배경
- 이 작업이 필요한 이유:

## 목표
- G1:
- G2:

## 비목표
- NG1:
- NG2:

## 범위
- 포함:
- 제외:

## 참고 문서
- Spec: `docs/specs/<spec-file>.md`
- Domain guardrails:
  - `docs/domain/product-direction.md`
  - `docs/domain/architecture-guardrails.md`

## 완료 조건(AC)
- [ ] AC1:
- [ ] AC2:

## 세부 과업
- [ ] T-01
- [ ] T-02

## 검증 계획
- `./gradlew check`
- `./gradlew test`
- `./gradlew build` (필요 시)

## 종료 기준
- [ ] 코드 + 테스트 완료
- [ ] `develop` 대상 PR 생성 완료
- [ ] coderabbitai/codex/ecc 리뷰 반영 완료
