import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from shells import FIRST_SHELL, with_second_shell

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "provision" / "eso-takeover.sh"
WEBHOOK_DATA = '{"ca.crt":"V0VCSE9PSy1DQQ==","tls.crt":"V0VCSE9PSy1DRVJU","tls.key":"V0VCSE9PSy1LRVk="}'
SECRET_TEXT = "SUPERSECRETVALUE"

FAKE_AWS = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/aws-calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\\n' 111122223333 ;;
  "ssm send-command")
    while [ "$1" != "--parameters" ]; do shift; done
    printf '%s\\n' "$2" | jq -r '.commands[]' > "$FAKE_DIR/remote"
    "$FAKE_SHELL" "$FAKE_DIR/remote" > "$FAKE_DIR/stdout" 2> "$FAKE_DIR/stderr" \\
      && printf '%s\\n' Success > "$FAKE_DIR/status" || printf '%s\\n' Failed > "$FAKE_DIR/status"
    [ -f "$FAKE_DIR/never-answers" ] && printf '%s\\n' InProgress > "$FAKE_DIR/status"
    printf '%s\\n' cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) cat "$FAKE_DIR/status" ;;
      *StandardErrorContent*) cat "$FAKE_DIR/stderr" ;;
      *) cat "$FAKE_DIR/stdout" ;;
    esac ;;
  *) exit 9 ;;
esac
"""

FAKE_TOOL = """#!/usr/bin/env -S python3 -I
import json
import os
import signal
import subprocess
import sys
import tempfile
from pathlib import Path

fake_dir = Path(os.environ["FAKE_DIR"])
call_text = " ".join([Path(sys.argv[0]).name, *sys.argv[1:]])
with open(fake_dir / "calls", "a", encoding="utf-8") as calls_file:
    calls_file.write(call_text + "\\n")
fake_rules = json.loads((fake_dir / "rules.json").read_text(encoding="utf-8"))
for rule_index, one_rule in enumerate(fake_rules):
    if one_rule["match"] not in call_text:
        continue
    count_path = fake_dir / f"count-{rule_index}"
    seen_count = int(count_path.read_text()) if count_path.exists() else 0
    count_path.write_text(str(seen_count + 1))
    one_answer = one_rule["answers"][min(seen_count, len(one_rule["answers"]) - 1)]
    if one_answer.get("interrupt"):
        signal.signal(signal.SIGINT, signal.SIG_DFL)
        os.killpg(os.getpgrp(), signal.SIGINT)
    if "diff" in one_answer:
        with tempfile.TemporaryDirectory() as diff_root:
            for side_name in ("live", "merged"):
                side_dir = Path(diff_root) / side_name.upper()
                side_dir.mkdir()
                for file_name, file_text in one_answer["diff"][side_name].items():
                    (side_dir / file_name).write_text(file_text)
            diff_run = subprocess.run([os.environ["KUBECTL_EXTERNAL_DIFF"], f"{diff_root}/LIVE", f"{diff_root}/MERGED"])
        sys.exit(diff_run.returncode)
    sys.stdout.write(one_answer.get("out", ""))
    sys.stderr.write(one_answer.get("err", ""))
    sys.exit(one_answer.get("code", 0))
sys.stderr.write(f"fake: no rule for {call_text}\\n")
sys.exit(9)
"""

FAKE_SLEEP = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/sleeps"
"""

# Shaped from a recorded dry run and apply against the Seoul control plane: the CRD, Deployment, pod, Secret
# and ExternalSecret names and counts, the CRD generations, the Deployment revisions, the start times, the
# three ReplicaSet names the pods carry, the Application resource statuses (CRDs Synced, the two webhook
# configurations OutOfSync) and an empty diff. The uids and the name of the older external-secrets
# ReplicaSet are made up. The helm list JSON, the render and the diff files are written from the
# documented formats, not recorded.
CRD_ROWS = [(f"{plural_name}.external-secrets.io", generation_text) for plural_name, generation_text in (
    ("acraccesstokens.generators", "1"), ("clusterexternalsecrets", "1"), ("clustersecretstores", "2"),
    ("ecrauthorizationtokens.generators", "1"), ("externalsecrets", "2"), ("fakes.generators", "1"),
    ("gcraccesstokens.generators", "1"), ("githubaccesstokens.generators", "1"), ("passwords.generators", "1"),
    ("pushsecrets", "1"), ("secretstores", "2"), ("uuids.generators", "1"), ("vaultdynamicsecrets.generators", "1"),
    ("webhooks.generators", "1"))]
CRD_NAMES = [crd_row[0] for crd_row in CRD_ROWS]
WEBHOOK_NAMES = ["externalsecret-validate", "secretstore-validate"]
DEPLOYMENT_ROWS = [("external-secrets", "2"), ("external-secrets-cert-controller", "1"), ("external-secrets-webhook", "1")]
REPLICA_SET_NAMES = ["external-secrets-5c9f8d7b64", "external-secrets-67d885c886",
                     "external-secrets-cert-controller-7999cd84c4", "external-secrets-webhook-7bf7d4b488"]
