import dataclasses
import datetime
import json
import os
import re
import shlex
import shutil
import signal
import subprocess
import sys
from pathlib import Path

from opscli.paths import (
    AWS_TIMEOUT_SECONDS, CLI_PATH, CONSTANTS_PATH, DOCKER_TIMEOUT_SECONDS, ENV_PROFILES, EXPIRED_MARKERS,
    OpsError, STS_ARGV, TUNNEL_DOCUMENT, TUNNEL_PLUGIN)
from opscli.redaction import account_label, caller_name


def shell_constant(repo_root, constant_name):
    constants_file = repo_root / CONSTANTS_PATH
    value_match = re.search(rf'^{constant_name}="([^"]*)"', constants_file.read_text(encoding="utf-8"),
                            re.MULTILINE) if constants_file.is_file() else None
    if not value_match:
        raise OpsError(f"{CONSTANTS_PATH} defines no {constant_name}; restore it from git")
    return value_match.group(1)


def login_command(repo_root):
    return f"aws sso login --sso-session {shell_constant(repo_root, 'AWS_SSO_SESSION')}"


@dataclasses.dataclass(frozen=True)
class SessionState:
    profile_name: str
    state_name: str
    detail_text: str


class SystemEffects:
    def run_captured(self, command_argv, child_env=None, work_dir=None, timeout_seconds=None):
        return subprocess.run(command_argv, capture_output=True, text=True, env=child_env, cwd=work_dir,
                              timeout=timeout_seconds, stdin=subprocess.DEVNULL)

    def run_split(self, command_argv, child_env, work_dir):
        try:
            return subprocess.run(command_argv, capture_output=True, encoding="utf-8", errors="replace",
                                  env=child_env, cwd=work_dir, stdin=subprocess.DEVNULL)
        except FileNotFoundError:
            raise OpsError(f"{command_argv[0]} is not installed or not on PATH; run ops doctor") from None

    def run_streamed(self, command_argv, child_env, work_dir, log_file, echo_output):
        try:
            child_process = subprocess.Popen(command_argv, cwd=work_dir, env=child_env, stdin=subprocess.DEVNULL,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                             start_new_session=True)
        except FileNotFoundError:
            raise OpsError(f"{command_argv[0]} is not installed or not on PATH; run ops doctor") from None
        try:
            for raw_line in child_process.stdout:
                text_line = raw_line.decode(errors="replace")
                log_file.write(text_line)
                if echo_output:
                    sys.stdout.write(text_line)
                    sys.stdout.flush()
            return child_process.wait()
        except KeyboardInterrupt:
            os.killpg(child_process.pid, signal.SIGINT)
            child_process.wait()
            raise

    def process_rows(self):
        found_rows = []
        for proc_dir in Path("/proc").glob("[0-9]*"):
            try:
                raw_bytes = (proc_dir / "cmdline").read_bytes()
            except OSError:
                continue
            if raw_bytes:
                found_rows.append((int(proc_dir.name), tuple(raw_bytes.rstrip(b"\0").decode(errors="replace")
                                                             .split("\0"))))
        return found_rows

    def tool_path(self, tool_name):
        return shutil.which(tool_name) or ""


def session_state(system_effects, profile_name):
    try:
        sts_run = system_effects.run_captured([*STS_ARGV, "--profile", profile_name],
                                              timeout_seconds=AWS_TIMEOUT_SECONDS)
    except FileNotFoundError:
        return SessionState(profile_name, "missing", "no aws CLI on PATH; install the AWS CLI v2")
    except subprocess.TimeoutExpired:
        return SessionState(profile_name, "failed", f"aws sts timed out after {AWS_TIMEOUT_SECONDS}s")
    if sts_run.returncode == 0:
        try:
            identity_data = json.loads(sts_run.stdout)
        except json.JSONDecodeError:
            identity_data = {}
        return SessionState(profile_name, "valid",
                            f"account {account_label(str(identity_data.get('Account', '?')))} as "
                            f"{caller_name(str(identity_data.get('Arn', '?')))}")
    error_text = " ".join((sts_run.stderr or sts_run.stdout).split())
    if any(marker_text in error_text for marker_text in EXPIRED_MARKERS):
        return SessionState(profile_name, "expired", error_text)
    return SessionState(profile_name, "failed", error_text or f"aws sts exit {sts_run.returncode}")


def session_failure(repo_root, one_state):
    if one_state.state_name == "expired":
        return (f"the SSO session for profile '{one_state.profile_name}' has expired; run: "
                f"{login_command(repo_root)}")
    return (f"no AWS identity for profile '{one_state.profile_name}' ({one_state.detail_text}); retry with "
            f"aws sts get-caller-identity --profile {one_state.profile_name}")


def docker_state(system_effects):
    try:
        docker_run = system_effects.run_captured(["docker", "version", "--format", "{{.Server.Version}}"],
                                                 timeout_seconds=DOCKER_TIMEOUT_SECONDS)
    except (OSError, subprocess.TimeoutExpired) as docker_error:
        return False, str(docker_error)
    if docker_run.returncode == 0:
        return True, docker_run.stdout.strip()
    return False, ((docker_run.stderr or "").strip().splitlines() or ["?"])[-1]


