import dataclasses
import hashlib
import json
import os
import re
import shlex
import sys
from pathlib import Path

from opscli.paths import (
    ACCOUNT_VARIABLE, ACCOUNT_VARIABLE_PATTERN, ACCOUNT_VARS_EXAMPLE, ANSI_PATTERN, BLOCKED_EXIT,
    BLOCKED_LINE, CLI_PATH, CP_OVERRIDE_ENV, DEFAULT_ENV, ENV_PROFILES, ERROR_TAIL_LINES,
    FIXED_BACKEND_ENVS, GUARDED_KINDS, GUARDED_UPDATE_TYPES, MODULES_DIR, OpsError, SETTINGS_DIR, TERRAFORM_ROOT, TERRAFORM_RUNNER,
    VAR_FILE_FLAGS, VERDICT_BLOCKED, VERDICT_CLEAR, VERDICT_SUFFIX, WARN_CHECK_STATUSES, log_stem)
from opscli.redaction import aws_config_path, profile_account, redact_accounts, redacted_log
from opscli.session import child_environment, require_session, write_header
from opscli.commands import ScriptInvocation, run_script, spec_named


def plan_stacks(repo_root):
    terraform_dir = repo_root / TERRAFORM_ROOT
    if not terraform_dir.is_dir():
        return []
    return [stack_dir.name for stack_dir in sorted(terraform_dir.iterdir())
            if stack_dir.is_dir() and stack_dir.name != MODULES_DIR and any(stack_dir.glob("*.tf"))]


@dataclasses.dataclass(frozen=True)
class StackInfo:
    stack_name: str
    fixed_backend: str
    per_account: bool
    is_moved: bool
    takes_account: bool

    @property
    def is_unmapped(self):
        return bool(self.fixed_backend) and self.fixed_backend not in FIXED_BACKEND_ENVS

    def default_env(self):
        return FIXED_BACKEND_ENVS.get(self.fixed_backend, "" if self.per_account else DEFAULT_ENV)


def stack_info(repo_root, stack_name):
    stack_dir = repo_root / TERRAFORM_ROOT / stack_name
    fixed_backend = backend_file_name(repo_root, stack_name)
    per_account = not fixed_backend and (stack_dir / ACCOUNT_VARS_EXAMPLE).is_file()
    tf_texts = [tf_file.read_text(encoding="utf-8") for tf_file in stack_dir.glob("*.tf")]
    return StackInfo(stack_name, fixed_backend, per_account, (stack_dir / SETTINGS_DIR).is_dir(),
                     any(ACCOUNT_VARIABLE_PATTERN.search(tf_text) for tf_text in tf_texts))


def env_backend_name(env_name):
    return next((file_name for file_name, fixed_env in FIXED_BACKEND_ENVS.items() if fixed_env == env_name),
                f"backend.{env_name}.hcl")


def runner_text(repo_root, stack_name, terraform_words):
    return (f"{repo_root / TERRAFORM_RUNNER} -chdir={repo_root / TERRAFORM_ROOT / stack_name} "
            f"{' '.join(terraform_words)}")


def plan_env(repo_root, one_stack, requested_env):
    if one_stack.is_unmapped:
        raise OpsError(f"{TERRAFORM_ROOT}/{one_stack.stack_name} keeps its state in the account behind "
                       f"{one_stack.fixed_backend}, which has no ops profile; plan it directly with that "
                       f"account's profile: AWS_PROFILE=<profile for that account> "
                       f"{runner_text(repo_root, one_stack.stack_name, ['plan'])}")
    if one_stack.fixed_backend and requested_env and requested_env != one_stack.default_env():
        raise OpsError(f"{TERRAFORM_ROOT}/{one_stack.stack_name} keeps its state in the account behind "
                       f"{one_stack.fixed_backend}; plan it with --env {one_stack.default_env()} or without --env")
    if one_stack.per_account and not requested_env:
        present_envs = [f"--env {one_env} ({env_backend_name(one_env)})" for one_env in ENV_PROFILES
                        if (repo_root / TERRAFORM_ROOT / env_backend_name(one_env)).is_file()]
        choice_text = ", ".join(present_envs) or ("no env has its backend file in this checkout; copy them "
                                                  "from the main checkout")
        raise OpsError(f"{TERRAFORM_ROOT}/{one_stack.stack_name} is applied once per account, so it takes no "
                       f"default profile; name the account: {choice_text}")
    return requested_env or one_stack.default_env()


