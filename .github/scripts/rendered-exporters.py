import argparse
import configparser
import pathlib
import posixpath
import sys

import yaml

EXPORTERS = {
    "node-exporter": ("DaemonSet", "node-exporter", "9100", "node"),
    "mysqld-exporter": ("Deployment", "prometheus-mysql-exporter", "9104", "mysqld"),
    "redis-exporter-auth": ("Deployment", "prometheus-redis-exporter", "9121", "redis-auth"),
    "redis-exporter-ratelimit": ("Deployment", "prometheus-redis-exporter", "9121", "redis-ratelimit"),
}

NODE_ARGS = [
    "--path.procfs=/host/proc",
    "--path.sysfs=/host/sys",
    "--web.listen-address=[$(HOST_IP)]:9100",
    "--collector.disable-defaults",
    "--collector.meminfo",
    "--collector.cpu",
    "--collector.pressure",
    "--collector.filesystem",
    "--collector.loadavg",
    "--collector.conntrack",
    "--collector.diskstats",
    "--collector.netdev",
    "--collector.netdev.device-exclude=^(lo|veth.*|cni.*|flannel.*)$",
]

MYSQLD_ARGS = [
    "--config.my-cnf=/etc/cnf/my.cnf",
    "--mysqld.address=$(MYSQLD_ADDRESS)",
    "--mysqld.username=$(MYSQL_EXPORTER_USER)",
    "--collect.perf_schema.eventsstatements",
    "--collect.perf_schema.tableiowaits",
]

MYSQLD_SECRET = "mysqld-exporter-dsn"
MYSQLD_ENV_KEYS = {
    "MYSQLD_ADDRESS": "endpoint",
    "MYSQL_EXPORTER_USER": "username",
    "MYSQLD_EXPORTER_PASSWORD": "password",
}
MYSQLD_CNF_FLAG = "--config.my-cnf="
MYSQLD_CNF_KEY = "my.cnf"
CA_CONFIG_MAP = "rds-ca-bundle"

REDIS_ADDRESSES = {
    "redis-exporter-auth": "redis://redis-auth:6379",
    "redis-exporter-ratelimit": "redis://redis-ratelimit:6379",
}
REDIS_PASSWORD_SOURCE = ("backend-runtime-config", "AUTH_REDIS_PASSWORD")
REDIS_ENV_NAMES = {
    "redis-exporter-auth": {"REDIS_ADDR", "REDIS_PASSWORD"},
    "redis-exporter-ratelimit": {"REDIS_ADDR"},
}


def read_objects(rendered_paths):
    for rendered_path in rendered_paths:
        for manifest in yaml.safe_load_all(pathlib.Path(rendered_path).read_text(encoding="utf-8")):
            if manifest:
                yield manifest


def read_ssl_ca(cnf_text):
    cnf_parser = configparser.ConfigParser()
    cnf_parser.read_string(cnf_text)
    return cnf_parser.get("client", "ssl-ca", fallback=None)


def named_object(manifests, kind_name, object_name):
    return next(
        (
            manifest
            for manifest in manifests
            if manifest["kind"] == kind_name and manifest["metadata"]["name"] == object_name
        ),
        {},
    )


def check_region(manifests):
    region_problems = []
    secret_spec = named_object(manifests, "ExternalSecret", MYSQLD_SECRET).get("spec") or {}
    secret_template = (secret_spec.get("target") or {}).get("template") or {}
    secret_keys = {data_entry["secretKey"] for data_entry in secret_spec.get("data") or []}
    for missing_key in sorted(set(MYSQLD_ENV_KEYS.values()) - secret_keys):
        region_problems.append(f"ExternalSecret {MYSQLD_SECRET} yields no key {missing_key}")
    if secret_template.get("mergePolicy") != "Merge":
        region_problems.append(f"the template of ExternalSecret {MYSQLD_SECRET} does not merge, so its data keys are not in the Secret")
    cnf_text = (secret_template.get("data") or {}).get(MYSQLD_CNF_KEY)
    if cnf_text is None:
        region_problems.append(f"the template of ExternalSecret {MYSQLD_SECRET} yields no key {MYSQLD_CNF_KEY}")
        return None, region_problems
    ssl_ca_path = read_ssl_ca(cnf_text)
    if ssl_ca_path is None:
        region_problems.append(f"{MYSQLD_CNF_KEY} of ExternalSecret {MYSQLD_SECRET} names no ssl-ca")
        return None, region_problems
    ca_keys = named_object(manifests, "ConfigMap", CA_CONFIG_MAP).get("data") or {}
    if posixpath.basename(ssl_ca_path) not in ca_keys:
        region_problems.append(f"ConfigMap {CA_CONFIG_MAP} has no key {posixpath.basename(ssl_ca_path)}, which ssl-ca names")
    return ssl_ca_path, region_problems


