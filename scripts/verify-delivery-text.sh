#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/verify-delivery-text.sh issue-file <path>
  ./scripts/verify-delivery-text.sh pr-file <path>
  ./scripts/verify-delivery-text.sh commit-file <path>
  ./scripts/verify-delivery-text.sh issue <issue-number>
  ./scripts/verify-delivery-text.sh pr <pr-number>
  ./scripts/verify-delivery-text.sh commit <commit-ref>
EOF
}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    fail "필수 명령어를 찾을 수 없습니다: $cmd"
  fi
}

check_utf8() {
  local text="$1"
  if ! printf '%s' "$text" | iconv -f UTF-8 -t UTF-8 | cat >/dev/null 2>&1; then
    fail "UTF-8 인코딩이 깨졌습니다."
  fi
}

check_sections() {
  local text="$1"
  local label="$2"
  shift 2
  local missing=()
  local section
  for section in "$@"; do
    if ! printf '%s' "$text" | rg -F -- "$section" >/dev/null; then
      missing+=("$section")
    fi
  done

  if ((${#missing[@]} > 0)); then
    echo "FAIL: $label 필수 섹션 누락" >&2
    printf ' - %s\n' "${missing[@]}" >&2
    exit 1
  fi
}

if [[ $# -ne 2 ]]; then
  usage
  exit 1
fi

require_cmd rg
require_cmd iconv

mode="$1"
target="$2"
profile=""
source_desc=""
text=""

read_text_from_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "파일을 찾을 수 없습니다: $file"
  iconv -f UTF-8 -t UTF-8 "$file" | cat >/dev/null 2>&1 || fail "파일 인코딩이 UTF-8이 아닙니다: $file"
  cat "$file"
}

case "$mode" in
  issue-file)
    profile="issue"
    source_desc="issue-file:$target"
    text="$(read_text_from_file "$target")"
    ;;
  pr-file)
    profile="pr"
    source_desc="pr-file:$target"
    text="$(read_text_from_file "$target")"
    ;;
  commit-file)
    profile="commit"
    source_desc="commit-file:$target"
    text="$(read_text_from_file "$target")"
    ;;
  issue)
    require_cmd gh
    profile="issue"
    source_desc="issue:$target"
    text="$(gh issue view "$target" --json body --jq '.body')"
    ;;
  pr)
    require_cmd gh
    profile="pr"
    source_desc="pr:$target"
    text="$(gh pr view "$target" --json body --jq '.body')"
    ;;
  commit)
    require_cmd git
    profile="commit"
    source_desc="commit:$target"
    text="$(git log -1 --pretty=%B "$target")"
    ;;
  *)
    usage
    exit 1
    ;;
esac

if [[ -z "${text//[$' \t\r\n']/}" ]]; then
  fail "$source_desc 본문이 비어 있습니다."
fi

check_utf8 "$text"

if printf '%s' "$text" | rg -n --fixed-strings "\\n" >/dev/null; then
  fail "$source_desc 본문에 문자열 \\n 이 포함되어 있습니다."
fi

if printf '%s' "$text" | rg -n --fixed-strings "�" >/dev/null; then
  fail "$source_desc 본문에 깨진 문자(�)가 포함되어 있습니다."
fi

case "$profile" in
  issue)
    check_sections "$text" "Issue" \
      "## 작업 배경" \
      "## 목표" \
      "## 비목표" \
      "## 범위" \
      "## 참고 문서" \
      "## 완료 조건(AC)" \
      "## 세부 과업" \
      "## 검증 계획" \
      "## 종료 기준"
    ;;
  pr)
    check_sections "$text" "PR" \
      "## 요약" \
      "## 연결 이슈" \
      "## 범위 점검" \
      "## 주요 변경 사항" \
      "## TDD 근거" \
      "## 검증 결과" \
      "## Rule-Base 근거" \
      "## 리뷰 피드백 반영" \
      "## 리스크 / 롤백" \
      '## 릴리스 메모 (`develop` 머지 기준)'
    ;;
  commit)
    first_line="$(printf '%s' "$text" | sed -n '1p')"
    if ! printf '%s' "$first_line" | rg -q '^[a-z]+\([^)]+\): \[#([0-9]+)\] T-[0-9]+ .+'; then
      fail "커밋 제목 형식 불일치: $first_line"
    fi
    check_sections "$text" "Commit" \
      "이슈" \
      "배경" \
      "변경 사항" \
      "TDD 근거" \
      "Rule-Base 근거" \
      "검증" \
      "딴지 리뷰" \
      "리스크 및 롤백" \
      "다음 단계"
    ;;
  *)
    fail "지원하지 않는 프로필입니다: $profile"
    ;;
esac

echo "PASS: $source_desc"