@dataclasses.dataclass(frozen=True)
class PlanRequest:
    stack_name: str
    extra_args: tuple
    expected_addresses: tuple = ()
    input_args: tuple = ()


@dataclasses.dataclass(frozen=True)
class ApplyRequest:
    plan_path: Path


OUT_FLAGS = {"-out", "--out"}
EXITCODE_FLAGS = {"-detailed-exitcode", "--detailed-exitcode"}


EXPECT_OPTION = "--expect"


def expectation_split(plan_words):
    expected_addresses = []
    free_words = []
    word_index = 0
    while word_index < len(plan_words):
        one_word = plan_words[word_index]
        if one_word == EXPECT_OPTION or one_word.startswith(f"{EXPECT_OPTION}="):
            if "=" in one_word:
                one_address = one_word.split("=", 1)[1]
            elif word_index + 1 < len(plan_words):
                one_address = plan_words[word_index + 1]
                word_index += 1
            else:
                one_address = ""
            if not one_address:
                raise OpsError(f"{EXPECT_OPTION} needs the address of a change you mean, as the plan summary "
                               f"prints it: {CLI_PATH} tf plan <stack> {EXPECT_OPTION} <address>")
            if one_address in expected_addresses:
                raise OpsError(f"{EXPECT_OPTION} {one_address} is given twice; name each address once")
            expected_addresses.append(one_address)
        else:
            free_words.append(one_word)
        word_index += 1
    return free_words, tuple(expected_addresses)


def apply_request(arg_words, caller_dir):
    if len(arg_words) != 1 or arg_words[0].startswith("-"):
        raise OpsError(f"ops tf apply takes one plan file and nothing else: the `!` line "
                       f"{CLI_PATH} tf plan <stack> prints; terraform's own args belong to the plan")
    return ApplyRequest(Path(caller_dir, arg_words[0]).resolve())


def tf_request(repo_root, arg_words, caller_dir):
    if not arg_words:
        raise OpsError("ops tf takes plan or apply; see ops list")
    if arg_words[0] == "apply":
        return apply_request(arg_words[1:], caller_dir)
    if arg_words[0] == "destroy":
        raise OpsError(f"ops tf has no destroy: plan it with {CLI_PATH} tf plan <stack> {EXPECT_OPTION} "
                       f"<address> -- -destroy, naming each address, then run the printed `!` apply line")
    if arg_words[0] != "plan":
        raise OpsError(f"ops tf has only plan and apply, not '{arg_words[0]}'; init, workspace and state run "
                       f"through {TERRAFORM_RUNNER} yourself")
    plan_words = list(arg_words[1:])
    extra_args = ()
    if "--" in plan_words:
        split_index = plan_words.index("--")
        plan_words, extra_args = plan_words[:split_index], tuple(plan_words[split_index + 1:])
    plan_words, expected_addresses = expectation_split(plan_words)
    stack_names = plan_stacks(repo_root)
    if len(plan_words) != 1:
        raise OpsError(f"ops tf plan takes one stack, then -- and terraform's own args; stacks: "
                       f"{', '.join(stack_names)}")
    if plan_words[0] not in stack_names:
        raise OpsError(f"unknown stack '{plan_words[0]}'; the stacks under {TERRAFORM_ROOT}/: "
                       f"{', '.join(stack_names)}")
    if any(one_arg.split("=", 1)[0] in OUT_FLAGS for one_arg in extra_args):
        raise OpsError("ops tf plan writes the plan file itself, beside its log; drop -out")
    if any(one_arg.split("=", 1)[0] in EXITCODE_FLAGS for one_arg in extra_args):
        raise OpsError(f"ops tf plan owns the exit code (0 planned, {BLOCKED_EXIT} blocked, other non-zero "
                       f"failed) and prints the change summary itself; drop -detailed-exitcode")
    return PlanRequest(plan_words[0], extra_args, expected_addresses)


