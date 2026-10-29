#!/bin/sh
set -eu

verified=${1:-}
event=${2:-}
kustomization=${3:-deploy/overlays/oregon/kustomization.yaml}
set -- backend/Dockerfile backend/build.gradle.kts backend/settings.gradle.kts backend/src \
  .dockerignore static.lock testdata/planner-content.schema.json

if [ "$verified" = "--list-inputs" ]; then
  printf '%s\n' "$@"
  exit 0
fi

if [ -z "$verified" ] || [ -z "$event" ]; then
  echo "usage: $0 <verified-sha> <event-name> [kustomization-path] | --list-inputs" >&2
  exit 2
fi

emit() {
  echo "backend=$1"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "backend=$1" >> "$GITHUB_OUTPUT"
  fi
}

if [ "$event" = "workflow_dispatch" ]; then
  echo "workflow_dispatch: deploying $verified unconditionally"
  emit true
  exit 0
fi

deployed=$(git show "$verified:$kustomization" 2>/dev/null \
  | sed -n 's/^[[:space:]]*newTag:[[:space:]]*//p' | tr -d "\"' \r" | head -n 1)

if [ -z "$deployed" ] || ! git cat-file -e "$deployed^{commit}" 2>/dev/null; then
  echo "deployed tag '$deployed' is not a commit in this history: deploying $verified"
  emit true
  exit 0
fi

if git diff --quiet "$deployed" "$verified" -- "$@"; then
  echo "image inputs ($*) unchanged from deployed $deployed to verified $verified: skipping deploy"
  emit false
else
  echo "image inputs ($*) changed from deployed $deployed to verified $verified: deploying"
  emit true
fi
