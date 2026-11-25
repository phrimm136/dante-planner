import dataclasses
import shlex
import sys
from pathlib import Path

from opscli.paths import (
    ACCOUNT_VARIABLE, ACCOUNT_VARS_EXAMPLE, APP_SCRIPT, BLOCKED_EXIT, BOOTLOG_SCRIPT, DAY_PATTERN, DB_VERIFY_SCRIPT,
    DEFAULT_BOOTLOG_LINES, DEFAULT_ENV, DEFAULT_SITE, INSTANCE_PATTERN, NODES_SCRIPT,
    ENV_PROFILES, FLEET_VERIFY_SCRIPT, GUARDED_UPDATE_TYPES, LOGS_SCRIPT, METRICS_SCRIPT, NET_NAMES,
    OpsError, SETTINGS_DIR, SINCE_PATTERN, SITE_NAMES, SQL_SCRIPT, STEP_PATTERN, STS_ARGV, TERRAFORM_RUNNER,
    TUNNEL_MODES, TUNNEL_SCRIPT, VERDICT_SUFFIX, WARN_CHECK_STATUSES, log_stem)
from opscli.redaction import KEY_BLOCK_MAX_LINES, SecretRedactor, redact_text, redacted_log
from opscli.session import child_environment, require_docker, require_session, write_header

FIRST_LINE_DROPPED = "<first line dropped: the console buffer may start inside a secret>\n"
USAGE_TEXT = f"""usage: ops [--env prod|staging|mgmt] <command> [args]
       ops list | doctor | help
       ops hook < tool-json

Runs the repeated workstation reads of production, terraform plans and the apply of a plan it
cleared. Only tf apply changes cloud state, and you run it as the printed `!` line; the hook
refuses it from the Bash tool in the forms it can see (hook below). Each command runs one script
under scripts/ops/ with AWS_PROFILE set from --env (prod, staging or mgmt, the profile names
in ~/.aws/config; default prod), after checking that profile's SSO session.

output:     the script's output goes to stdout and to a dated log; the exit code is the
            script's. Errors of ops itself exit 2 and name the next action.
logs:       <root>/.claude/reports/logs/ops-<command>-<YYYYMMDD-HHMMSS>.log when
            <root>/.claude/reports exists, otherwise the main checkout's .claude/reports/logs.
redaction:  every line printed or logged hides account ids, join and IMDS tokens, TOKEN= values,
            passwords, private keys and SSH key blocks. A key block that never closes is hidden for at most
            {KEY_BLOCK_MAX_LINES} lines after its BEGIN line and announced; bootlog drops its first line and says so.
nodes:      one line per app, data, ingress and cp instance and node: joined, joining (running under
            10 minutes, no node), NOT JOINED (running 10 minutes or more, no node) or gone (a node with
            no instance). Exit 0 all joined, 4 one joining, 1 one NOT JOINED or gone, 2 not readable. A
            NOT JOINED surge instance of the app ASG gets a `!` command for you to run; ops never runs it.
repo root:  the checkout this CLI file lives in, whatever the current directory.
tf plan:    writes the plan file beside its log (ops-tf-<stack>-<ts>.plan), keeps the full plan
            text in the log, and prints one line per changed resource (- destroy, -/+ replace,
            ~ update, + create, <= import, -> move, forget: dropped from the state and left in
            the cloud), terraform's counts line with the forgets added, and the `!` apply
            command for you to run. Words after -- reach terraform plan unchanged; -out and
            -detailed-exitcode are ops's own. A stack is any directory under terraform/ with
            *.tf files except modules; a stack whose backend file names a fixed account takes
            that account's profile (backend.management.hcl: mgmt), and a stack whose fixed
            account has no ops profile is planned directly through terraform-run.sh. A stack
            applied once per account ({ACCOUNT_VARS_EXAMPLE}, no fixed backend file) takes no
            default: name --env, and the refusal lists the envs whose backend file is here.
inputs:     a stack with an {SETTINGS_DIR}/ directory is planned with -var-file={SETTINGS_DIR}/<env>.tfvars for
            the env the plan runs under and, when it declares variable "{ACCOUNT_VARIABLE}", with
            -var {ACCOUNT_VARIABLE}=<that profile's sso_account_id>, both after -out and before
            the words after --. Such a stack refuses -var-file after --, and -var for a variable
            the settings file assigns or for {ACCOUNT_VARIABLE}; a -var for any other variable (a
            per-run value such as apply_immediately) passes. A missing settings file or account
            id stops the plan before terraform runs. Any other stack takes no automatic arguments.
            The hook's suggestion for a direct runner plan of such a stack drops its -var-file.
guard:     every replace, destroy or forget, and every update of an
            {', '.join(GUARDED_UPDATE_TYPES[:-1])} or {GUARDED_UPDATE_TYPES[-1]}, must be named with
            --expect <address> (repeatable, before the --), and every expected address must be
            such a change. Otherwise the plan is blocked: UNEXPECTED and ABSENT lines name the
            difference, no apply command is printed and tf plan exits {BLOCKED_EXIT}. A check block whose
            status is {' or '.join(WARN_CHECK_STATUSES)} prints a WARN line per problem message (or its
            address and status when it has none) and changes nothing else. The plan file gets a
            <plan file>{VERDICT_SUFFIX} beside it with the stack, the profile, the verdict, both
            address lists and the plan file's sha256; addresses compare as the summary prints them,
            account ids redacted.
tf apply:   takes one plan file and nothing else; the stack and the profile come from its
            verdict file. It refuses a file without a verdict file, a blocked verdict and a plan
            file changed since it was judged, then runs terraform-run.sh apply on it with the
            same session check, output, redaction and log as every other command.
hook:       exit 2 refuses a direct call to a script ops wraps, every ops tf apply and every
            terraform-run.sh apply or destroy the shell parser can read, and any command whose raw
            text names {' or '.join(NET_NAMES)} together with the
            standalone word apply or destroy, parsed or not. It guards ordinary commands; it
            is not a sandbox: a process that shares your cloud session can still call AWS itself.
            Known cost: a search or a message naming the CLI path and the word is refused too;
            put the pattern in a file (rg -f <file>) or the message in one (git commit -F <file>).

`ops list` prints every command with its purpose; `ops doctor` reports the SSO session of
each profile, session-manager-plugin, Docker and the pinned mysql client image, jq, and the
open RDS tunnels.
"""