HEREDOC_START = re.compile(r"<<-?([A-Za-z_][A-Za-z0-9_]*)\s*$")
ASSIGNED_NAME = re.compile(r"\s*([A-Za-z_][A-Za-z0-9_-]*)\s*=(?!=)")


def assigned_names(settings_text):
    found_names = set()
    nest_depth = 0
    heredoc_marker = ""
    in_block = False
    for text_line in settings_text.splitlines():
        if heredoc_marker:
            if text_line.strip() == heredoc_marker:
                heredoc_marker = ""
            continue
        if nest_depth == 0 and not in_block:
            name_match = ASSIGNED_NAME.match(text_line)
            if name_match:
                found_names.add(name_match.group(1))
        char_index = 0
        in_string = False
        while char_index < len(text_line):
            line_char = text_line[char_index]
            char_pair = text_line[char_index:char_index + 2]
            if in_block:
                if char_pair == "*/":
                    in_block = False
                    char_index += 1
            elif in_string:
                if line_char == "\\":
                    char_index += 1
                elif line_char == '"':
                    in_string = False
            elif line_char == '"':
                in_string = True
            elif line_char == "#" or char_pair == "//":
                break
            elif char_pair == "/*":
                in_block = True
                char_index += 1
            elif char_pair == "<<":
                heredoc_match = HEREDOC_START.match(text_line[char_index:])
                if heredoc_match:
                    heredoc_marker = heredoc_match.group(1)
                    break
            elif line_char in "{[(":
                nest_depth += 1
            elif line_char in "}])":
                nest_depth = max(nest_depth - 1, 0)
            char_index += 1
    return found_names


def var_names(extra_args):
    named_vars = []
    for arg_index, one_arg in enumerate(extra_args):
        if one_arg in ("-var", "--var"):
            var_value = extra_args[arg_index + 1] if arg_index + 1 < len(extra_args) else ""
        elif one_arg.startswith(("-var=", "--var=")):
            var_value = one_arg.split("=", 1)[1]
        else:
            continue
        if "=" in var_value:
            named_vars.append(var_value.split("=", 1)[0].strip())
    return named_vars


def stack_inputs(repo_root, one_stack, env_name, extra_args):
    if not one_stack.is_moved:
        return ()
    stack_text = f"{TERRAFORM_ROOT}/{one_stack.stack_name}"
    settings_name = f"{SETTINGS_DIR}/{env_name}.tfvars"
    if any(one_arg.split("=", 1)[0] in VAR_FILE_FLAGS for one_arg in extra_args):
        raise OpsError(f"{stack_text} takes its settings from {settings_name} and the account of the selected "
                       f"profile, never from -var-file; edit {stack_text}/{settings_name} instead")
    settings_path = repo_root / stack_text / settings_name
    if not settings_path.is_file():
        raise OpsError(f"{stack_text}/{settings_name} is missing; {stack_text} has an {SETTINGS_DIR}/ directory, "
                       f"so a plan under --env {env_name} reads its settings from that file: create it")
    settled_names = assigned_names(settings_path.read_text(encoding="utf-8"))
    for var_name in var_names(extra_args):
        if one_stack.takes_account and var_name == ACCOUNT_VARIABLE:
            raise OpsError(f"-var {ACCOUNT_VARIABLE} is refused: ops passes the account of the selected profile "
                           f"itself; choose the account with --env")
        if var_name in settled_names:
            raise OpsError(f"-var {var_name} is refused: {stack_text}/{settings_name} sets it, and a second "
                           f"source would win silently; edit that file instead")
    input_args = [f"-var-file={settings_name}"]
    if one_stack.takes_account:
        profile_name = ENV_PROFILES[env_name]
        account_id = profile_account(profile_name)
        if not account_id:
            raise OpsError(f"profile '{profile_name}' has no sso_account_id in {aws_config_path()}, and "
                           f"{stack_text} takes {ACCOUNT_VARIABLE} from it; add the line to that profile")
        input_args += ["-var", f"{ACCOUNT_VARIABLE}={account_id}"]
    return tuple(input_args)


BACKEND_FILE_PATTERN = re.compile(r"-backend-config=(?:\.\./)?(backend\.[A-Za-z0-9_.-]+\.hcl)")