SNAPSHOT_FORMAT_ROW = "format eso-takeover-2\n"
DEPLOYMENT_NAMES = [deployment_row[0] for deployment_row in DEPLOYMENT_ROWS]
POD_ROWS = [("external-secrets-67d885c886-76kkg", "2026-08-17T07:35:13Z"),
            ("external-secrets-cert-controller-7999cd84c4-mmg5r", "2026-08-17T07:35:03Z"),
            ("external-secrets-webhook-7bf7d4b488-hl68t", "2026-08-17T07:35:03Z")]
POD_NAMES = [pod_row[0] for pod_row in POD_ROWS]
SECRET_NAMES = ["backend-runtime-config", "cloudflare-tunnel-token", "grafana-loki-write", "grafana-remote-write",
                "jwt-encryption-key", "jwt-rs256-private-key", "jwt-rs256-public-key", "mysqld-exporter-dsn", "origin-tls"]


def fake_uid(kind_prefix, row_index):
    return f"00000000-0000-4000-8000-{kind_prefix}{row_index:010d}"


def yaml_document(kind_name, object_name, namespace_name=""):
    namespace_line = f"  namespace: {namespace_name}\n" if namespace_name else ""
    return (f"---\n# Source: external-secrets/templates/x.yaml\napiVersion: v1\nkind: {kind_name}\nmetadata:\n"
            f"  name: {object_name}\n{namespace_line}  labels:\n    app.kubernetes.io/instance: external-secrets\n"
            f"spec:\n  replicas: 1\n")


RENDER_TEXT = "".join(
    [yaml_document("CustomResourceDefinition", crd_name) for crd_name in CRD_NAMES]
    + [yaml_document("Deployment", deployment_name, "external-secrets") for deployment_name in DEPLOYMENT_NAMES]
    + [yaml_document("ServiceAccount", "external-secrets", "external-secrets"),
       yaml_document("Service", "external-secrets-webhook", "external-secrets"),
       yaml_document("Secret", "external-secrets-webhook", "external-secrets"),
       yaml_document("ClusterRole", "external-secrets-controller"),
       yaml_document("ClusterRoleBinding", "external-secrets-controller")]
    + [yaml_document("ValidatingWebhookConfiguration", webhook_name) for webhook_name in WEBHOOK_NAMES])

LIVE_ROWS = [("Deployment", "external-secrets", deployment_name) for deployment_name in DEPLOYMENT_NAMES] + [
    ("ServiceAccount", "external-secrets", "external-secrets"), ("Service", "external-secrets", "external-secrets-webhook"),
    ("Secret", "external-secrets", "external-secrets-webhook"), ("ClusterRole", "<none>", "external-secrets-controller"),
    ("ClusterRoleBinding", "<none>", "external-secrets-controller")]

LAST_APPLIED = ("  annotations:\n    kubectl.kubernetes.io/last-applied-configuration: |\n"
                "      {\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\"}\n")


def deployment_text(pod_annotation="true", object_annotation=""):
    return (f"apiVersion: apps/v1\nkind: Deployment\nmetadata:\n{object_annotation}  name: external-secrets\n"
            f"  namespace: external-secrets\nspec:\n  replicas: 1\n  template:\n    metadata:\n"
            f"      annotations:\n        prometheus.io/scrape: \"{pod_annotation}\"\n"
            f"      labels:\n        app.kubernetes.io/name: external-secrets\n    spec:\n      containers:\n"
            f"      - args:\n        - --concurrent=1\n        name: external-secrets\nstatus:\n  replicas: 1\n")


def cluster_role_text(rule_verbs):
    return ("apiVersion: rbac.authorization.k8s.io/v1\nkind: ClusterRole\nmetadata:\n"
            "  name: external-secrets-controller\nrules:\n- apiGroups:\n  - external-secrets.io\n"
            f"  resources:\n  - externalsecrets\n  verbs:\n{''.join(f'  - {one_verb}' + chr(10) for one_verb in rule_verbs)}")


def crd_text(label_lines=""):
    return f"apiVersion: apiextensions.k8s.io/v1\nkind: CustomResourceDefinition\nmetadata:\n{label_lines}  name: {CRD_NAMES[4]}\n"


SECRET_FILE = f"apiVersion: v1\ndata:\n  tls.key: {SECRET_TEXT}\nkind: Secret\nmetadata:\n  name: external-secrets-webhook\n"
DEPLOYMENT_FILE = "apps.v1.Deployment.external-secrets.external-secrets"
SECRET_FILE_NAME = "v1.Secret.external-secrets.external-secrets-webhook"
CRD_FILE_NAME = f"apiextensions.k8s.io.v1.CustomResourceDefinition..{CRD_NAMES[4]}"
ROLE_FILE_NAME = "rbac.authorization.k8s.io.v1.ClusterRole..external-secrets-controller"
ROLE_VERBS = ["get", "list", "watch"]
UNCHANGED_FILES = {DEPLOYMENT_FILE: deployment_text(), SECRET_FILE_NAME: SECRET_FILE, CRD_FILE_NAME: crd_text(),
                   ROLE_FILE_NAME: cluster_role_text(ROLE_VERBS)}
CLEAN_DIFF = {"live": UNCHANGED_FILES, "merged": UNCHANGED_FILES}
ANNOTATION_AND_CRD_DIFF = {"live": UNCHANGED_FILES, "merged": {
    **UNCHANGED_FILES, DEPLOYMENT_FILE: deployment_text(object_annotation=LAST_APPLIED),
    CRD_FILE_NAME: crd_text("  labels:\n    app.kubernetes.io/instance: external-secrets\n")}}