@dataclasses.dataclass(frozen=True)
class CommandSpec:
    command_name: str
    arg_hint: str
    purpose_text: str
    script_path: str


DISPATCH_TABLE = (
    CommandSpec("metrics", "'<promql>' [--since D] [--step S] [--names]",
                "Mimir range query (metrics-query.sh); since default 1h, step 5m; "
                "--names prints the metric name on each line", METRICS_SCRIPT),
    CommandSpec("logs", "'<logql>' [--since D] [--limit N] | --day YYYY-MM-DD '<logql>' [--limit N]",
                "Loki range query (logs-query.sh); since default 24h, limit 500", LOGS_SCRIPT),
    CommandSpec("sql", "\"<SELECT ...>\" [--site oregon|seoul] | -f FILE [--site ...]",
                "read-only SQL as the SELECT-only user (rds-query.sh); starts the tunnel when down",
                SQL_SCRIPT),
    CommandSpec("tunnel", "start|stop|status [--site oregon|seoul]",
                "the SSM port-forward to RDS on 127.0.0.1 (rds-tunnel.sh); a local port only",
                TUNNEL_SCRIPT),
    CommandSpec("verify db", "", "DB metric families, mysql_up and the DB alert rules (db-observability-check.sh)",
                DB_VERIFY_SCRIPT),
    CommandSpec("verify fleet", "[--site oregon|seoul]",
                "a site's nodes, pods, Argo CD apps, RDS reachability and etcd snapshots (fleet-verify.sh); "
                "site default oregon", FLEET_VERIFY_SCRIPT),
    CommandSpec("nodes", "[--site oregon|seoul]",
                "each app, data, ingress and cp instance joined with its cluster node (fleet-nodes.sh); exit 4 while "
                "one is joining, 1 when one has not joined or a node has no instance", NODES_SCRIPT),
    CommandSpec("bootlog", "<instance-id> [--site oregon|seoul] [--lines N]",
                "the redacted tail of an instance's EC2 console output (boot-log.sh); lines default "
                f"{DEFAULT_BOOTLOG_LINES}", BOOTLOG_SCRIPT),
    CommandSpec("app", "[--site oregon|seoul]",
                "a site's root Argo CD Application: source, sync, health, conditions and what is not Synced "
                "(argocd-app.sh)", APP_SCRIPT),
    CommandSpec("tf plan", "<stack> [--expect <address>]... [-- terraform plan args]",
                "terraform plan through terraform-run.sh; prints the summary, guards replace, destroy, forget "
                "and instance updates, and prints the apply command for you when the guard passes; a stack with "
                f"{SETTINGS_DIR}/ takes {SETTINGS_DIR}/<env>.tfvars and the profile's account id itself",
                TERRAFORM_RUNNER),
    CommandSpec("tf apply", "<plan file>",
                "applies a plan file tf plan cleared, through terraform-run.sh; you run it with !, and the "
                "hook refuses every form of it that it can see in a Bash-tool command", TERRAFORM_RUNNER),
    CommandSpec("whoami", "", "the account and role the selected profile resolves to (aws sts get-caller-identity)",
                ""),
    CommandSpec("doctor", "", "SSO session per profile, session-manager-plugin, Docker and the mysql image, jq, "
                "open tunnels", ""),
    CommandSpec("list", "", "this list", ""),
)