def backend_file_name(repo_root, stack_name):
    stack_dir = repo_root / TERRAFORM_ROOT / stack_name
    env_files = {f"backend.{one_env}.hcl" for one_env in ENV_PROFILES}
    fixed_names = {name_match.group(1) for doc_file in [*stack_dir.glob("*.tf"), stack_dir / "README.md"]
                   if doc_file.is_file()
                   for name_match in BACKEND_FILE_PATTERN.finditer(doc_file.read_text(encoding="utf-8"))
                   if name_match.group(1) not in env_files}
    return fixed_names.pop() if len(fixed_names) == 1 else ""


def require_init(repo_root, one_stack, env_name):
    stack_dir = repo_root / TERRAFORM_ROOT / one_stack.stack_name
    state_dir = stack_dir / ".terraform"
    if state_dir.is_dir():
        return
    backend_file = repo_root / TERRAFORM_ROOT / (one_stack.fixed_backend or env_backend_name(env_name))
    init_words = ["init", f"-backend-config={backend_file}"]
    missing_note = ""
    if not backend_file.is_file():
        missing_note = (f" ({backend_file.name} is not in this checkout: copy it from the main checkout, or "
                        f"write it from {TERRAFORM_ROOT}/backend.account.hcl.example)")
    raise OpsError(f"{TERRAFORM_ROOT}/{one_stack.stack_name} is not initialized in this checkout ({state_dir} is "
                   f"missing); run, then retry:\n  AWS_PROFILE={ENV_PROFILES[env_name]} "
                   f"{runner_text(repo_root, one_stack.stack_name, init_words)}{missing_note}")


def require_site_init(repo_root, script_invocation, env_name):
    if script_invocation.site_stack and not os.environ.get(CP_OVERRIDE_ENV):
        require_init(repo_root, stack_info(repo_root, script_invocation.site_stack), env_name or DEFAULT_ENV)


def plan_arguments(stack_name, plan_path, input_args, extra_args):
    return (f"-chdir={TERRAFORM_ROOT}/{stack_name}", "plan", f"-out={plan_path}", *input_args, *extra_args)


def show_arguments(stack_name, plan_path):
    return (f"-chdir={TERRAFORM_ROOT}/{stack_name}", "show", "-json", str(plan_path))


def apply_arguments(stack_name, plan_path):
    return (f"-chdir={TERRAFORM_ROOT}/{stack_name}", "apply", str(plan_path))


def apply_line(repo_root, plan_path):
    return f"! {repo_root / CLI_PATH} tf apply {plan_path}"


SUMMARY_ORDER = (("destroy", "- "), ("replace", "-/+ "), ("update", "~ "), ("create", "+ "), ("import", "<= "),
                 ("move", "-> "), ("forget", "forget "))


def change_kind(action_list):
    action_set = set(action_list)
    if action_set == {"delete", "create"}:
        return "replace"
    if action_list == ["delete"]:
        return "destroy"
    if action_list == ["update"]:
        return "update"
    if action_list == ["create"]:
        return "create"
    if action_list == ["forget"]:
        return "forget"
    return ""


@dataclasses.dataclass(frozen=True)
class PlanSummary:
    change_lines: tuple
    add_count: int
    change_count: int
    destroy_count: int
    output_count: int
    import_count: int
    forget_count: int = 0
    guarded_markers: dict = dataclasses.field(default_factory=dict)
    check_messages: tuple = ()

    @property
    def counts_line(self):
        import_text = f"{self.import_count} to import, " if self.import_count else ""
        forget_text = f", {self.forget_count} to forget" if self.forget_count else ""
        return (f"Plan: {import_text}{self.add_count} to add, {self.change_count} to change, "
                f"{self.destroy_count} to destroy{forget_text}.")


def is_guarded(kind_name, resource_type):
    return kind_name in GUARDED_KINDS or (kind_name == "update" and resource_type in GUARDED_UPDATE_TYPES)


