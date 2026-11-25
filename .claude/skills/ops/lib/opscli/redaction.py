import contextlib
import functools
import os
import re
import sys
from pathlib import Path

from opscli.paths import (
    ACCOUNT_ID_LINE, ARN_ACCOUNT_PATTERN, AWS_CONFIG_ENV, CONFIG_SECTION_PATTERN, DEFAULT_AWS_CONFIG,
    ECR_HOST_PATTERN, ENV_PROFILES, UNMAPPED_ACCOUNT)

REDACTED_TEXT = "<redacted>"
REDACTED_BLOCK = "<redacted key block>"
LEARNED_MIN_LENGTH = 16
SHELL_WORD = r"""(?:"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|\\.|[^\s"'\\])+|\S+"""
TOKEN_ASSIGNMENT = re.compile(rf"(TOKEN=)({SHELL_WORD})")
SECRET_PATTERNS = (
    (TOKEN_ASSIGNMENT, rf"\1{REDACTED_TEXT}"),
    (re.compile(r"(?<![A-Za-z0-9+/])K10(?:[0-9A-Za-z]*::[^\s'\"]+|[0-9A-Za-z]{8,})"), REDACTED_TEXT),
    (re.compile(rf"(--(?:agent-)?token[ =])({SHELL_WORD})"), rf"\1{REDACTED_TEXT}"),
    (re.compile(r"(X-aws-ec2-metadata-token:\s*)[^\s'\"]+", re.IGNORECASE), rf"\1{REDACTED_TEXT}"),
    (re.compile(rf"(--[\w-]*password[ ]+)({SHELL_WORD})", re.IGNORECASE), rf"\1{REDACTED_TEXT}"),
    (re.compile(rf"(pass(?:word|wd|phrase)\w*[\"']?[ \t]*[=:][ \t]*|MYSQL_PWD=)({SHELL_WORD})", re.IGNORECASE),
     rf"\1{REDACTED_TEXT}"),
    (re.compile(r"(://[^\s:/@]*:)[^\s/@]+(?=@)"), rf"\1{REDACTED_TEXT}"),
    (re.compile(r"(client-key-data:\s*)\S+"), rf"\1{REDACTED_TEXT}"),
    (re.compile(r"(\+ echo )[A-Za-z0-9]{32,}(?=\s*$)", re.MULTILINE), rf"\1{REDACTED_TEXT}"),
    (re.compile(r"\b((?:ssh-(?:rsa|dss|ed25519)|ecdsa-sha2-\S+)\s+)AAAA[A-Za-z0-9+/=]+"), rf"\1{REDACTED_TEXT}"),
)
KEY_BLOCK_MAX_LINES = 128
KEY_MARKER = re.compile(r"-----(BEGIN|END) [A-Z0-9 ]*KEY[A-Z0-9 ]*-----")


def aws_config_path():
    return Path(os.environ.get(AWS_CONFIG_ENV) or Path(DEFAULT_AWS_CONFIG).expanduser())


@functools.cache
def config_profiles(config_file):
    if not config_file.is_file():
        return ()
    profile_rows = []
    section_name = ""
    for text_line in config_file.read_text(encoding="utf-8", errors="replace").splitlines():
        section_match = CONFIG_SECTION_PATTERN.match(text_line)
        if section_match:
            section_name = section_match.group(1)
            continue
        id_match = ACCOUNT_ID_LINE.match(text_line)
        if id_match and section_name:
            profile_rows.append((section_name, id_match.group(1)))
    return tuple(profile_rows)


@functools.cache
def config_accounts(config_file):
    account_profiles = {}
    for section_name, account_id in config_profiles(config_file):
        known_profile = account_profiles.get(account_id)
        if known_profile is None or (section_name in ENV_PROFILES.values()
                                     and known_profile not in ENV_PROFILES.values()):
            account_profiles[account_id] = section_name
    return account_profiles


def profile_account(profile_name):
    return dict(config_profiles(aws_config_path())).get(profile_name, "")


def redact_accounts(raw_text):
    for account_id, profile_name in config_accounts(aws_config_path()).items():
        raw_text = raw_text.replace(account_id, f"<account:{profile_name}>")
    return ARN_ACCOUNT_PATTERN.sub(rf"\1{UNMAPPED_ACCOUNT}", raw_text)


