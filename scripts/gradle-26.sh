#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_VERSION="9.5.1"
GRADLE_HOME="${ROOT_DIR}/.gradle-26/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${ROOT_DIR}/.gradle-26/gradle-${GRADLE_VERSION}-bin.zip"

if [[ ! -x "${GRADLE_HOME}/bin/gradle" ]]; then
  mkdir -p "${ROOT_DIR}/.gradle-26"
  if [[ ! -f "${GRADLE_ZIP}" ]]; then
    curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${GRADLE_ZIP}"
  fi
  unzip -q "${GRADLE_ZIP}" -d "${ROOT_DIR}/.gradle-26"
fi

exec "${GRADLE_HOME}/bin/gradle" --project-dir "${ROOT_DIR}/build-26" "$@"