def check_warnings(show_data):
    warning_texts = []
    for one_check in show_data.get("checks") or ():
        check_status = one_check.get("status", "")
        if check_status not in WARN_CHECK_STATUSES:
            continue
        problem_messages = [one_problem.get("message", "") for one_instance in one_check.get("instances") or ()
                            for one_problem in one_instance.get("problems") or ()]
        check_address = (one_check.get("address") or {}).get("to_display", "?")
        warning_texts += problem_messages or [f"{check_address} {check_status}"]
    return tuple(warning_texts)


def plan_summary(show_data):
    grouped_rows = {kind_name: [] for kind_name, _ in SUMMARY_ORDER}
    guarded_rows = {kind_name: [] for kind_name in grouped_rows}
    for resource_change in show_data.get("resource_changes") or ():
        change_data = resource_change.get("change", {})
        kind_name = change_kind(list(change_data.get("actions", ())))
        if kind_name:
            grouped_rows[kind_name].append(resource_change["address"])
        if is_guarded(kind_name, resource_change.get("type", "")):
            guarded_rows[kind_name].append(resource_change["address"])
        if change_data.get("importing"):
            grouped_rows["import"].append(resource_change["address"])
        previous_address = resource_change.get("previous_address", "")
        if previous_address and previous_address != resource_change["address"]:
            grouped_rows["move"].append(f"{previous_address} to {resource_change['address']}")
    change_lines = tuple(marker_text + resource_address for kind_name, marker_text in SUMMARY_ORDER
                         for resource_address in grouped_rows[kind_name])
    replace_count = len(grouped_rows["replace"])
    output_count = sum(1 for output_change in (show_data.get("output_changes") or {}).values()
                       if list(output_change.get("actions", ())) != ["no-op"])
    guarded_markers = {resource_address: marker_text for kind_name, marker_text in SUMMARY_ORDER
                       for resource_address in guarded_rows[kind_name]}
    return PlanSummary(change_lines,
                       len(grouped_rows["create"]) + replace_count,
                       len(grouped_rows["update"]),
                       len(grouped_rows["destroy"]) + replace_count,
                       output_count,
                       len(grouped_rows["import"]),
                       len(grouped_rows["forget"]),
                       guarded_markers,
                       check_warnings(show_data))


@dataclasses.dataclass(frozen=True)
class PlanVerdict:
    stack_name: str
    profile_name: str
    expected_addresses: tuple
    guarded_addresses: tuple
    plan_sha256: str

    @property
    def unexpected_addresses(self):
        return tuple(one_address for one_address in self.guarded_addresses
                     if one_address not in self.expected_addresses)

    @property
    def absent_addresses(self):
        return tuple(one_address for one_address in self.expected_addresses
                     if one_address not in self.guarded_addresses)

    @property
    def verdict_name(self):
        return VERDICT_BLOCKED if self.unexpected_addresses or self.absent_addresses else VERDICT_CLEAR

    def as_document(self):
        return {"stack": self.stack_name, "env": self.profile_name, "verdict": self.verdict_name,
                "expected": list(self.expected_addresses), "guarded": list(self.guarded_addresses),
                "plan_sha256": self.plan_sha256}


def file_sha256(file_path):
    return hashlib.sha256(file_path.read_bytes()).hexdigest()


def verdict_path_of(plan_path):
    return plan_path.with_name(plan_path.name + VERDICT_SUFFIX)


def plan_verdict(plan_info, plan_request, profile_name, plan_sha256):
    return PlanVerdict(plan_request.stack_name, profile_name,
                       tuple(sorted({redact_accounts(one_address) for one_address in plan_request.expected_addresses})),
                       tuple(sorted({redact_accounts(one_address) for one_address in plan_info.guarded_markers})),
                       redact_accounts(plan_sha256))


def write_verdict(plan_path, one_verdict):
    verdict_text = json.dumps(one_verdict.as_document(), indent=2) + "\n"
    verdict_path_of(plan_path).write_text(redact_accounts(verdict_text), encoding="utf-8")


def guard_lines(plan_info, one_verdict):
    return [*[f"UNEXPECTED {marker_text}{one_address}"
              for one_address, marker_text in plan_info.guarded_markers.items()
              if redact_accounts(one_address) in one_verdict.unexpected_addresses],
            *[f"ABSENT {one_address}" for one_address in one_verdict.absent_addresses]]


