#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 <out-dir> <rendered.yaml>...   (writes <out-dir>/chart-<name>.yaml for every Application in the rendered files and prints 'rendered <name>' per Application; needs docker and python3 with PyYAML)" >&2
  exit 2
}

[ $# -ge 2 ] || usage
. "$(dirname "$0")/images.sh"

out_dir=$(cd "$1" && pwd)
shift
values_dir=$(mktemp -d)
trap 'rm -rf "$values_dir"' EXIT

apps=$(python3 - "$values_dir" "$@" <<'PY'
import json
import pathlib
import sys

import yaml

RENDERED_HELM_FIELDS = {"releaseName", "valuesObject"}

values_dir = pathlib.Path(sys.argv[1])
applications = {}
for path in map(pathlib.Path, sys.argv[2:]):
    for doc in yaml.safe_load_all(path.read_text()):
        if not doc or doc.get("kind") != "Application":
            continue
        name = doc["metadata"]["name"]
        if name in applications:
            if applications[name] != doc:
                sys.exit(f"render-charts: Application {name} ({path.name}) differs from the one rendered before it; an Application is identical in every region")
            continue
        applications[name] = doc
        spec = doc["spec"]
        if "sources" in spec:
            sys.exit(f"render-charts: Application {name} ({path.name}) uses spec.sources, which this renderer cannot represent; use a single spec.source")
        source = spec["source"]
        if "chart" not in source:
            sys.exit(f"render-charts: Application {name} ({path.name}) has no spec.source.chart; this renderer handles Helm chart sources only, and every Application in the render installs a chart")
        helm = source.get("helm", {})
        unsupported = sorted(set(helm) - RENDERED_HELM_FIELDS)
        if unsupported:
            sys.exit(f"render-charts: Application {name} ({path.name}) sets spec.source.helm.{', spec.source.helm.'.join(unsupported)}, which this renderer cannot represent; put the values in spec.source.helm.valuesObject")
        (values_dir / f"{name}.yaml").write_text(json.dumps(helm.get("valuesObject", {})))
        print("\t".join([
            name,
            source["repoURL"],
            source["chart"],
            str(source["targetRevision"]),
            helm.get("releaseName", name),
            spec["destination"]["namespace"],
        ]))
PY
)

while IFS=$'\t' read -r name repo_url chart version release namespace; do
  [ -n "$name" ] || continue
  if ! docker run --rm -v "$values_dir:/values:ro" "$HELM_IMAGE" template "$release" "$chart" \
    --repo "$repo_url" --version "$version" --namespace "$namespace" -f "/values/$name.yaml" \
    >"$out_dir/chart-$name.yaml" 2>"$values_dir/$name.err"; then
    cat "$values_dir/$name.err" >&2
    echo "render-charts: helm template failed for Application $name ($chart $version from $repo_url)" >&2
    exit 1
  fi
  [ -s "$out_dir/chart-$name.yaml" ] || { echo "render-charts: Application $name rendered nothing" >&2; exit 1; }
  echo "rendered $name"
done <<<"$apps"