HOOK_ROW = ("hook", "the PreToolUse entry: reads the Bash tool JSON on stdin, exit 2 refuses a direct "
            "call to a script ops wraps and any command naming the CLI or runner with apply or destroy; "
            "not a sandbox")


def spec_named(command_name):
    return next(one_spec for one_spec in DISPATCH_TABLE if one_spec.command_name == command_name)


def list_lines():
    command_rows = [(f"ops {one_spec.command_name} {one_spec.arg_hint}".rstrip(), one_spec.purpose_text)
                    for one_spec in DISPATCH_TABLE]
    command_rows.append((f"ops {HOOK_ROW[0]}", HOOK_ROW[1]))
    command_rows.append(("--env prod|staging|mgmt", "on any command: the AWS profile, default prod "
                         "(a fixed-account stack defaults to its own; a per-account stack has none)"))
    column_width = max(len(command_text) for command_text, _ in command_rows if len(command_text) <= 40)
    out_lines = []
    for command_text, purpose_text in command_rows:
        if len(command_text) > column_width:
            out_lines.append(command_text)
            out_lines.append(" " * (column_width + 2) + purpose_text)
        else:
            out_lines.append(command_text.ljust(column_width + 2) + purpose_text)
    return out_lines


@dataclasses.dataclass(frozen=True)
class OpsRequest:
    env_name: str
    wants_help: bool
    command_words: tuple


def parse_request(argv_words):
    env_name = ""
    wants_help = False
    command_words = []
    word_index = 0
    after_dashes = False
    while word_index < len(argv_words):
        one_word = argv_words[word_index]
        if after_dashes:
            command_words.append(one_word)
        elif one_word == "--":
            after_dashes = True
            command_words.append(one_word)
        elif one_word == "help" and not command_words:
            wants_help = True
        elif one_word in ("-h", "--help") and not command_words:
            raise OpsError(f"{one_word} is not an option; run ops help")
        elif one_word == "--env":
            if word_index + 1 >= len(argv_words):
                raise OpsError(f"--env needs one of {', '.join(ENV_PROFILES)}; see ops help")
            env_name = argv_words[word_index + 1]
            word_index += 1
        elif one_word.startswith("--env="):
            env_name = one_word.split("=", 1)[1]
        else:
            command_words.append(one_word)
        word_index += 1
    if env_name and env_name not in ENV_PROFILES:
        raise OpsError(f"--env {env_name} is not an environment; use one of {', '.join(ENV_PROFILES)}")
    return OpsRequest(env_name, wants_help, tuple(command_words))


