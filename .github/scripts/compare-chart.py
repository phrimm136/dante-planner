#!/usr/bin/env python3
"""Compare a kustomize render with the chart's render of the same region: compare-chart.py <kustomize.yaml> <chart.yaml>."""

import re
import sys

import yaml

HASHED_CONFIGMAP = re.compile(r"^(mysqld-tls-cnf|rds-ca-bundle)-[a-z0-9]{10}$")
CHECKSUM = "checksum/config"


def unhashed(value):
    if isinstance(value, dict):
        return {key: unhashed(item) for key, item in value.items()}
    if isinstance(value, list):
        return [unhashed(item) for item in value]
    if isinstance(value, str) and (match := HASHED_CONFIGMAP.match(value)):
        return match.group(1)
    return value


def objects(path, from_kustomize):
    found = {}
    with open(path) as render:
        for doc in yaml.safe_load_all(render):
            if not doc:
                continue
            meta = doc["metadata"]
            if from_kustomize:
                doc = unhashed(doc)
                meta = doc["metadata"]
                if doc["kind"] == "GatewayClass":
                    meta.pop("namespace", None)
            elif doc["kind"] == "Deployment" and meta["name"] == "mysqld-exporter":
                if not doc["spec"]["template"]["metadata"]["annotations"].pop(CHECKSUM, None):
                    sys.exit(f"Deployment mysqld-exporter carries no {CHECKSUM} annotation")
            key = (doc["apiVersion"], doc["kind"], meta.get("namespace", ""), meta["name"])
            if key in found:
                sys.exit(f"{path} holds {key} twice")
            found[key] = doc
    return found


def differences(left, right, path=""):
    if isinstance(left, dict) and isinstance(right, dict):
        for key in sorted(set(left) | set(right)):
            if key not in left or key not in right:
                yield f"{path}.{key} only in {'chart' if key not in left else 'kustomize'}"
            else:
                yield from differences(left[key], right[key], f"{path}.{key}")
    elif isinstance(left, list) and isinstance(right, list) and len(left) == len(right):
        for index, (left_item, right_item) in enumerate(zip(left, right)):
            yield from differences(left_item, right_item, f"{path}[{index}]")
    elif left != right or type(left) is not type(right):
        yield f"{path}: kustomize {left!r:.120}, chart {right!r:.120}"


def main():
    kustomize, chart = objects(sys.argv[1], True), objects(sys.argv[2], False)
    problems = [f"{' '.join(key)} only in {'kustomize' if key in kustomize else 'chart'}"
                for key in sorted(set(kustomize) ^ set(chart))]
    for key in sorted(set(kustomize) & set(chart)):
        problems += [f"{key[1]} {key[3]} {line}" for line in differences(kustomize[key], chart[key])]
    if problems or not kustomize:
        sys.exit("\n".join(problems) or "the kustomize render holds no object")
    print(len(kustomize))


main()