def summary_lines(plan_info, one_verdict, repo_root, plan_path):
    warn_lines = [f"WARN {message_text}" for message_text in plan_info.check_messages]
    if plan_info.change_lines:
        change_lines = [*plan_info.change_lines, plan_info.counts_line]
    elif plan_info.output_count:
        change_lines = [f"Outputs only: {plan_info.output_count} changed."]
    else:
        change_lines = ["No changes."]
    if one_verdict.verdict_name == VERDICT_BLOCKED:
        return [*warn_lines, *change_lines, *guard_lines(plan_info, one_verdict), BLOCKED_LINE]
    if plan_info.change_lines or plan_info.output_count:
        return [*warn_lines, *change_lines, apply_line(repo_root, plan_path)]
    return [*warn_lines, *change_lines]


def error_tail(log_text):
    clean_lines = [ANSI_PATTERN.sub("", text_line).rstrip() for text_line in log_text.splitlines()]
    body_lines = [text_line for text_line in clean_lines
                  if text_line.strip() and not text_line.startswith(("$ ", "# ops:"))]
    error_index = next((line_index for line_index, text_line in enumerate(body_lines) if "Error:" in text_line),
                       None)
    tail_lines = body_lines[error_index:] if error_index is not None else body_lines
    return tail_lines[-ERROR_TAIL_LINES:]


def run_plan(repo_paths, plan_request, profile_name, system_effects, run_moment):
    repo_paths.log_dir.mkdir(parents=True, exist_ok=True)
    stem_path = log_stem(repo_paths.log_dir, ("tf", plan_request.stack_name), run_moment, (".log", ".plan"))
    log_path = stem_path.with_name(stem_path.name + ".log")
    plan_path = stem_path.with_name(stem_path.name + ".plan")
    runner_path = str(repo_paths.repo_root / TERRAFORM_RUNNER)
    plan_argv = (runner_path, *plan_arguments(plan_request.stack_name, plan_path, plan_request.input_args,
                                              plan_request.extra_args))
    child_env = child_environment(profile_name)
    with redacted_log(log_path, "w") as log_file:
        write_header(log_file, plan_argv, profile_name, repo_paths.repo_root)
        plan_exit = system_effects.run_streamed(plan_argv, child_env, repo_paths.repo_root, log_file, False)
        log_file.end_output()
        log_file.write(f"\n# ops: plan exit {plan_exit}\n")
    if plan_exit != 0:
        print("\n".join(error_tail(log_path.read_text(encoding="utf-8", errors="replace"))), file=sys.stderr)
        print(f"ops: terraform plan failed (exit {plan_exit}); full output in {log_path}. A stack that needs "
              f"init or a workspace is prepared with {TERRAFORM_RUNNER} first", file=sys.stderr)
        return plan_exit
    show_argv = (runner_path, *show_arguments(plan_request.stack_name, plan_path))
    show_run = system_effects.run_captured(list(show_argv), child_env, repo_paths.repo_root)
    with redacted_log(log_path, "a") as log_file:
        log_file.write(f"$ {shlex.join(show_argv)}\n# ops: show exit {show_run.returncode}\n")
    if show_run.returncode != 0:
        print(show_run.stderr.strip(), file=sys.stderr)
        print(f"ops: terraform show -json failed (exit {show_run.returncode}); plan file {plan_path}, "
              f"log {log_path}", file=sys.stderr)
        return show_run.returncode
    plan_info = plan_summary(json.loads(show_run.stdout))
    one_verdict = plan_verdict(plan_info, plan_request, profile_name, file_sha256(plan_path))
    write_verdict(plan_path, one_verdict)
    out_lines = summary_lines(plan_info, one_verdict, repo_paths.repo_root, plan_path)
    with redacted_log(log_path, "a") as log_file:
        log_file.write("\n".join(out_lines) + "\n")
    print("\n".join(out_lines))
    exit_code = BLOCKED_EXIT if one_verdict.verdict_name == VERDICT_BLOCKED else 0
    print(f"ops: exit {exit_code}, log {log_path}", file=sys.stderr)
    return exit_code


