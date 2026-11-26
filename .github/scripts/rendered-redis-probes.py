import pathlib
import sys

import yaml

REDIS_CONTAINER = "redis"
REDIS_PORT = 6379
PASSWORD_ENV = "REDIS_AUTH_PASSWORD"
TCP_PROBES = ("livenessProbe",)

REDIS_WORKLOADS = {
    ("StatefulSet", "redis-auth"): ["sh", "-c", f'[ "$(REDISCLI_AUTH="${PASSWORD_ENV}" redis-cli PING)" = PONG ]'],
    ("Deployment", "redis-ratelimit"): ["sh", "-c", '[ "$(redis-cli PING)" = PONG ]'],
}


def read_objects(rendered_path):
    for manifest in yaml.safe_load_all(pathlib.Path(rendered_path).read_text(encoding="utf-8")):
        if manifest:
            yield manifest


def port_number(redis_container, probe_port):
    named_ports = {
        container_port.get("name"): container_port["containerPort"] for container_port in redis_container.get("ports") or []
    }
    return named_ports.get(probe_port, probe_port)


def find_container_problems(redis_container, readiness_command):
    for probe_name in TCP_PROBES:
        tcp_port = ((redis_container.get(probe_name) or {}).get("tcpSocket") or {}).get("port")
        if port_number(redis_container, tcp_port) != REDIS_PORT:
            yield f"{probe_name} is not a tcpSocket probe on port {REDIS_PORT}"
    found_command = ((redis_container.get("readinessProbe") or {}).get("exec") or {}).get("command")
    if found_command != readiness_command:
        yield f"readinessProbe runs {found_command!r}, want {readiness_command!r}, which passes only on the reply PONG"
    if any(env_entry["name"] == "REDISCLI_AUTH" for env_entry in redis_container.get("env") or []):
        yield "env REDISCLI_AUTH is set on the container, so every redis-cli run in it authenticates"


def find_problems(manifests):
    problem_lines = []
    for (kind_name, workload_name), readiness_command in REDIS_WORKLOADS.items():
        subject = f"{kind_name} {workload_name}"
        workloads = [
            manifest
            for manifest in manifests
            if manifest["kind"] == kind_name and manifest["metadata"]["name"] == workload_name
        ]
        if len(workloads) != 1:
            problem_lines.append(f"{subject}: rendered {len(workloads)} times, want once")
            continue
        redis_container = next(
            (
                pod_container
                for pod_container in workloads[0]["spec"]["template"]["spec"]["containers"]
                if pod_container["name"] == REDIS_CONTAINER
            ),
            None,
        )
        if redis_container is None:
            problem_lines.append(f"{subject}: no container {REDIS_CONTAINER}")
            continue
        problem_lines += [
            f"{subject}: {container_problem}"
            for container_problem in find_container_problems(redis_container, readiness_command)
        ]
    return problem_lines


def main(rendered_paths):
    if not rendered_paths:
        print("usage: rendered-redis-probes.py <rendered.yaml>...   (one region render each; needs PyYAML)", file=sys.stderr)
        return 2
    problem_lines = [
        f"{pathlib.Path(rendered_path).name}: {problem_line}"
        for rendered_path in rendered_paths
        for problem_line in find_problems(list(read_objects(rendered_path)))
    ]
    for problem_line in problem_lines:
        print(problem_line)
    return 1 if problem_lines else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
