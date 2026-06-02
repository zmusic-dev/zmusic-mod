#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[publish-marketplace] %s\n' "$*"
}

log_json() {
  jq . <<<"$1" || printf '%s\n' "$1"
}

fail() {
  printf '[publish-marketplace] %s\n' "$*" >&2
  exit 1
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    fail "missing required environment variable: $name"
  fi
}

api_request() {
  local method="$1"
  local url="$2"
  shift 2

  local response_file
  response_file="$(mktemp)"

  API_RESPONSE_STATUS="$(
    curl \
      --silent \
      --show-error \
      --location \
      --request "$method" \
      --output "$response_file" \
      --write-out '%{http_code}' \
      "$@" \
      "$url"
  )"
  API_RESPONSE_BODY="$(cat "$response_file")"
  rm -f "$response_file"
}

expect_status() {
  local actual="$1"
  shift

  local expected
  for expected in "$@"; do
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
  done

  fail "unexpected HTTP status: $actual, response: ${API_RESPONSE_BODY:-<empty>}"
}

loader_display_name() {
  case "$1" in
    fabric) printf 'Fabric' ;;
    forge) printf 'Forge' ;;
    neoforge) printf 'NeoForge' ;;
    *) fail "unsupported loader: $1" ;;
  esac
}

publish_modrinth() {
  require_env MODRINTH_PROJECT_ID

  local metadata existing_count
  metadata="$(
    jq -cn \
      --arg name "$RELEASE_NAME" \
      --arg version_number "$RELEASE_VERSION" \
      --arg project_id "$MODRINTH_PROJECT_ID" \
      --arg loader "$MOD_LOADER" \
      --arg game_version "$MC_VERSION" \
      --arg changelog "$CHANGELOG" \
      '{
        name: $name,
        version_number: $version_number,
        changelog: $changelog,
        dependencies: [],
        game_versions: [$game_version],
        version_type: "release",
        loaders: [$loader],
        featured: false,
        status: "listed",
        requested_status: "listed",
        project_id: $project_id,
        file_parts: ["file"],
        primary_file: "file"
      }'
  )"

  if [[ "$DRY_RUN" == "true" ]]; then
    log "DRY_RUN Modrinth metadata:"
    log_json "$metadata"
    return
  fi

  require_env MODRINTH_TOKEN

  api_request \
    GET \
    "${MODRINTH_API_BASE_URL%/}/project/${MODRINTH_PROJECT_ID}/version" \
    -H "Authorization: Bearer $MODRINTH_TOKEN" \
    -H "Accept: application/json"
  expect_status "$API_RESPONSE_STATUS" 200

  existing_count="$(
    jq \
      --arg version_number "$RELEASE_VERSION" \
      --arg loader "$MOD_LOADER" \
      --arg game_version "$MC_VERSION" \
      --arg filename "$FILE_NAME" \
      '[.[] | select(
        .version_number == $version_number
        and (.loaders | index($loader))
        and (.game_versions | index($game_version))
        and ([.files[]?.filename] | index($filename))
      )] | length' \
      <<<"$API_RESPONSE_BODY"
  )"

  if [[ "$existing_count" != "0" ]]; then
    log "Modrinth already has $FILE_NAME; skipping"
    return
  fi

  log "Publishing Modrinth: $RELEASE_NAME ($MOD_LOADER $MC_VERSION)"
  api_request \
    POST \
    "${MODRINTH_API_BASE_URL%/}/version" \
    -H "Authorization: Bearer $MODRINTH_TOKEN" \
    -H "Accept: application/json" \
    -F "data=$metadata" \
    -F "file=@${FILE_PATH};filename=${FILE_NAME}"
  expect_status "$API_RESPONSE_STATUS" 200
  log "Modrinth response:"
  log_json "$API_RESPONSE_BODY"
}

publish_curseforge() {
  require_env CURSEFORGE_PROJECT_ID

  local metadata loader_name
  loader_name="$(loader_display_name "$MOD_LOADER")"
  metadata="$(
    jq -cn \
      --arg changelog "$CHANGELOG" \
      --arg display_name "$RELEASE_NAME" \
      --arg loader_name "$loader_name" \
      --arg game_version "$MC_VERSION" \
      '{
        changelog: $changelog,
        changelogType: "markdown",
        displayName: $display_name,
        gameVersionNames: [$loader_name, $game_version],
        releaseType: "release"
      }'
  )"

  if [[ "$DRY_RUN" == "true" ]]; then
    log "DRY_RUN CurseForge metadata:"
    log_json "$metadata"
    return
  fi

  require_env CURSEFORGE_TOKEN

  log "Publishing CurseForge: $RELEASE_NAME ($loader_name $MC_VERSION)"
  api_request \
    POST \
    "${CURSEFORGE_API_BASE_URL%/}/api/projects/${CURSEFORGE_PROJECT_ID}/upload-file" \
    -H "X-Api-Token: $CURSEFORGE_TOKEN" \
    -H "Accept: application/json" \
    -F "metadata=$metadata" \
    -F "file=@${FILE_PATH};filename=${FILE_NAME}"
  expect_status "$API_RESPONSE_STATUS" 200
  log "CurseForge response:"
  log_json "$API_RESPONSE_BODY"
}

main() {
  require_env FILE_PATH
  require_env MOD_LOADER
  require_env MC_VERSION
  require_env RELEASE_VERSION

  if [[ ! -f "$FILE_PATH" ]]; then
    fail "file does not exist: $FILE_PATH"
  fi

  FILE_NAME="$(basename "$FILE_PATH")"
  RELEASE_NAME="${RELEASE_NAME:-ZMusic ${RELEASE_VERSION} ${MOD_LOADER} ${MC_VERSION}}"
  CHANGELOG="${CHANGELOG:-}"
  MODRINTH_API_BASE_URL="${MODRINTH_API_BASE_URL:-https://api.modrinth.com/v2}"
  CURSEFORGE_API_BASE_URL="${CURSEFORGE_API_BASE_URL:-https://minecraft.curseforge.com}"
  PUBLISH_MODRINTH="${PUBLISH_MODRINTH:-true}"
  PUBLISH_CURSEFORGE="${PUBLISH_CURSEFORGE:-true}"
  DRY_RUN="${DRY_RUN:-false}"

  case "$DRY_RUN" in
    true | false) ;;
    *) fail "DRY_RUN must be true or false" ;;
  esac

  case "$PUBLISH_MODRINTH" in
    true) publish_modrinth ;;
    false) log "Skipping Modrinth" ;;
    *) fail "PUBLISH_MODRINTH must be true or false" ;;
  esac

  case "$PUBLISH_CURSEFORGE" in
    true) publish_curseforge ;;
    false) log "Skipping CurseForge" ;;
    *) fail "PUBLISH_CURSEFORGE must be true or false" ;;
  esac
}

main "$@"