def env_by_name(exporter_container):
    return {env_entry["name"]: env_entry for env_entry in exporter_container.get("env") or []}


def secret_source(env_entry):
    key_ref = ((env_entry or {}).get("valueFrom") or {}).get("secretKeyRef") or {}
    return key_ref.get("name"), key_ref.get("key")


def find_metadata_problems(pod_metadata, exporter_name):
    _, _, port_text, job_name = EXPORTERS[exporter_name]
    expected_annotations = {
        "prometheus.io/scrape": "true",
        "prometheus.io/path": "/metrics",
        "prometheus.io/port": port_text,
        "prometheus.io/job": job_name,
    }
    pod_annotations = pod_metadata.get("annotations") or {}
    for annotation_name, expected_value in expected_annotations.items():
        found_value = pod_annotations.get(annotation_name)
        if found_value != expected_value:
            yield f"pod annotation {annotation_name} is {found_value!r}, want {expected_value!r}"
    app_label = (pod_metadata.get("labels") or {}).get("app")
    if app_label != exporter_name:
        yield f"pod label app is {app_label!r}, want {exporter_name!r}"


def find_arg_problems(exporter_container, expected_args):
    found_args = exporter_container.get("args") or []
    for extra_arg in sorted(set(found_args) - set(expected_args)):
        yield f"unexpected arg {extra_arg}"
    for missing_arg in sorted(set(expected_args) - set(found_args)):
        yield f"missing arg {missing_arg}"
    for repeated_arg in sorted({found_arg for found_arg in found_args if found_args.count(found_arg) > 1}):
        yield f"repeated arg {repeated_arg}"


def find_node_problems(pod_spec, exporter_container):
    yield from find_arg_problems(exporter_container, NODE_ARGS)
    root_volumes = set()
    for pod_volume in pod_spec.get("volumes") or []:
        host_path = (pod_volume.get("hostPath") or {}).get("path")
        if host_path and posixpath.normpath(host_path) == "/":
            root_volumes.add(pod_volume["name"])
            yield f"volume {pod_volume['name']} is the host's root"
    for pod_container in pod_spec["containers"]:
        for volume_mount in pod_container.get("volumeMounts") or []:
            if volume_mount["name"] in root_volumes:
                yield f"container {pod_container['name']} mounts the host's root at {volume_mount['mountPath']}"


def mounts_file(pod_spec, exporter_container, file_path, source_kind, name_field, source_name):
    directory_path, file_name = posixpath.split(posixpath.normpath(file_path))
    pod_volumes = {pod_volume["name"]: pod_volume for pod_volume in pod_spec.get("volumes") or []}
    for volume_mount in exporter_container.get("volumeMounts") or []:
        if posixpath.normpath(volume_mount["mountPath"]) != directory_path or "subPath" in volume_mount:
            continue
        volume_source = pod_volumes.get(volume_mount["name"], {}).get(source_kind) or {}
        volume_items = volume_source.get("items")
        holds_file = volume_items is None or any(
            (volume_item["key"], volume_item["path"]) == (file_name, file_name) for volume_item in volume_items
        )
        if volume_source.get(name_field) == source_name and holds_file:
            return True
    return False


