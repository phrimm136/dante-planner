import json
import pathlib
import sys
import urllib.request

import yaml

SOURCES = [
    "https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/690f754646e8326128fd686e3e46117ac479cfdf/config/crd/standard/gateway.networking.k8s.io_gatewayclasses.yaml",
    "https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/690f754646e8326128fd686e3e46117ac479cfdf/config/crd/standard/gateway.networking.k8s.io_gateways.yaml",
    "https://raw.githubusercontent.com/kubernetes-sigs/gateway-api/690f754646e8326128fd686e3e46117ac479cfdf/config/crd/standard/gateway.networking.k8s.io_httproutes.yaml",
    "https://raw.githubusercontent.com/traefik/traefik/e90f4a7cb4e2a457ce72b292c2ce383ae71661eb/docs/content/reference/dynamic-configuration/kubernetes-crd-definition-v1.yml",
    "https://raw.githubusercontent.com/external-secrets/external-secrets/a50dff013c8a6e0fc12f2e6ab78172b7b4ae46bd/deploy/crds/bundle.yaml",
]

KINDS = {
    ("gateway.networking.k8s.io", "GatewayClass", "v1"),
    ("gateway.networking.k8s.io", "Gateway", "v1"),
    ("gateway.networking.k8s.io", "HTTPRoute", "v1"),
    ("traefik.io", "TLSOption", "v1alpha1"),
    ("external-secrets.io", "ExternalSecret", "v1beta1"),
    ("external-secrets.io", "SecretStore", "v1beta1"),
}


def strict(node, root=False):
    if isinstance(node, dict):
        out = {k: strict(v) for k, v in node.items()}
        if "properties" in out and not root:
            out.setdefault("additionalProperties", False)
        return out
    if isinstance(node, list):
        return [strict(v) for v in node]
    return node


def main(out_dir):
    wanted = set(KINDS)
    for url in SOURCES:
        with urllib.request.urlopen(url) as response:
            docs = list(yaml.safe_load_all(response))
        for crd in docs:
            if not crd or crd.get("kind") != "CustomResourceDefinition":
                continue
            group = crd["spec"]["group"]
            kind = crd["spec"]["names"]["kind"]
            for version in crd["spec"]["versions"]:
                key = (group, kind, version["name"])
                if key not in wanted:
                    continue
                schema = strict(version["schema"]["openAPIV3Schema"], root=True)
                path = out_dir / group / f"{kind.lower()}_{version['name']}.json"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(schema, indent=2, sort_keys=True) + "\n")
                wanted.discard(key)
                print(f"wrote {path}")
    if wanted:
        sys.exit(f"no CRD in SOURCES defines {sorted(wanted)}")


if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).parent / "schemas")
