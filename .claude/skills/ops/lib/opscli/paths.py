import dataclasses
import os
import re
import subprocess
from pathlib import Path

CLI_PATH = ".claude/skills/ops/bin/ops"
CHECKS_CLI_PATH = ".claude/skills/checks/bin/checks"
CONSTANTS_PATH = "scripts/ops/lib/constants.sh"
TERRAFORM_ROOT = "terraform"
TERRAFORM_RUNNER = "scripts/ops/terraform-run.sh"
METRICS_SCRIPT = "scripts/ops/access/metrics-query.sh"
LOGS_SCRIPT = "scripts/ops/access/logs-query.sh"
SQL_SCRIPT = "scripts/ops/access/rds-query.sh"
TUNNEL_SCRIPT = "scripts/ops/access/rds-tunnel.sh"
DB_VERIFY_SCRIPT = "scripts/ops/access/db-observability-check.sh"
FLEET_VERIFY_SCRIPT = "scripts/ops/fleet-verify.sh"
NODES_SCRIPT = "scripts/ops/access/fleet-nodes.sh"
BOOTLOG_SCRIPT = "scripts/ops/access/boot-log.sh"
APP_SCRIPT = "scripts/ops/access/argocd-app.sh"
CP_OVERRIDE_ENV = "CP_INSTANCE_ID"
STS_ARGV = ("aws", "sts", "get-caller-identity", "--output", "json")

ENV_PROFILES = {"prod": "prod", "staging": "staging", "mgmt": "mgmt"}
FIXED_BACKEND_ENVS = {"backend.management.hcl": "mgmt"}
MODULES_DIR = "modules"
SETTINGS_DIR = "envs"
ACCOUNT_VARIABLE = "aws_account_id"
ACCOUNT_VARIABLE_PATTERN = re.compile(rf'^\s*variable\s+"{ACCOUNT_VARIABLE}"', re.MULTILINE)
ACCOUNT_VARS_EXAMPLE = "account.tfvars.example"
DEFAULT_ENV = "prod"
SITE_NAMES = ("oregon", "seoul")
DEFAULT_SITE = "oregon"
TUNNEL_MODES = ("start", "stop", "status")
EXPIRED_MARKERS = ("Token has expired", "SSO session")
SINCE_PATTERN = re.compile(r"^\d+[mhd]$")
STEP_PATTERN = re.compile(r"^\d+[smhd]$")
INSTANCE_PATTERN = re.compile(r"^i-[0-9a-f]+\Z")
DEFAULT_BOOTLOG_LINES = 60
DAY_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TUNNEL_PLUGIN = "session-manager-plugin"
TUNNEL_DOCUMENT = "AWS-StartPortForwardingSessionToRemoteHost"
AWS_TIMEOUT_SECONDS = 30
DOCKER_TIMEOUT_SECONDS = 10
ERROR_TAIL_LINES = 20
GIT_ENV_NAMES = ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_COMMON_DIR")
ANSI_PATTERN = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")
AWS_CONFIG_ENV = "AWS_CONFIG_FILE"
DEFAULT_AWS_CONFIG = "~/.aws/config"
CONFIG_SECTION_PATTERN = re.compile(r"^\s*\[(?:profile\s+)?([^\]]+?)\s*\]")
ACCOUNT_ID_LINE = re.compile(r"^\s*sso_account_id\s*=\s*(\d+)\s*$")
ARN_ACCOUNT_PATTERN = re.compile(r"(arn:aws[a-z-]*:[^:\s]*:[^:\s]*:)\d{12}(?=:)")
UNMAPPED_ACCOUNT = "<account>"
ECR_HOST_PATTERN = re.compile(r"\b\d{12}(?=\.dkr\.ecr\.)")
GUARDED_KINDS = ("destroy", "replace", "forget")
GUARDED_UPDATE_TYPES = ("aws_instance", "aws_autoscaling_group", "aws_db_instance")
WARN_CHECK_STATUSES = ("fail", "error")
VERDICT_SUFFIX = ".verdict.json"
VERDICT_CLEAR = "clear"
VERDICT_BLOCKED = "blocked"
BLOCKED_EXIT = 3
BLOCKED_LINE = ("blocked: name each change you mean with --expect <address>, before the -- ; "
                "no apply command is printed")
PYTHON_PATTERN = re.compile(r"^python3?(\.\d+)?$")
OPS_CLI_NAME = Path(CLI_PATH).name
USER_ONLY_SUBCOMMANDS = ("apply", "destroy")
NET_NAMES = ("skills/ops/bin/ops", Path(TERRAFORM_RUNNER).name)
NET_WORD_PATTERN = re.compile(rf"(?<![A-Za-z0-9_-])({'|'.join(USER_ONLY_SUBCOMMANDS)})(?![A-Za-z0-9_-])")
VAR_FILE_FLAGS = {"-var-file", "--var-file"}


class OpsError(Exception):
    pass


def git_environment():
    return {env_key: env_value for env_key, env_value in os.environ.items() if env_key not in GIT_ENV_NAMES}


def find_repo_paths(checkout_dir):
    top_run = subprocess.run(["git", "-C", str(checkout_dir), "rev-parse", "--show-toplevel"],
                             capture_output=True, text=True, env=git_environment())
    if top_run.returncode != 0:
        raise OpsError(f"{checkout_dir} is not inside a git checkout; run the ops CLI of a checkout")
    common_run = subprocess.run(["git", "-C", str(checkout_dir), "rev-parse", "--path-format=absolute",
                                 "--git-common-dir"], capture_output=True, text=True, env=git_environment())
    repo_root = Path(top_run.stdout.strip())
    main_root = Path(common_run.stdout.strip()).parent if common_run.returncode == 0 else repo_root
    return RepoPaths(repo_root, main_root)


def log_stem(log_dir, name_parts, run_moment, file_suffixes=(".log",)):
    stem_text = "-".join(["ops", *[slug_text(one_part) for one_part in name_parts if one_part],
                          run_moment.strftime("%Y%m%d-%H%M%S")])
    candidate_stem = log_dir / stem_text
    collision_count = 1
    while any(candidate_stem.with_name(candidate_stem.name + one_suffix).exists() for one_suffix in file_suffixes):
        collision_count += 1
        candidate_stem = log_dir / f"{stem_text}-{collision_count}"
    return candidate_stem


def own_checkout_dir():
    return Path(__file__).resolve().parents[5]


def slug_text(label_text):
    return re.sub(r"[^a-z0-9]+", "-", label_text.lower()).strip("-")


@dataclasses.dataclass(frozen=True)
class RepoPaths:
    repo_root: Path
    main_root: Path

    @property
    def log_dir(self):
        if (self.repo_root / ".claude" / "reports").is_dir():
            return self.repo_root / ".claude" / "reports" / "logs"
        return self.main_root / ".claude" / "reports" / "logs"