@dataclasses.dataclass(frozen=True)
class ScriptInvocation:
    command_spec: CommandSpec
    script_args: tuple
    log_parts: tuple
    needs_session: bool
    needs_docker: bool = False
    site_stack: str = ""
    tail_lines: int = 0


class ArgReader:
    def __init__(self, prog_name, arg_words):
        self.prog_name = prog_name
        self.arg_words = list(arg_words)

    def raise_failure(self, message_text):
        raise OpsError(f"{self.prog_name}: {message_text}; see ops list")

    def split_options(self, option_names, flag_names=()):
        option_values = {}
        free_words = []
        word_index = 0
        while word_index < len(self.arg_words):
            one_word = self.arg_words[word_index]
            option_name = one_word.split("=", 1)[0]
            if one_word in flag_names:
                option_values[one_word] = one_word
            elif option_name in option_names:
                if "=" in one_word:
                    option_values[option_name] = one_word.split("=", 1)[1]
                elif word_index + 1 < len(self.arg_words):
                    option_values[option_name] = self.arg_words[word_index + 1]
                    word_index += 1
                else:
                    self.raise_failure(f"{option_name} needs a value")
            elif one_word.startswith("--") and len(one_word) > 2:
                self.raise_failure(f"unknown option {one_word}")
            else:
                free_words.append(one_word)
            word_index += 1
        return option_values, free_words

    def matched_value(self, option_values, option_name, value_pattern, shape_text):
        raw_value = option_values.get(option_name, "")
        if raw_value and not value_pattern.match(raw_value):
            self.raise_failure(f"{option_name} takes {shape_text}, not '{raw_value}'")
        return raw_value

    def site_of(self, option_values):
        site_name = option_values.get("--site", "")
        if site_name and site_name not in SITE_NAMES:
            self.raise_failure(f"--site takes {' or '.join(SITE_NAMES)}, not '{site_name}'")
        return site_name

    def one_free(self, free_words, shape_text):
        if len(free_words) != 1:
            self.raise_failure(f"takes exactly one {shape_text}, got {len(free_words)}")
        return free_words[0]


def positional_tail(head_words, optional_words):
    kept_words = list(optional_words)
    while kept_words and not kept_words[-1]:
        kept_words.pop()
    return (*head_words, *kept_words)


def count_value(arg_reader, option_values, option_name):
    raw_count = option_values.get(option_name, "")
    if raw_count and (not raw_count.isdecimal() or int(raw_count) < 1):
        arg_reader.raise_failure(f"{option_name} takes a positive whole number, not '{raw_count}'")
    return raw_count


def metrics_invocation(arg_words):
    arg_reader = ArgReader("ops metrics", arg_words)
    option_values, free_words = arg_reader.split_options({"--since", "--step"}, {"--names"})
    query_text = arg_reader.one_free(free_words, "'<promql>' query")
    since_text = arg_reader.matched_value(option_values, "--since", SINCE_PATTERN, "a window like 30m, 6h or 2d")
    step_text = arg_reader.matched_value(option_values, "--step", STEP_PATTERN, "a step like 30s, 1m or 5m")
    name_words = ("--names",) if "--names" in option_values else ()
    return ScriptInvocation(spec_named("metrics"), positional_tail((*name_words, query_text), (since_text, step_text)),
                            ("metrics",), True)