TEMPLATE_DIFF = {"live": UNCHANGED_FILES, "merged": {**UNCHANGED_FILES, DEPLOYMENT_FILE: deployment_text("false")}}
ROLE_DIFF = {"live": UNCHANGED_FILES, "merged": {**UNCHANGED_FILES, ROLE_FILE_NAME: cluster_role_text(ROLE_VERBS[:2])}}

STARTED_NOW = "2026-11-26T01:00:00Z"


def helm_list(chart_version="0.10.4", release_status="deployed"):
    return json.dumps([{"name": "external-secrets", "namespace": "external-secrets", "revision": "1",
                        "updated": "2026-08-17 07:34:40.123456789 +0000 UTC", "status": release_status,
                        "chart": f"external-secrets-{chart_version}", "app_version": f"v{chart_version}"}])


def resource_rows(**changed_rows):
    resource_list = ([("CustomResourceDefinition", "", crd_name, "Synced", "") for crd_name in CRD_NAMES]
                     + [("ValidatingWebhookConfiguration", "", webhook_name, "OutOfSync", "")
                        for webhook_name in WEBHOOK_NAMES]
                     + [("Deployment", "external-secrets", deployment_name, "Synced", "")
                        for deployment_name in DEPLOYMENT_NAMES])
    return [changed_rows.get(one_row[2], one_row) for one_row in resource_list]


def app_state(sync_status="OutOfSync", health_status="Healthy", operation_phase="", started_at="",
              pending_revision="", resource_list=None, operation_message=""):
    initiated_by = "take-over" if started_at == STARTED_NOW else ""
    state_lines = [f"sync\t{sync_status}", f"health\t{health_status}", f"phase\t{operation_phase}",
                   f"started\t{started_at}", f"pending\t{pending_revision}", f"initiator\t{initiated_by}"]
    state_lines += ["\t".join(["resource", *one_row])
                    for one_row in (resource_rows() if resource_list is None else resource_list)]
    state_lines.append(f"message\t{operation_message}")
    return {"out": "\n".join(state_lines) + "\n"}


TAKEN_OVER = app_state("Synced", "Healthy", "Succeeded", STARTED_NOW)
OPERATION_RUNNING = app_state(operation_phase="Running", started_at=STARTED_NOW, pending_revision="0.10.4")
VWC_UNSYNCED = app_state("OutOfSync", "Healthy", "Succeeded", STARTED_NOW)
OPERATION_FAILED = app_state(operation_phase="Failed", started_at=STARTED_NOW,
                             operation_message="one or more objects failed to apply")
FAILED_READ = {"code": 1, "err": "Unable to connect to the server: net/http: TLS handshake timeout\n"}


def column_rows(table_rows):
    return "".join("   ".join(one_row) + "\n" for one_row in table_rows)


def deployment_rows(changed_revision=""):
    return column_rows((deployment_name, fake_uid("20", deployment_index),
                        changed_revision if changed_revision and deployment_index == 0 else revision_text)
                       for deployment_index, (deployment_name, revision_text) in enumerate(DEPLOYMENT_ROWS))


def replica_set_rows(*extra_names):
    return column_rows((replica_set_name, fake_uid("60", replica_set_index))
                       for replica_set_index, replica_set_name in enumerate([*REPLICA_SET_NAMES, *extra_names]))


def pod_rows(changed_uid=""):
    return column_rows([(pod_name, changed_uid if changed_uid and pod_index == 0 else fake_uid("10", pod_index),
                         start_time, "0") for pod_index, (pod_name, start_time) in enumerate(POD_ROWS)])