class SecretRedactor:
    def __init__(self):
        self.in_key_block = False
        self.hidden_newlines = 0
        self.hidden_line_open = False
        self.learned_values = set()

    def redact_chunk(self, raw_text):
        for token_match in TOKEN_ASSIGNMENT.finditer(raw_text):
            token_value = token_match.group(2).strip("$'\"")
            if len(token_value) >= LEARNED_MIN_LENGTH and token_value != REDACTED_TEXT:
                self.learned_values.add(token_value)
        kept_text = self.without_key_blocks(raw_text)
        for one_pattern, replacement_text in SECRET_PATTERNS:
            kept_text = one_pattern.sub(replacement_text, kept_text)
        for token_value in sorted(self.learned_values, key=len, reverse=True):
            kept_text = kept_text.replace(token_value, REDACTED_TEXT)
        return ECR_HOST_PATTERN.sub(UNMAPPED_ACCOUNT, redact_accounts(kept_text))

    def without_key_blocks(self, raw_text):
        settled_text = ""
        kept_text = ""
        rest_text = raw_text
        while rest_text:
            marker_match = KEY_MARKER.search(rest_text)
            text_before = rest_text[:marker_match.start()] if marker_match else rest_text
            if self.in_key_block:
                shown_text = self.text_past_bound(text_before)
                if shown_text is not None:
                    rest_text = shown_text + rest_text[len(text_before):]
                elif not marker_match:
                    return kept_text
                elif marker_match.group(1) == "END":
                    self.in_key_block = False
                    rest_text = rest_text[marker_match.end():]
                    continue
                else:
                    rest_text = rest_text[len(text_before):]
                kept_text += self.end_notice()
                settled_text = kept_text
                continue
            if not marker_match:
                return kept_text + rest_text
            rest_text = rest_text[marker_match.end():]
            if marker_match.group(1) == "END":
                kept_text = settled_text + REDACTED_BLOCK
                continue
            kept_text += text_before + REDACTED_BLOCK
            self.in_key_block = True
            self.hidden_newlines = 0
            self.hidden_line_open = True
        return kept_text

    def text_past_bound(self, hidden_text):
        newline_budget = KEY_BLOCK_MAX_LINES + 1 - self.hidden_newlines
        line_parts = hidden_text.split("\n", newline_budget)
        past_bound = len(line_parts) > newline_budget
        self.hidden_newlines += len(line_parts) - 1
        if past_bound:
            self.hidden_line_open = False
            return line_parts[-1]
        if hidden_text:
            self.hidden_line_open = not hidden_text.endswith("\n")
        return None

    def end_notice(self):
        if not self.in_key_block:
            return ""
        self.in_key_block = False
        line_count = self.hidden_newlines + self.hidden_line_open
        return f"\n<redacted key block never closed: {line_count} lines hidden>\n"


def redact_text(raw_text):
    secret_redactor = SecretRedactor()
    return secret_redactor.redact_chunk(raw_text) + secret_redactor.end_notice()


def account_label(account_id):
    label_text = redact_text(account_id)
    return UNMAPPED_ACCOUNT if re.search(r"\d{12}", label_text) else label_text


def caller_name(arn_text):
    return arn_text.rsplit("/", 1)[-1] if "/" in arn_text else arn_text.rsplit(":", 1)[-1]


class RedactingWriter:
    def __init__(self, target_file):
        self.target_file = target_file
        self.secret_redactor = SecretRedactor()

    def write(self, text_chunk):
        return self.target_file.write(self.secret_redactor.redact_chunk(text_chunk))

    def end_output(self):
        end_notice = self.secret_redactor.end_notice()
        if end_notice:
            self.target_file.write(end_notice)

    def flush(self):
        self.target_file.flush()

    def __getattr__(self, attr_name):
        return getattr(self.target_file, attr_name)


@contextlib.contextmanager
def redacted_streams():
    saved_streams = (sys.stdout, sys.stderr)
    redacting_streams = tuple(RedactingWriter(one_stream) for one_stream in saved_streams)
    sys.stdout, sys.stderr = redacting_streams
    try:
        yield
    finally:
        sys.stdout, sys.stderr = saved_streams
        for one_stream in redacting_streams:
            one_stream.end_output()


@contextlib.contextmanager
def redacted_log(log_path, open_mode):
    with open(log_path, open_mode, encoding="utf-8") as raw_log:
        log_writer = RedactingWriter(raw_log)
        try:
            yield log_writer
        finally:
            log_writer.end_output()