def logs_invocation(arg_words):
    arg_reader = ArgReader("ops logs", arg_words)
    option_values, free_words = arg_reader.split_options({"--since", "--limit", "--day"})
    query_text = arg_reader.one_free(free_words, "'<logql>' query")
    limit_text = count_value(arg_reader, option_values, "--limit")
    day_text = arg_reader.matched_value(option_values, "--day", DAY_PATTERN, "a date like 2026-07-23")
    if day_text:
        if "--since" in option_values:
            arg_reader.raise_failure("--day and --since are two ways to pick the window; pass one")
        script_args = positional_tail(("--day", day_text, query_text), (limit_text,))
    else:
        since_text = arg_reader.matched_value(option_values, "--since", SINCE_PATTERN, "a window like 30m, 6h or 2d")
        script_args = positional_tail((query_text,), (since_text, limit_text))
    return ScriptInvocation(spec_named("logs"), script_args, ("logs",), True)


def sql_invocation(arg_words, caller_dir):
    arg_reader = ArgReader("ops sql", arg_words)
    option_values, free_words = arg_reader.split_options({"--site"})
    site_name = arg_reader.site_of(option_values)
    if free_words[:1] == ["-f"]:
        file_words = free_words[1:]
        if len(file_words) != 1:
            arg_reader.raise_failure("-f takes exactly one SQL file")
        sql_file = (Path(caller_dir) / file_words[0]).resolve()
        if not sql_file.is_file():
            arg_reader.raise_failure(f"no SQL file at {sql_file}")
        head_words = ("-f", str(sql_file))
    else:
        head_words = (arg_reader.one_free(free_words, "\"<SELECT ...>\" statement"),)
    return ScriptInvocation(spec_named("sql"), positional_tail(head_words, (site_name,)), ("sql",), True, True)


def tunnel_invocation(arg_words):
    arg_reader = ArgReader("ops tunnel", arg_words)
    option_values, free_words = arg_reader.split_options({"--site"})
    mode_name = arg_reader.one_free(free_words, "mode (start, stop or status)")
    if mode_name not in TUNNEL_MODES:
        arg_reader.raise_failure(f"mode is one of {', '.join(TUNNEL_MODES)}, not '{mode_name}'")
    site_name = arg_reader.site_of(option_values)
    return ScriptInvocation(spec_named("tunnel"), positional_tail((mode_name,), (site_name,)),
                            ("tunnel", mode_name), mode_name == "start")


def site_only_args(arg_reader):
    option_values, free_words = arg_reader.split_options({"--site"})
    if free_words:
        arg_reader.raise_failure(f"takes only --site, not '{free_words[0]}'")
    return arg_reader.site_of(option_values) or DEFAULT_SITE


def site_invocation(command_name, log_parts, arg_words):
    site_name = site_only_args(ArgReader(f"ops {command_name}", arg_words))
    return ScriptInvocation(spec_named(command_name), (site_name,), (*log_parts, site_name), True,
                            site_stack=site_name)


def bootlog_invocation(arg_words):
    arg_reader = ArgReader("ops bootlog", arg_words)
    option_values, free_words = arg_reader.split_options({"--site", "--lines"})
    instance_id = arg_reader.one_free(free_words, "<instance-id>")
    if not INSTANCE_PATTERN.match(instance_id):
        arg_reader.raise_failure(f"<instance-id> is i- and hex digits, not '{instance_id}'")
    site_name = arg_reader.site_of(option_values) or DEFAULT_SITE
    line_count = int(count_value(arg_reader, option_values, "--lines") or DEFAULT_BOOTLOG_LINES)
    return ScriptInvocation(spec_named("bootlog"), (instance_id, site_name), ("bootlog", instance_id), True,
                            tail_lines=line_count)


def verify_invocation(arg_words):
    target_names = [one_spec.command_name.split()[1] for one_spec in DISPATCH_TABLE
                    if one_spec.command_name.startswith("verify ")]
    if not arg_words or arg_words[0] not in target_names:
        raise OpsError(f"ops verify takes one of {', '.join(target_names)}; see ops list")
    target_name = arg_words[0]
    arg_reader = ArgReader(f"ops verify {target_name}", arg_words[1:])
    if target_name != "fleet":
        if arg_words[1:]:
            arg_reader.raise_failure("takes no arguments")
        return ScriptInvocation(spec_named(f"verify {target_name}"), (), ("verify", target_name), True)
    return site_invocation("verify fleet", ("verify", "fleet"), arg_words[1:])


