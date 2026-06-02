#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
changelog_file="${CHANGELOG_FILE:-CHANGELOG.md}"

if [ -z "$tag" ]; then
  printf 'usage: %s <tag>\n' "$0" >&2
  exit 2
fi

if [ ! -f "$changelog_file" ]; then
  printf 'changelog file does not exist: %s\n' "$changelog_file" >&2
  exit 1
fi

awk -v tag="$tag" '
  function trim(value) {
    sub(/^[[:space:]]+/, "", value)
    sub(/[[:space:]]+$/, "", value)
    return value
  }

  /^##[[:space:]]+/ {
    heading = $0
    sub(/^##[[:space:]]+/, "", heading)
    heading = trim(heading)
    if (substr(heading, 1, 1) == "[") {
      sub(/^\[/, "", heading)
      sub(/\].*$/, "", heading)
    }

    if (found) {
      exit
    }
    if (heading == tag) {
      found = 1
      next
    }
  }

  found {
    print
    if ($0 ~ /[^[:space:]]/) {
      printed = 1
    }
  }

  END {
    if (!found) {
      printf "missing changelog section for %s\n", tag > "/dev/stderr"
      exit 1
    }
    if (!printed) {
      printf "empty changelog section for %s\n", tag > "/dev/stderr"
      exit 1
    }
  }
' "$changelog_file" | sed '/./,$!d'