def find_mysqld_problems(pod_spec, exporter_container, ssl_ca_paths):
    yield from find_arg_problems(exporter_container, MYSQLD_ARGS)
    env_entries = env_by_name(exporter_container)
    for env_name, secret_key in MYSQLD_ENV_KEYS.items():
        if secret_source(env_entries.get(env_name)) != (MYSQLD_SECRET, secret_key):
            yield f"env {env_name} does not come from Secret {MYSQLD_SECRET}, key {secret_key}"
    cnf_paths = [
        found_arg.removeprefix(MYSQLD_CNF_FLAG)
        for found_arg in exporter_container.get("args") or []
        if found_arg.startswith(MYSQLD_CNF_FLAG)
    ]
    if (
        len(cnf_paths) != 1
        or posixpath.basename(cnf_paths[0]) != MYSQLD_CNF_KEY
        or not mounts_file(pod_spec, exporter_container, cnf_paths[0], "secret", "secretName", MYSQLD_SECRET)
    ):
        yield f"no single arg {MYSQLD_CNF_FLAG}<path> points at key {MYSQLD_CNF_KEY} of a mounted Secret {MYSQLD_SECRET}"
    for ssl_ca_path in ssl_ca_paths:
        if not mounts_file(pod_spec, exporter_container, ssl_ca_path, "configMap", "name", CA_CONFIG_MAP):
            yield f"{posixpath.dirname(ssl_ca_path)} is not a mount of ConfigMap {CA_CONFIG_MAP}, so ssl-ca {ssl_ca_path} is not its file"


def find_redis_problems(exporter_container, exporter_name):
    yield from find_arg_problems(exporter_container, [])
    env_entries = env_by_name(exporter_container)
    found_address = env_entries.get("REDIS_ADDR", {}).get("value")
    expected_address = REDIS_ADDRESSES[exporter_name]
    if found_address != expected_address:
        yield f"env REDIS_ADDR is {found_address!r}, want {expected_address!r}"
    expected_names = REDIS_ENV_NAMES[exporter_name]
    if "REDIS_PASSWORD" in expected_names and secret_source(env_entries.get("REDIS_PASSWORD")) != REDIS_PASSWORD_SOURCE:
        yield "env REDIS_PASSWORD does not come from Secret {}, key {}".format(*REDIS_PASSWORD_SOURCE)
    for extra_name in sorted(set(env_entries) - expected_names):
        yield f"unexpected env {extra_name}"


def find_problems(manifests, ssl_ca_paths):
    problem_lines = []
    for exporter_name, (kind_name, container_name, _, _) in EXPORTERS.items():
        workloads = [
            manifest
            for manifest in manifests
            if manifest["kind"] == kind_name and manifest["metadata"]["name"] == exporter_name
        ]
        if len(workloads) != 1:
            problem_lines.append(f"{kind_name} {exporter_name}: rendered {len(workloads)} times, want once")
            continue
        pod_template = workloads[0]["spec"]["template"]
        exporter_problems = list(find_metadata_problems(pod_template["metadata"], exporter_name))
        pod_spec = pod_template["spec"]
        exporter_container = next(
            (pod_container for pod_container in pod_spec["containers"] if pod_container["name"] == container_name), None
        )
        if exporter_container is None:
            exporter_problems.append(f"no container {container_name}")
        elif exporter_name == "node-exporter":
            exporter_problems += find_node_problems(pod_spec, exporter_container)
        elif exporter_name == "mysqld-exporter":
            exporter_problems += find_mysqld_problems(pod_spec, exporter_container, ssl_ca_paths)
        else:
            exporter_problems += find_redis_problems(exporter_container, exporter_name)
        problem_lines += [f"{kind_name} {exporter_name}: {exporter_problem}" for exporter_problem in exporter_problems]
    return problem_lines


def main(arguments):
    argument_parser = argparse.ArgumentParser(prog="rendered-exporters.py", description="needs PyYAML")
    argument_parser.add_argument("--regions", nargs="+", required=True, metavar="rendered.yaml")
    argument_parser.add_argument("--charts", nargs="+", required=True, metavar="chart-render.yaml")
    parsed_arguments = argument_parser.parse_args(arguments)
    problem_lines = []
    ssl_ca_paths = set()
    for region_path in parsed_arguments.regions:
        ssl_ca_path, region_problems = check_region(list(read_objects([region_path])))
        problem_lines += [f"{pathlib.Path(region_path).name}: {region_problem}" for region_problem in region_problems]
        if ssl_ca_path is not None:
            ssl_ca_paths.add(ssl_ca_path)
    problem_lines += find_problems(list(read_objects(parsed_arguments.charts)), sorted(ssl_ca_paths))
    for problem_line in problem_lines:
        print(problem_line)
    return 1 if problem_lines else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