def build_invocation(command_words, caller_dir):
    command_name, arg_words = command_words[0], list(command_words[1:])
    if command_name == "metrics":
        return metrics_invocation(arg_words)
    if command_name == "logs":
        return logs_invocation(arg_words)
    if command_name == "sql":
        return sql_invocation(arg_words, caller_dir)
    if command_name == "tunnel":
        return tunnel_invocation(arg_words)
    if command_name == "verify":
        return verify_invocation(arg_words)
    if command_name in ("nodes", "app"):
        return site_invocation(command_name, (command_name,), arg_words)
    if command_name == "bootlog":
        return bootlog_invocation(arg_words)
    if command_name == "whoami":
        if arg_words:
            raise OpsError("ops whoami takes no arguments besides --env; see ops list")
        return ScriptInvocation(spec_named("whoami"), (), ("whoami",), True)
    raise OpsError(f"unknown command '{command_name}'; run ops list")


def redacted_console(console_text):
    if not console_text:
        return ""
    first_line, line_break, rest_text = console_text.partition("\n")
    secret_redactor = SecretRedactor()
    secret_redactor.redact_chunk(first_line + line_break)
    return FIRST_LINE_DROPPED + secret_redactor.redact_chunk(rest_text) + secret_redactor.end_notice()


def run_tailed(repo_paths, tail_lines, command_argv, profile_name, system_effects, log_file):
    child_env = child_environment(profile_name)
    if not tail_lines:
        return system_effects.run_streamed(command_argv, child_env, repo_paths.repo_root, log_file, True)
    split_run = system_effects.run_split(command_argv, child_env, repo_paths.repo_root)
    log_file.write(split_run.stderr)
    log_file.end_output()
    sys.stderr.write(split_run.stderr)
    tail_text = "".join(redacted_console(split_run.stdout).splitlines(keepends=True)[-tail_lines:])
    log_file.write(tail_text)
    sys.stdout.write(tail_text)
    return split_run.returncode


def run_script(repo_paths, script_invocation, profile_name, system_effects, run_moment):
    script_file = repo_paths.repo_root / script_invocation.command_spec.script_path
    command_argv = (str(script_file), *script_invocation.script_args)
    repo_paths.log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_stem(repo_paths.log_dir, script_invocation.log_parts, run_moment).with_suffix(".log")
    with redacted_log(log_path, "w") as log_file:
        write_header(log_file, command_argv, profile_name, repo_paths.repo_root)
        exit_code = run_tailed(repo_paths, script_invocation.tail_lines, command_argv, profile_name, system_effects,
                               log_file)
        log_file.end_output()
        log_file.write(f"\n# ops: exit {exit_code}\n")
    print(f"ops: exit {exit_code}, log {log_path}", file=sys.stderr)
    return exit_code


def run_whoami(repo_paths, profile_name, system_effects, run_moment):
    one_state = require_session(repo_paths, profile_name, system_effects)
    repo_paths.log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_stem(repo_paths.log_dir, ("whoami",), run_moment).with_suffix(".log")
    identity_line = f"profile {profile_name}: {one_state.detail_text}"
    log_path.write_text(redact_text(f"$ {shlex.join([*STS_ARGV, '--profile', profile_name])}\n{identity_line}\n"),
                        encoding="utf-8")
    print(identity_line)
    print(f"ops: exit 0, log {log_path}", file=sys.stderr)
    return 0


def run_invocation(repo_paths, script_invocation, env_name, system_effects, run_moment):
    profile_name = ENV_PROFILES[env_name or DEFAULT_ENV]
    if script_invocation.command_spec.command_name == "whoami":
        return run_whoami(repo_paths, profile_name, system_effects, run_moment)
    if script_invocation.needs_session:
        require_session(repo_paths, profile_name, system_effects)
    if script_invocation.needs_docker:
        require_docker(system_effects)
    return run_script(repo_paths, script_invocation, profile_name, system_effects, run_moment)