class EsoTakeoverTest(unittest.TestCase):
    shell_path = FIRST_SHELL

    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.work_dir = Path(self.work_handle.name)
        self.fake_bin = self.work_dir / "bin"
        self.fake_bin.mkdir()
        for tool_name, tool_text in (("aws", FAKE_AWS), ("k3s", FAKE_TOOL), ("helm", FAKE_TOOL), ("sleep", FAKE_SLEEP)):
            (self.fake_bin / tool_name).write_text(tool_text)
            (self.fake_bin / tool_name).chmod(0o755)
        self.state_dir = self.work_dir / "state"
        self.baseline_path = self.state_dir / "oregon-baseline.txt"
        self.app_answers = [app_state()]
        self.pod_answers = [pod_rows()]
        self.deployment_answers = [deployment_rows()]
        self.replica_set_answers = [replica_set_rows()]
        self.helm_answer = {"out": helm_list()}
        self.render_answer = {"out": RENDER_TEXT}
        self.patch_answer = {"out": "application.argoproj.io/external-secrets patched\n"}
        self.live_rows = list(LIVE_ROWS)
        self.diff_answer = CLEAN_DIFF

    def tearDown(self):
        self.work_handle.cleanup()

    def write_rules(self):
        fake_rules = [
            {"match": "jsonpath=sync", "answers": self.app_answers},
            {"match": "valuesObject", "answers": [{"out": '{"installCRDs":true,"nodeSelector":{"role":"app"}}'}]},
            {"match": "patch application external-secrets", "answers": [self.patch_answer]},
            {"match": "helm -n external-secrets list", "answers": [self.helm_answer]},
            {"match": "helm template external-secrets external-secrets", "answers": [self.render_answer]},
            {"match": "get deploy,ds,sts", "answers": [{"out": column_rows(self.live_rows)}]},
            {"match": " diff -f ", "answers": [{"diff": self.diff_answer}]},
            {"match": "get deploy -o", "answers": [{"out": row_text} for row_text in self.deployment_answers]},
            {"match": "get replicasets -o", "answers": [{"out": row_text} for row_text in self.replica_set_answers]},
            {"match": "get pods", "answers": [pod_answer if isinstance(pod_answer, dict) else {"out": pod_answer}
                                              for pod_answer in self.pod_answers]},
            {"match": "get secrets -o", "answers": [{"out": column_rows(
                (secret_name, fake_uid("30", secret_index)) for secret_index, secret_name in enumerate(SECRET_NAMES))}]},
            {"match": "get secret external-secrets-webhook", "answers": [{"out": WEBHOOK_DATA}]},
            {"match": "get crd -o", "answers": [{"out": column_rows(
                [(crd_name, fake_uid("40", crd_index), generation_text)
                 for crd_index, (crd_name, generation_text) in enumerate(CRD_ROWS)]
                + [("gateways.gateway.networking.k8s.io", fake_uid("50", 0), "2")])}]},
            {"match": "get externalsecret", "answers": [{"out": "".join(
                f"{secret_name} True\n" for secret_name in SECRET_NAMES)}]},
        ]
        (self.work_dir / "rules.json").write_text(json.dumps(fake_rules))
        for count_path in self.work_dir.glob("count-*"):
            count_path.unlink()

    def takeover_run(self, *script_args, own_session=False):
        self.write_rules()
        run_env = {**os.environ, "PATH": f"{self.fake_bin}:{os.environ['PATH']}", "FAKE_DIR": str(self.work_dir),
                   "FAKE_SHELL": self.shell_path, "CP_INSTANCE_ID": "i-0cc", "AWS_PROFILE": "prod",
                   "ESO_TAKEOVER_DIR": str(self.state_dir)}
        return subprocess.run([str(SCRIPT_PATH), *script_args], capture_output=True, text=True, env=run_env,
                              stdin=subprocess.DEVNULL, start_new_session=own_session)

    def tool_calls(self):
        calls_path = self.work_dir / "calls"
        return calls_path.read_text().splitlines() if calls_path.exists() else []

    def patch_calls(self):
        return [one_call for one_call in self.tool_calls() if " patch application " in one_call]

    def assert_stopped(self, script_run, reason_text):
        self.assertEqual(script_run.returncode, 3, script_run.stdout + script_run.stderr)
        self.assertIn(reason_text, script_run.stdout)
        self.assertIn("nothing was patched", script_run.stdout)
        self.assertEqual(self.patch_calls(), [])
        self.assertFalse(self.baseline_path.exists())

    def assert_finished_ending(self, script_run, cause_text):
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn(cause_text, script_run.stdout)
        self.assertIn("no rollback ran", script_run.stdout)
        self.assertNotIn("may still be running", script_run.stdout)
        self.assertNotIn("rerun with --site oregon --apply", script_run.stdout)
        self.assertTrue(self.baseline_path.exists())

    def assert_unfinished_ending(self, script_run):
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("a sync operation was submitted and may still be running", script_run.stdout)
        self.assertIn(f"the baseline is saved at {self.baseline_path}", script_run.stdout)
        self.assertIn("next: rerun with --site oregon --apply; it verifies against the saved baseline", script_run.stdout)
        self.assertTrue(self.baseline_path.exists())

    def test_a_dry_run_on_a_clean_cluster_passes_every_check_and_prints_the_baseline(self):
        script_run = self.takeover_run("--site", "seoul")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        check_lines = [one_line for one_line in script_run.stdout.splitlines() if one_line.startswith("check ")]
        self.assertEqual(check_lines, [
            "check 1 helm release: ok: release external-secrets is external-secrets-0.10.4 deployed",
            "check 2 stray objects: ok: every labeled object is in the chart's render",
            "check 3 diff: ok: no difference",
            "check 4 application resources: ok: non-Synced: 0 CustomResourceDefinition, "
            "2 ValidatingWebhookConfiguration; none requires pruning"])
        output_lines = script_run.stdout.splitlines()
        webhook_hash = hashlib.sha256(WEBHOOK_DATA.encode()).hexdigest()
        self.assertIn(f"webhook-data external-secrets-webhook {webhook_hash}", output_lines)
        self.assertIn(f"pod {POD_NAMES[1]} {fake_uid('10', 1)} 2026-08-17T07:35:03Z 0", output_lines)
        self.assertIn(f"crd {CRD_NAMES[2]} {fake_uid('40', 2)} 2", output_lines)
        self.assertEqual(len([one_line for one_line in output_lines if one_line.startswith("crd ")]), 14)
        self.assertEqual(len([one_line for one_line in output_lines if one_line.startswith("secret ")]), 9)
        self.assertNotIn("gateways.gateway", script_run.stdout)
        self.assertIn("externalsecret origin-tls True", output_lines)
        for secret_part in (SECRET_TEXT, "V0VCSE9PSy1LRVk="):
            self.assertNotIn(secret_part, script_run.stdout + script_run.stderr)
        self.assertEqual(self.patch_calls(), [])
        self.assertFalse(self.state_dir.exists())
        self.assertIn("--region ap-northeast-2", (self.work_dir / "aws-calls").read_text())
        self.assertIn("helm template external-secrets external-secrets --repo https://charts.external-secrets.io "
                      "--version 0.10.4 -n external-secrets --include-crds -f ", "\n".join(self.tool_calls()))

    def test_the_last_applied_annotation_alone_and_a_crd_change_pass_the_diff(self):
        self.diff_answer = ANNOTATION_AND_CRD_DIFF
        script_run = self.takeover_run("--site", "oregon")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        self.assertIn("check 3 diff: ok: only CustomResourceDefinition and ValidatingWebhookConfiguration objects "
                      f"change: {CRD_FILE_NAME}", script_run.stdout.splitlines())

    def test_another_chart_version_stops(self):
        self.helm_answer = {"out": helm_list("0.10.3")}
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            "check 1 helm release: stop: release external-secrets is external-secrets-0.10.3 "
                            "deployed, want external-secrets-0.10.4 deployed")

    def test_a_stray_labeled_object_stops(self):
        self.live_rows.append(("ConfigMap", "external-secrets", "leftover-config"))
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            "check 2 stray objects: stop: labeled objects outside the chart's render: "
                            "ConfigMap/external-secrets/leftover-config")

    def test_a_diff_under_spec_template_stops(self):
        self.diff_answer = TEMPLATE_DIFF
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            f"check 3 diff: stop: the sync would change spec.template of {DEPLOYMENT_FILE}")

    def test_a_changed_cluster_role_rule_stops(self):
        self.diff_answer = ROLE_DIFF
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            "check 3 diff: stop: the sync would change objects besides CustomResourceDefinition and "
                            f"ValidatingWebhookConfiguration: {ROLE_FILE_NAME}")

    def test_a_deployment_that_is_not_synced_stops(self):
        self.app_answers = [app_state(resource_list=resource_rows(**{
            "external-secrets-webhook": ("Deployment", "external-secrets", "external-secrets-webhook", "OutOfSync", "")}))]
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            "check 4 application resources: stop: non-Synced resources besides the expected kinds: "
                            "Deployment/external-secrets/external-secrets-webhook OutOfSync (non-Synced: "
                            "0 CustomResourceDefinition, 2 ValidatingWebhookConfiguration)")

    def test_a_resource_that_requires_pruning_stops(self):
        self.app_answers = [app_state(resource_list=resource_rows(**{
            WEBHOOK_NAMES[0]: ("ValidatingWebhookConfiguration", "", WEBHOOK_NAMES[0], "OutOfSync", "true")}))]
        self.assert_stopped(self.takeover_run("--site", "oregon", "--apply"),
                            "check 4 application resources: stop: resources that require pruning: "
                            f"ValidatingWebhookConfiguration/-/{WEBHOOK_NAMES[0]}")

    def test_a_chart_fetch_failure_stops(self):
        self.render_answer = {"code": 1, "err": "Error: looks like \"https://charts.external-secrets.io\" is not a "
                                                "valid chart repository or cannot be reached: 503\n"}
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assert_stopped(script_run, "check 2 stray objects: stop: helm template external-secrets 0.10.4 failed: "
                                        "Error: looks like")
        self.assertIn("check 3 diff: stop: helm template external-secrets 0.10.4 failed", script_run.stdout)
        self.assertFalse([one_call for one_call in self.tool_calls() if " diff -f " in one_call])

    def test_apply_sends_one_patch_saves_the_baseline_waits_and_verifies(self):
        self.app_answers = [app_state(), app_state(pending_revision="0.10.4"), OPERATION_RUNNING, TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        patch_list = self.patch_calls()
        self.assertEqual(len(patch_list), 1)
        self.assertIn('-p {"operation":{"initiatedBy":{"username":"take-over"},'
                      '"sync":{"revision":"0.10.4","prune":false}}}', patch_list[0])
        self.assertIn("poll 2/120: operation Running, sync OutOfSync, health Healthy", script_run.stdout)
        self.assertIn("operation Succeeded; Application external-secrets Synced and Healthy", script_run.stdout)
        self.assertIn("the snapshot equals the baseline", script_run.stdout)
        self.assertIn("ops verify fleet --site oregon", script_run.stdout)
        self.assertIn(f"baseline saved to {self.baseline_path}", script_run.stdout)
        saved_lines = self.baseline_path.read_text().splitlines()
        self.assertEqual(saved_lines[0], SNAPSHOT_FORMAT_ROW.strip())
        self.assertIn(f"pod {POD_NAMES[0]} {fake_uid('10', 0)} 2026-08-17T07:35:13Z 0", saved_lines)
        self.assertIn(f"deployment external-secrets {fake_uid('20', 0)} 2", saved_lines)
        self.assertIn(f"replicaset {REPLICA_SET_NAMES[0]} {fake_uid('60', 0)}", saved_lines)
        self.assertNotIn("before:", script_run.stdout)
        self.assertNotIn("may still be running", script_run.stdout)

    def test_the_deployment_read_takes_the_revision_and_not_the_generation(self):
        self.app_answers = [app_state(), TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        deployment_reads = [one_call for one_call in self.tool_calls() if " get deploy -o " in one_call]
        self.assertEqual(len(deployment_reads), 2)
        for one_call in deployment_reads:
            self.assertIn("{.metadata.annotations.deployment\\.kubernetes\\.io/revision}", one_call)
            self.assertNotIn("generation", one_call)

    def test_a_new_replica_set_or_a_revision_change_after_the_sync_differs(self):
        for changed_rows, after_line in (
                ({"replica_set_answers": [replica_set_rows(), replica_set_rows("external-secrets-7f6d5c4b3a")]},
                 f"after:  replicaset external-secrets-7f6d5c4b3a {fake_uid('60', 4)}"),
                ({"deployment_answers": [deployment_rows(), deployment_rows("3")]},
                 f"after:  deployment external-secrets {fake_uid('20', 0)} 3")):
            with self.subTest(changed_rows=list(changed_rows)):
                for state_file in self.state_dir.glob("*"):
                    state_file.unlink()
                self.app_answers = [app_state(), TAKEN_OVER]
                for attribute_name, attribute_value in changed_rows.items():
                    setattr(self, attribute_name, attribute_value)
                script_run = self.takeover_run("--site", "oregon", "--apply")
                self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
                self.assertIn("the sync ran and the snapshot differs from the saved baseline; do not rerun --apply",
                              script_run.stdout)
                self.assertIn(after_line, script_run.stdout.splitlines())
                self.deployment_answers = [deployment_rows()]
                self.replica_set_answers = [replica_set_rows()]

    def test_a_saved_baseline_in_the_old_format_is_not_compared(self):
        self.state_dir.mkdir()
        self.baseline_path.write_text(f"deployment external-secrets {fake_uid('20', 0)} 2\n"
                                      f"pod {POD_NAMES[0]} {fake_uid('10', 0)} 2026-08-17T07:35:13Z 0\n")
        self.app_answers = [TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn(f"the baseline saved at {self.baseline_path} predates the snapshot format eso-takeover-2",
                      script_run.stdout)
        self.assertIn("next: compare by hand:", script_run.stdout)
        self.assertNotIn("VERIFY", script_run.stdout)
        self.assertNotIn("taken over and verified", script_run.stdout)
        self.assertEqual(self.patch_calls(), [])
        self.assertFalse([one_call for one_call in self.tool_calls() if " get pods " in one_call])

    def test_succeeded_but_out_of_sync_keeps_polling_until_synced(self):
        settling_state = app_state("OutOfSync", "Healthy", "Succeeded", STARTED_NOW)
        self.app_answers = [app_state(), OPERATION_RUNNING, settling_state, settling_state, TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        self.assertIn("poll 2/120: operation Succeeded, sync OutOfSync, health Healthy", script_run.stdout)
        self.assertIn("poll 3/120: operation Succeeded, sync OutOfSync, health Healthy", script_run.stdout)
        self.assertIn("the snapshot equals the baseline", script_run.stdout)

    def test_a_failed_poll_is_retried_within_the_poll_count(self):
        self.app_answers = [app_state(), OPERATION_RUNNING, FAILED_READ, TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        self.assertIn("poll 2/120: reading the Application failed; polling again", script_run.stdout)
        self.assertIn("TLS handshake timeout", script_run.stderr)
        self.assertNotIn("a remote command failed", script_run.stdout + script_run.stderr)
        self.assertEqual(len(self.patch_calls()), 1)

    def test_a_failed_patch_call_reaches_the_common_ending(self):
        self.patch_answer = {"code": 1, "err": "Error from server (InternalError): etcdserver: request timed out\n"}
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assert_unfinished_ending(script_run)
        self.assertIn("etcdserver: request timed out", script_run.stderr)
        self.assertNotIn("WAIT", script_run.stdout)

    def test_a_failed_operation_says_not_to_rerun_and_a_rerun_sends_no_patch(self):
        self.app_answers = [app_state(), OPERATION_RUNNING, OPERATION_FAILED]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assert_finished_ending(script_run, "do not rerun --apply")
        self.assertIn("the take-over operation ended Failed: one or more objects failed to apply", script_run.stdout)
        self.assertNotIn("VERIFY", script_run.stdout)
        self.app_answers = [OPERATION_FAILED]
        rerun_result = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(rerun_result.returncode, 3, rerun_result.stdout + rerun_result.stderr)
        self.assertIn("the take-over operation ended Failed: one or more objects failed to apply", rerun_result.stdout)
        self.assertIn("nothing was patched", rerun_result.stdout)
        self.assertEqual(len(self.patch_calls()), 1)

    def test_succeeded_with_the_webhook_configurations_out_of_sync_ends_with_their_cause(self):
        self.app_answers = [app_state(), VWC_UNSYNCED]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("poll 120/120: operation Succeeded, sync OutOfSync, health Healthy", script_run.stdout)
        self.assertIn("the snapshot equals the baseline; every ExternalSecret is Ready", script_run.stdout)
        self.assertIn("the sync ran and nothing was recreated: the snapshot equals the saved baseline", script_run.stdout)
        self.assertIn(f"not Synced: ValidatingWebhookConfiguration/{WEBHOOK_NAMES[0]} OutOfSync, "
                      f"ValidatingWebhookConfiguration/{WEBHOOK_NAMES[1]} OutOfSync", script_run.stdout)
        self.assertIn("the cert-controller injects its caBundle", script_run.stdout)
        self.assertIn("next: add the ignoreDifferences entry in deploy/chart/templates/external-secrets.yaml, merge, "
                      "then rerun with --site oregon --apply: it sends no second sync and verifies against the saved "
                      "baseline", script_run.stdout)
        self.assertNotIn("may still be running", script_run.stdout)
        self.assertNotIn("do not rerun", script_run.stdout)
        self.app_answers = [VWC_UNSYNCED]
        rerun_result = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(rerun_result.returncode, 3, rerun_result.stdout + rerun_result.stderr)
        self.assertIn("the sync ran and nothing was recreated", rerun_result.stdout)
        self.assertIn("nothing was patched", rerun_result.stdout)
        self.assertEqual(len(self.patch_calls()), 1)

    def test_succeeded_out_of_sync_with_a_recreated_pod_is_the_differs_ending(self):
        self.state_dir.mkdir()
        self.baseline_path.write_text(f"{SNAPSHOT_FORMAT_ROW}pod {POD_NAMES[0]} pod-uid-before 2026-08-17T07:35:13Z 0\n")
        self.app_answers = [VWC_UNSYNCED]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("the sync ran and the snapshot differs from the saved baseline; do not rerun --apply",
                      script_run.stdout)
        self.assertNotIn("nothing was recreated", script_run.stdout)
        self.assertEqual(self.patch_calls(), [])

    def test_an_unsynced_deployment_gets_no_webhook_cause(self):
        self.app_answers = [app_state(), app_state("OutOfSync", "Progressing", "Succeeded", STARTED_NOW,
                                                   resource_list=resource_rows(**{DEPLOYMENT_NAMES[0]: (
                                                       "Deployment", "external-secrets", DEPLOYMENT_NAMES[0],
                                                       "OutOfSync", "")}))]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("the sync ran and nothing was recreated", script_run.stdout)
        self.assertIn(f"Deployment/{DEPLOYMENT_NAMES[0]} OutOfSync", script_run.stdout)
        self.assertIn("no rollback ran", script_run.stdout)
        self.assertIn("after the fix syncs, rerun with --site oregon --apply: it sends no second sync",
                      script_run.stdout)
        self.assertNotIn("caBundle", script_run.stdout)

    def test_out_of_sync_webhook_configurations_and_an_unfinished_comparison_claim_nothing_about_the_snapshot(self):
        for pod_failure in (FAILED_READ, {"interrupt": True}):
            with self.subTest(pod_failure=pod_failure):
                for state_file in self.state_dir.glob("*"):
                    state_file.unlink()
                self.app_answers = [app_state(), VWC_UNSYNCED]
                self.pod_answers = [pod_rows(), pod_failure]
                script_run = self.takeover_run("--site", "oregon", "--apply", own_session=True)
                self.assertNotEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
                self.assertIn("the snapshot comparison against the saved baseline did not finish", script_run.stdout)
                self.assertIn(f"not Synced: ValidatingWebhookConfiguration/{WEBHOOK_NAMES[0]} OutOfSync", script_run.stdout)
                self.assertIn("next: rerun with --site oregon --apply; it sends no second sync and verifies against "
                              "the saved baseline", script_run.stdout)
                self.assertNotIn("nothing was recreated", script_run.stdout)
                self.assertNotIn("snapshot equals", script_run.stdout)

    def test_a_settling_poll_then_synced_then_a_failed_snapshot_read_is_the_read_failed_ending(self):
        self.app_answers = [app_state(), VWC_UNSYNCED, TAKEN_OVER]
        self.pod_answers = [pod_rows(), FAILED_READ]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("the snapshot could not be read from the control plane", script_run.stdout)
        self.assertIn("next: rerun with --site oregon --apply; it verifies against the saved baseline", script_run.stdout)
        self.assertNotIn("nothing was recreated", script_run.stdout)
        self.assertNotIn("not Synced:", script_run.stdout)

    def test_a_failed_snapshot_read_on_a_verifying_rerun_names_the_rerun(self):
        self.state_dir.mkdir()
        self.baseline_path.write_text(f"{SNAPSHOT_FORMAT_ROW}pod {POD_NAMES[0]} pod-uid-before 2026-08-17T07:35:13Z 0\n")
        self.app_answers = [TAKEN_OVER]
        self.pod_answers = [FAILED_READ]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("next: rerun with --site oregon --apply; it verifies against the saved baseline", script_run.stdout)
        self.assertEqual(self.patch_calls(), [])

    def test_an_earlier_failed_operation_is_not_taken_for_the_new_one(self):
        old_failure = app_state(operation_phase="Failed", started_at="2026-11-20T01:00:00Z")
        self.app_answers = [old_failure, old_failure, TAKEN_OVER]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 0, script_run.stdout + script_run.stderr)
        self.assertIn("poll 1/120: operation Failed", script_run.stdout)

    def test_a_changed_pod_uid_after_the_sync_exits_1_with_both_rows(self):
        self.app_answers = [app_state(), TAKEN_OVER]
        self.pod_answers = [pod_rows(), pod_rows("pod-uid-new")]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assert_finished_ending(script_run, "the sync ran and the snapshot differs from the saved baseline; "
                                                "do not rerun --apply")
        output_lines = script_run.stdout.splitlines()
        before_index = output_lines.index(f"before: pod {POD_NAMES[0]} {fake_uid('10', 0)} 2026-08-17T07:35:13Z 0")
        self.assertEqual(output_lines[before_index + 1], f"after:  pod {POD_NAMES[0]} pod-uid-new 2026-08-17T07:35:13Z 0")
        self.assertEqual(len([one_line for one_line in output_lines if one_line.startswith("before:")]), 1)
        self.assertEqual(len(self.patch_calls()), 1)

    def test_a_rerun_after_a_failed_verify_compares_against_the_saved_baseline(self):
        self.app_answers = [app_state(), TAKEN_OVER]
        self.pod_answers = [pod_rows(), pod_rows("pod-uid-new")]
        self.assertEqual(self.takeover_run("--site", "oregon", "--apply").returncode, 1)
        self.app_answers = [TAKEN_OVER]
        self.pod_answers = [pod_rows("pod-uid-new")]
        rerun_result = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(rerun_result.returncode, 1, rerun_result.stdout + rerun_result.stderr)
        self.assertIn(f"VERIFY against the baseline saved at {self.baseline_path}", rerun_result.stdout)
        self.assertIn(f"after:  pod {POD_NAMES[0]} pod-uid-new 2026-08-17T07:35:13Z 0", rerun_result.stdout.splitlines())
        self.assertEqual(len(self.patch_calls()), 1)

    def test_a_rerun_after_an_interrupted_apply_verifies_against_the_saved_baseline(self):
        self.patch_answer = {"code": 1, "err": "Error from server (InternalError): etcdserver: request timed out\n"}
        self.assertEqual(self.takeover_run("--site", "oregon", "--apply").returncode, 1)
        self.app_answers = [TAKEN_OVER]
        rerun_result = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(rerun_result.returncode, 0, rerun_result.stdout + rerun_result.stderr)
        self.assertIn("the snapshot equals the baseline", rerun_result.stdout)
        self.assertIn("taken over and verified", rerun_result.stdout)

    def test_a_saved_baseline_and_a_failed_or_errored_take_over_operation_send_no_second_patch(self):
        self.state_dir.mkdir()
        self.baseline_path.write_text(f"{SNAPSHOT_FORMAT_ROW}pod {POD_NAMES[0]} pod-uid-before 2026-08-17T07:35:13Z 0\n")
        for operation_phase in ("Failed", "Error"):
            with self.subTest(operation_phase=operation_phase):
                self.app_answers = [app_state(operation_phase=operation_phase, started_at=STARTED_NOW,
                                              operation_message="one or more objects failed to apply")]
                script_run = self.takeover_run("--site", "oregon", "--apply")
                self.assertEqual(script_run.returncode, 3, script_run.stdout + script_run.stderr)
                self.assertIn(f"the take-over operation ended {operation_phase}: one or more objects failed to apply",
                              script_run.stdout)
                self.assertIn("do not rerun --apply", script_run.stdout)
                self.assertIn("an earlier --apply already sent the take-over sync", script_run.stdout)
        self.assertEqual(self.patch_calls(), [])
        self.assertEqual(self.baseline_path.read_text(),
                         f"{SNAPSHOT_FORMAT_ROW}pod {POD_NAMES[0]} pod-uid-before 2026-08-17T07:35:13Z 0\n")

    def test_already_taken_over_without_a_saved_baseline_is_exit_4_and_verifies_nothing(self):
        self.app_answers = [app_state("Synced", "Healthy", "Succeeded", STARTED_NOW,
                                      resource_list=[("Deployment", "external-secrets", "external-secrets", "Synced", "")])]
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 4, script_run.stdout + script_run.stderr)
        self.assertIn("already taken over; this run verified nothing", script_run.stdout)
        self.assertIn("next: compare the before rows a person saved by hand with:", script_run.stdout)
        self.assertIn("with none saved, read the ExternalSecrets' Ready conditions and the operator pods' ages",
                      script_run.stdout)
        self.assertEqual(self.patch_calls(), [])
        self.assertFalse([one_call for one_call in self.tool_calls() if " get pods " in one_call])

    def test_a_failed_remote_command_exits_1_with_the_helper_message(self):
        self.helm_answer = {"code": 1, "err": "Error: Kubernetes cluster unreachable\n"}
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("ended with status Failed", script_run.stderr)
        self.assertIn("helm -n external-secrets list failed", script_run.stderr)
        self.assertEqual(self.patch_calls(), [])

    def test_a_control_plane_that_never_answers_exits_1_with_the_helper_message(self):
        (self.work_dir / "never-answers").write_text("")
        script_run = self.takeover_run("--site", "oregon", "--apply")
        self.assertEqual(script_run.returncode, 1, script_run.stdout + script_run.stderr)
        self.assertIn("the control plane did not answer within 180 seconds; status InProgress", script_run.stderr)
        self.assertEqual(self.patch_calls(), [])

    def test_usage_errors_exit_2_before_any_aws_call(self):
        for script_args in ((), ("--site", "tokyo"), ("--site",), ("--site", "oregon", "--force")):
            with self.subTest(script_args=script_args):
                self.assertEqual(self.takeover_run(*script_args).returncode, 2)
        self.assertFalse((self.work_dir / "aws-calls").exists())


with_second_shell(EsoTakeoverTest, globals())


if __name__ == "__main__":
    unittest.main()
