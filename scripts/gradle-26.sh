#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_VERSION="9.5.1"
GRADLE_HOME="${ROOT_DIR}/.gradle-26/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${ROOT_DIR}/.gradle-26/gradle-${GRADLE_VERSION}-bin.zip"
PATCHED_NEOFORM_DIR="${ROOT_DIR}/.gradle-26/patched-maven/net/neoforged/neoform/26.1.2-1"
PATCHED_NEOFORM_ZIP="${PATCHED_NEOFORM_DIR}/neoform-26.1.2-1.zip"

if [[ ! -x "${GRADLE_HOME}/bin/gradle" ]]; then
  mkdir -p "${ROOT_DIR}/.gradle-26"
  if [[ ! -f "${GRADLE_ZIP}" ]]; then
    curl -fL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${GRADLE_ZIP}"
  fi
  unzip -q "${GRADLE_ZIP}" -d "${ROOT_DIR}/.gradle-26"
fi

if [[ ! -f "${PATCHED_NEOFORM_ZIP}" ]]; then
  WORK_DIR="$(mktemp -d)"
  trap 'rm -rf "${WORK_DIR}"' EXIT

  mkdir -p "${PATCHED_NEOFORM_DIR}"
  curl -fsSL "https://maven.neoforged.net/releases/net/neoforged/neoform/26.1.2-1/neoform-26.1.2-1.zip" \
    -o "${WORK_DIR}/neoform.zip"
  curl -fsSL "https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.67-beta/neoforge-26.1.2.67-beta-userdev.jar" \
    -o "${WORK_DIR}/userdev.jar"

  mkdir -p "${WORK_DIR}/neoform"
  unzip -q "${WORK_DIR}/neoform.zip" -d "${WORK_DIR}/neoform"
  unzip -p "${WORK_DIR}/userdev.jar" patches.lzma > "${WORK_DIR}/neoform/patches.lzma"
  (
    cd "${WORK_DIR}/neoform"
    jar cf "${PATCHED_NEOFORM_ZIP}" .
  )

  cat > "${PATCHED_NEOFORM_DIR}/neoform-26.1.2-1.pom" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>net.neoforged</groupId>
  <artifactId>neoform</artifactId>
  <version>26.1.2-1</version>
</project>
POM
fi

exec "${GRADLE_HOME}/bin/gradle" --project-dir "${ROOT_DIR}/build-26" "$@"
