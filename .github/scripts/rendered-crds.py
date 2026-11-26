import pathlib
import sys

import yaml

BUILTIN_GROUPS = {
    "",
    "admissionregistration.k8s.io",
    "apiextensions.k8s.io",
    "apiregistration.k8s.io",
    "apps",
    "autoscaling",
    "batch",
    "coordination.k8s.io",
    "discovery.k8s.io",
    "networking.k8s.io",
    "policy",
    "rbac.authorization.k8s.io",
    "scheduling.k8s.io",
    "storage.k8s.io",
}

OUTSIDE_GROUPS = {
    # ArgoCD's own install (core-install.yaml, applied by the CP boot script)
    "argoproj.io",
    # the CP boot script, from the Gateway API release
    "gateway.networking.k8s.io",
    # the CP boot script, from the Traefik repository
    "traefik.io",
}


def flattened(manifest):
    if manifest.get("kind", "").endswith("List") and isinstance(manifest.get("items"), list):
        for list_item in manifest["items"]:
            yield from flattened(list_item)
    else:
        yield manifest


def read_objects(rendered_paths):
    for rendered_path in rendered_paths:
        for manifest in yaml.safe_load_all(pathlib.Path(rendered_path).read_text(encoding="utf-8")):
            if manifest:
                yield from flattened(manifest)


def group_and_version(manifest):
    group_name, _, version_name = manifest["apiVersion"].rpartition("/")
    return group_name, version_name


def object_name(manifest):
    metadata = manifest.get("metadata", {})
    namespace_name = metadata.get("namespace")
    return f"{namespace_name}/{metadata['name']}" if namespace_name else metadata["name"]


def rendered_definitions(manifests):
    served_versions = {}
    for manifest in manifests:
        if manifest["kind"] != "CustomResourceDefinition" or group_and_version(manifest)[0] != "apiextensions.k8s.io":
            continue
        crd_spec = manifest["spec"]
        crd_versions = {version["name"] for version in crd_spec["versions"] if version["served"]}
        crd_key = (crd_spec["group"], crd_spec["names"]["kind"])
        served_versions[crd_key] = served_versions.get(crd_key, crd_versions) & crd_versions
    return served_versions


def find_problems(manifests, outside_groups):
    served_versions = rendered_definitions(manifests)
    rendered_groups = {crd_group for crd_group, _ in served_versions}
    used_groups = set()
    problem_lines = set()
    for manifest in manifests:
        group_name, version_name = group_and_version(manifest)
        if group_name in BUILTIN_GROUPS:
            continue
        used_groups.add(group_name)
        kind_name = manifest["kind"]
        subject = f"{group_name}/{version_name} {kind_name} {object_name(manifest)}"
        crd_versions = served_versions.get((group_name, kind_name))
        if group_name not in rendered_groups:
            if group_name not in outside_groups:
                problem_lines.add(f"{subject}: no CRD of group {group_name} is rendered, and the group is not in OUTSIDE_GROUPS")
        elif crd_versions is None:
            problem_lines.add(f"{subject}: CRDs of group {group_name} are rendered, but none defines kind {kind_name}")
        elif version_name not in crd_versions:
            served_text = ", ".join(sorted(crd_versions)) or "none"
            problem_lines.add(f"{subject}: its rendered CRD does not serve {version_name} (served: {served_text})")
    for group_name in outside_groups & rendered_groups:
        problem_lines.add(f"OUTSIDE_GROUPS lists {group_name}, but a CRD of that group is rendered; remove the entry")
    for group_name in outside_groups - rendered_groups - used_groups:
        problem_lines.add(f"OUTSIDE_GROUPS lists {group_name}, but no rendered object uses that group; remove the entry")
    return sorted(problem_lines)


def main(rendered_paths, outside_groups):
    if not rendered_paths:
        print("usage: rendered-crds.py <rendered.yaml>...   (every region render and chart render, as one set; needs PyYAML)", file=sys.stderr)
        return 2
    problem_lines = find_problems(list(read_objects(rendered_paths)), outside_groups)
    for problem_line in problem_lines:
        print(problem_line)
    return 1 if problem_lines else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], OUTSIDE_GROUPS))