def read_verdict(plan_path):
    verdict_path = verdict_path_of(plan_path)
    if not plan_path.is_file():
        raise OpsError(f"no plan file at {plan_path}; plan with {CLI_PATH} tf plan <stack> and run the `!` line "
                       f"it prints")
    if not verdict_path.is_file():
        raise OpsError(f"{plan_path} is not a plan ops produced (no {verdict_path.name} beside it); plan with "
                       f"{CLI_PATH} tf plan <stack> and run the `!` line it prints")
    try:
        verdict_data = json.loads(verdict_path.read_text(encoding="utf-8"))
        return verdict_data, PlanVerdict(str(verdict_data["stack"]), str(verdict_data["env"]),
                                         tuple(verdict_data["expected"]), tuple(verdict_data["guarded"]),
                                         str(verdict_data["plan_sha256"]))
    except (json.JSONDecodeError, KeyError, TypeError):
        raise OpsError(f"{verdict_path} is not a verdict ops wrote; plan again with "
                       f"{CLI_PATH} tf plan <stack>") from None


def cleared_verdict(repo_root, plan_path):
    verdict_data, one_verdict = read_verdict(plan_path)
    plan_again = f"plan again with {CLI_PATH} tf plan {one_verdict.stack_name}"
    if one_verdict.stack_name not in plan_stacks(repo_root) or one_verdict.profile_name not in ENV_PROFILES.values():
        raise OpsError(f"{verdict_path_of(plan_path)} names stack '{one_verdict.stack_name}' and profile "
                       f"'{one_verdict.profile_name}', which this checkout does not plan; {plan_again}")
    if verdict_data.get("verdict") != VERDICT_CLEAR or one_verdict.verdict_name != VERDICT_CLEAR:
        named_lines = [*[f"UNEXPECTED {one_address}" for one_address in one_verdict.unexpected_addresses],
                       *[f"ABSENT {one_address}" for one_address in one_verdict.absent_addresses]]
        raise OpsError(f"the guard blocked {plan_path} ({', '.join(named_lines)}); {plan_again} "
                       f"{EXPECT_OPTION} <address> for each change you mean")
    if one_verdict.plan_sha256 != redact_accounts(file_sha256(plan_path)):
        raise OpsError(f"the plan file {plan_path} changed after it was judged; {plan_again}")
    return one_verdict


def apply_env(one_verdict, requested_env):
    env_name = next(env_key for env_key, env_profile in ENV_PROFILES.items()
                    if env_profile == one_verdict.profile_name)
    if requested_env and requested_env != env_name:
        raise OpsError(f"the plan was made with --env {env_name}, and apply takes its profile from the plan; "
                       f"drop --env {requested_env}")
    return env_name


def run_apply(repo_paths, one_request, requested_env, system_effects, run_moment):
    one_verdict = cleared_verdict(repo_paths.repo_root, one_request.plan_path)
    env_name = apply_env(one_verdict, requested_env)
    require_init(repo_paths.repo_root, stack_info(repo_paths.repo_root, one_verdict.stack_name), env_name)
    require_session(repo_paths, one_verdict.profile_name, system_effects)
    script_invocation = ScriptInvocation(spec_named("tf apply"),
                                         apply_arguments(one_verdict.stack_name, one_request.plan_path),
                                         ("tf", "apply", one_verdict.stack_name), True)
    return run_script(repo_paths, script_invocation, one_verdict.profile_name, system_effects, run_moment)


def run_tf(repo_paths, ops_request, system_effects, caller_dir, run_moment):
    tf_command = tf_request(repo_paths.repo_root, list(ops_request.command_words[1:]), caller_dir)
    if isinstance(tf_command, ApplyRequest):
        return run_apply(repo_paths, tf_command, ops_request.env_name, system_effects, run_moment)
    one_stack = stack_info(repo_paths.repo_root, tf_command.stack_name)
    env_name = plan_env(repo_paths.repo_root, one_stack, ops_request.env_name)
    plan_request = dataclasses.replace(tf_command, input_args=stack_inputs(
        repo_paths.repo_root, one_stack, env_name, tf_command.extra_args))
    require_init(repo_paths.repo_root, one_stack, env_name)
    require_session(repo_paths, ENV_PROFILES[env_name], system_effects)
    return run_plan(repo_paths, plan_request, ENV_PROFILES[env_name], system_effects, run_moment)
