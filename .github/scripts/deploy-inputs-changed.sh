#!/bin/sh
set -eu

verified=${1:-}
event=${2:-}
values_file=${3:-deploy/chart/values-oregon.yaml}
set -- backend/Dockerfile backend/build.gradle.kts backend/settings.gradle.kts backend/src \
  backend/gradlew backend/gradle backend/lombok.config .dockerignore static.lock testdata/planner-content.schema.json

if [ "$verified" = "--list-inputs" ]; then
  printf '%s\n' "$@"
  exit 0
fi

if [ -z "$verified" ] || [ -z "$event" ]; then
  echo "usage: $0 <verified-sha> <event-name> [values-path] | --list-inputs" >&2
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

values=$(git show "$verified:$values_file") || {
  echo "cannot read $values_file at $verified" >&2
  exit 1
}
tag_lines=$(printf '%s\n' "$values" | sed -n '/^[[:space:]]*tag:/p')
deployed=$(printf '%s\n' "$tag_lines" | sed -n 's/^  tag: "\([0-9a-f]\{40\}\)"$/\1/p')
if [ -z "$deployed" ] || [ "$tag_lines" != "  tag: \"$deployed\"" ]; then
  echo "$values_file at $verified does not hold exactly one tag line with 40 hex in double quotes" >&2
  exit 1
fi

if ! git cat-file -e "$deployed^{commit}" 2>/dev/null; then
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