DOCKER_FIX = "start Docker (systemctl start docker)"


@dataclasses.dataclass(frozen=True)
class OpenTunnel:
    process_id: int
    local_port: str
    remote_host: str
    region_name: str


def open_tunnels(process_rows):
    found_tunnels = {}
    for process_id, argv_words in process_rows:
        if os.path.basename(argv_words[0]) != TUNNEL_PLUGIN or len(argv_words) < 6:
            continue
        try:
            start_request = json.loads(argv_words[5])
        except json.JSONDecodeError:
            continue
        if start_request.get("DocumentName") != TUNNEL_DOCUMENT:
            continue
        forward_params = start_request.get("Parameters") or {}
        local_port = (forward_params.get("localPortNumber") or ["?"])[0]
        remote_host = (forward_params.get("host") or ["?"])[0]
        tunnel_key = (local_port, remote_host)
        if tunnel_key not in found_tunnels or process_id < found_tunnels[tunnel_key].process_id:
            found_tunnels[tunnel_key] = OpenTunnel(process_id, local_port, remote_host, argv_words[2])
    return sorted(found_tunnels.values(), key=lambda one_tunnel: (one_tunnel.local_port, one_tunnel.remote_host))


def doctor_lines(repo_paths, system_effects):
    out_lines = [f"root     {repo_paths.repo_root}", f"logs     {repo_paths.log_dir}"]
    problem_count = 0
    for env_name, profile_name in ENV_PROFILES.items():
        one_state = session_state(system_effects, profile_name)
        if one_state.state_name == "valid":
            out_lines.append(f"sso      ok {env_name} (profile {profile_name}): {one_state.detail_text}")
        else:
            problem_count += 1
            out_lines.append(f"sso      {one_state.state_name.upper()} {env_name} (profile {profile_name}): "
                             f"{session_failure(repo_paths.repo_root, one_state)}")
    for tool_name, tool_use in ((TUNNEL_PLUGIN, "ops tunnel and ops sql need it; install the AWS "
                                 "Session Manager plugin"), ("jq", "the access scripts parse JSON with it; "
                                 "install jq")):
        tool_path = system_effects.tool_path(tool_name)
        if tool_path:
            out_lines.append(f"tool     ok {tool_name} {tool_path}")
        else:
            problem_count += 1
            out_lines.append(f"tool     MISSING {tool_name}; {tool_use}")
    docker_ok, docker_text = docker_state(system_effects)
    client_image = shell_constant(repo_paths.repo_root, "MYSQL_CLIENT_IMAGE")
    if docker_ok:
        out_lines.append(f"docker   ok server {docker_text}")
        image_run = system_effects.run_captured(["docker", "image", "inspect", "--format", "{{.Id}}",
                                                 client_image], timeout_seconds=DOCKER_TIMEOUT_SECONDS)
        image_state = ("ok pulled" if image_run.returncode == 0
                       else "not pulled yet; the first ops sql pulls it")
        out_lines.append(f"image    {image_state} {client_image}")
    else:
        problem_count += 1
        out_lines.append(f"docker   UNREACHABLE ({docker_text}); ops sql runs its client in Docker; {DOCKER_FIX}")
        out_lines.append(f"image    unknown {client_image} (Docker is down)")
    tunnel_rows = open_tunnels(system_effects.process_rows())
    for one_tunnel in tunnel_rows:
        out_lines.append(f"tunnel   open 127.0.0.1:{one_tunnel.local_port} -> {one_tunnel.remote_host} "
                         f"({one_tunnel.region_name}, pid {one_tunnel.process_id}); close: "
                         f"{CLI_PATH} tunnel stop")
    if not tunnel_rows:
        out_lines.append("tunnel   none open")
    return out_lines, problem_count


def child_environment(profile_name):
    return {**os.environ, "AWS_PROFILE": profile_name}


def write_header(log_file, command_argv, profile_name, work_dir):
    log_file.write(f"$ {shlex.join(command_argv)}\n")
    log_file.write(f"# ops: cwd {work_dir}\n")
    log_file.write(f"# ops: env AWS_PROFILE={profile_name}\n")
    log_file.write(f"# ops: started {datetime.datetime.now().isoformat(timespec='seconds')}\n\n")
    log_file.flush()


def require_session(repo_paths, profile_name, system_effects):
    one_state = session_state(system_effects, profile_name)
    if one_state.state_name != "valid":
        raise OpsError(session_failure(repo_paths.repo_root, one_state))
    return one_state


def require_docker(system_effects):
    docker_ok, docker_text = docker_state(system_effects)
    if not docker_ok:
        raise OpsError(f"Docker is unreachable ({docker_text}); ops sql runs its mysql client in Docker; "
                       f"{DOCKER_FIX}")


def run_doctor(repo_paths, system_effects):
    out_lines, problem_count = doctor_lines(repo_paths, system_effects)
    print("\n".join(out_lines))
    if problem_count:
        print(f"{problem_count} problem(s); each line above names its fix")
    return 1 if problem_count else 0
