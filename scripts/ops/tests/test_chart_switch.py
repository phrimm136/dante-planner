import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from shells import FIRST_SHELL, with_second_shell

SCRIPT = Path(__file__).resolve().parents[1] / "provision" / "gitops-chart-switch.sh"
REGISTRY = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/danteplanner-backend"
EXPECTED = [("ConfigMap", "mysqld-tls-cnf"), ("ConfigMap", "rds-ca-bundle"), ("ConfigMap", "mysqld-tls-cnf-ccth2t5d4h"),
            ("ConfigMap", "rds-ca-bundle-28d64bc9gf"), ("Deployment", "mysqld-exporter")]

FAKE_K3S = r'''#!%(python)s
import json, os, re, sys
state_path = os.environ["FAKE_DIR"] + "/app.json"
plan = json.load(open(os.environ["FAKE_DIR"] + "/plan.json"))
args = sys.argv[1:]
verb = next(word for word in args if word in ("get", "patch", "annotate"))
name = args[args.index("application") + 1]
app = json.load(open(state_path))
if name != app["metadata"]["name"]:
    sys.exit("not found")

def merge(target, change):
    for key, value in change.items():
        if value is None:
            target.pop(key, None)
        elif isinstance(value, dict):
            merge(target.setdefault(key, {}), value)
        else:
            target[key] = value

def at(node, path):
    for part in re.findall(r"\.((?:\\\.|[^.\[])+)|\[(\d+)\]|\[\?\(@\.name==\"([^\"]+)\"\)\]", path):
        if part[0]:
            node = node.get(part[0].replace("\\.", "."), "") if isinstance(node, dict) else ""
        elif part[1]:
            node = node[int(part[1])] if isinstance(node, list) and len(node) > int(part[1]) else ""
        else:
            node = next((item for item in node if item["name"] == part[2]), "") if isinstance(node, list) else ""
    return node if isinstance(node, str) else json.dumps(node) if node else ""

def render(node, template):
    out, rest = "", template
    while rest:
        loop = re.match(r"\{range (\.[^\[]+)\[\*\]\}(.*?)\{end\}", rest, re.S)
        if loop:
            items = node
            for part in loop.group(1).strip(".").split("."):
                items = items.get(part, []) if isinstance(items, dict) else []
            out += "".join(render(item, loop.group(2)) for item in items)
            rest = rest[loop.end():]
            continue
        token = re.match(r"\{\"((?:\\.|[^\"])*)\"\}|\{(\.(?:\\.|[^}])*)\}", rest)
        if token:
            out += token.group(1).replace("\\n", "\n") if token.group(1) is not None else at(node, token.group(2))
            rest = rest[token.end():]
        else:
            out += rest[0]
            rest = rest[1:]
    return out

with open(os.environ["FAKE_DIR"] + "/calls", "a") as calls:
    calls.write(verb + " " + (args[args.index("-p") + 1] if verb == "patch" else "") + "\n")
if verb == "get" and plan.get("signal_at"):
    spec = app["spec"]
    at_point = {"comparing": spec["source"]["path"] == "deploy/chart" and "automated" not in spec["syncPolicy"],
                "syncing": spec["source"]["path"] == "deploy/chart" and "automated" in spec["syncPolicy"]}[plan["signal_at"]]
    if at_point and not os.path.exists(os.environ["FAKE_DIR"] + "/signalled"):
        open(os.environ["FAKE_DIR"] + "/signalled", "w").close()
        os.kill(int(open(os.environ["FAKE_DIR"] + "/pid").read()), 15)
if verb == "get" and plan.get("slow") and "comparedTo" in args[-1]:
    import time
    time.sleep(plan["slow"])
if verb == "get" and plan.get("unreadable") and plan["unreadable"] in args[-1]:
    sys.exit("timed out")
if verb == "get":
    template = args[args.index("-o") + 1][len("jsonpath="):]
    sys.stdout.write(render(app, template))
    if "refresh" in template and app["metadata"].get("annotations"):
        app["metadata"]["reads"] = app["metadata"].get("reads", 0) + 1
        if app["metadata"]["reads"] > 1:
            del app["metadata"]["annotations"]
            app["status"].update(app.pop("late", {}))
        json.dump(app, open(state_path, "w"))
    if plan.get("sync_after_off") and "automated" not in app["spec"]["syncPolicy"] and "operation" in template:
        app["busy_reads"] = app.get("busy_reads", 0) + 1
        if app["busy_reads"] == 2:
            app["status"]["operationState"]["phase"] = "Succeeded"
            app.pop("operation", None)
        json.dump(app, open(state_path, "w"))
    sys.exit(0)
if verb == "patch":
    change = json.loads(args[args.index("-p") + 1])
    if plan.get("refuse_exact") == change or (plan.get("refuse") and plan["refuse"] in json.dumps(change)):
        sys.exit("refused")
    merge(app, change)
    if plan.get("sync_after_off") and "automated" not in app["spec"]["syncPolicy"] and app["spec"]["source"]["path"] != "deploy/chart":
        app["operation"] = {"sync": {"prune": True}}
        app["status"]["operationState"]["phase"] = "Running"
    if app["spec"]["source"]["path"] == "deploy/chart" and app.get("operation"):
        app["applied_chart_early"] = True
    if "automated" in app["spec"].get("syncPolicy", {}) and app["spec"]["source"]["path"] == "deploy/chart" and not plan.get("stuck"):
        app["status"]["sync"]["status"] = "Synced"
        app["status"]["resources"] = [{"kind": "Deployment", "name": "mysqld-exporter", "status": "Synced"}]
    if app["spec"]["source"]["path"].startswith("deploy/overlays"):
        app["status"]["sync"] = {"status": "Synced", "comparedTo": {"source": {"path": app["spec"]["source"]["path"]}}}
if verb == "annotate":
    app["metadata"]["annotations"] = {"argocd.argoproj.io/refresh": "hard"}
    app["metadata"]["reads"] = 0
if verb == "annotate" and app["spec"]["source"]["path"] == "deploy/chart":
    if plan.get("conditions"):
        app["status"]["conditions"] = plan["conditions"]
    if not plan.get("never_compared"):
        app["status"]["sync"] = {"status": plan.get("sync", "OutOfSync"), "comparedTo": {"source": {"path": "deploy/chart"}}}
        app["late"] = {"resources": [{"kind": kind, "name": item, "status": "OutOfSync"} for kind, item in plan["differ"]] + [
            {"kind": "StatefulSet", "name": "redis-auth", "status": "Synced"}]}
json.dump(app, open(state_path, "w"))
'''


def application(**overrides):
    app = {
        "metadata": {"name": "danteplanner-seoul"},
        "spec": {"source": {"path": "deploy/overlays/seoul", "targetRevision": "main",
                            "kustomize": {"images": [f"danteplanner-backend={REGISTRY}:*"]}},
                 "syncPolicy": {"automated": {"prune": True, "selfHeal": True}, "syncOptions": ["CreateNamespace=true"]}},
        "status": {"sync": {"status": "Synced", "comparedTo": {"source": {"path": "deploy/overlays/seoul"}}},
                   "health": {"status": "Healthy"}, "operationState": {"phase": "Succeeded"},
                   "resources": [{"kind": "StatefulSet", "name": "redis-auth", "status": "Synced"}]},
    }
    app.update(overrides)
    return app


class ChartSwitchTest(unittest.TestCase):
    shell_path = FIRST_SHELL

    def setUp(self):
        handle = tempfile.TemporaryDirectory()
        self.addCleanup(handle.cleanup)
        self.work = Path(handle.name)
        fake = self.work / "k3s"
        fake.write_text(FAKE_K3S % {"python": sys.executable})
        fake.chmod(0o755)

    def run_remote(self, app, mode="switch", site="seoul", **plan):
        (self.work / "app.json").write_text(json.dumps(app))
        (self.work / "plan.json").write_text(json.dumps({"differ": EXPECTED, **plan}))
        script = subprocess.run(["bash", "-c", f'source "{SCRIPT}"; remote_script "$@"', "t", mode, site, "5", "2", "1"],
                                capture_output=True, text=True, check=True).stdout
        result = subprocess.run([self.shell_path, "-c", 'echo $$ > "$FAKE_DIR/pid"\n' + script], capture_output=True, text=True,
                                env={**os.environ, "PATH": f"{self.work}:{os.environ['PATH']}", "FAKE_DIR": str(self.work)})
        self.app = json.loads((self.work / "app.json").read_text())
        calls = self.work / "calls"
        self.patches = [line for line in calls.read_text().splitlines() if line.startswith("patch")] if calls.exists() else []
        return result

    def assert_back_on_kustomize(self, result):
        self.assertEqual(result.returncode, 4, result.stdout + result.stderr)
        self.assertEqual(result.stdout.splitlines()[-1], "ROLLED BACK")
        self.assertEqual(self.app["spec"], application()["spec"])

    def assert_untouched(self, result, text):
        self.assertEqual(result.returncode, 3, result.stdout + result.stderr)
        self.assertIn(text, result.stdout.splitlines()[-1])
        self.assertEqual(self.patches, [])

    def test_switch_leaves_a_clean_helm_source_with_automated_sync_on(self):
        result = self.run_remote(application())
        self.assertEqual((result.returncode, result.stdout.splitlines()[-1]), (0, "SWITCHED"), result.stderr)
        self.assertEqual(self.app["spec"]["source"], {
            "path": "deploy/chart", "targetRevision": "main",
            "helm": {"valueFiles": ["values-seoul.yaml"], "parameters": [{"name": "image.repository", "value": REGISTRY}]}})
        self.assertEqual(self.app["spec"]["syncPolicy"], application()["spec"]["syncPolicy"])

    def test_automated_sync_is_off_before_the_source_changes(self):
        self.run_remote(application())
        first, second = (json.loads(line.split(" ", 1)[1]) for line in self.patches[:2])
        self.assertEqual(first, {"spec": {"syncPolicy": {"automated": None}}})
        self.assertIsNone(second["spec"]["source"]["kustomize"])
        self.assertNotIn("syncPolicy", second["spec"])

    def test_an_unexpected_difference_rolls_back_before_sync_is_on(self):
        result = self.run_remote(application(), differ=[*EXPECTED, ("StatefulSet", "redis-auth")])
        self.assert_back_on_kustomize(result)
        self.assertIn("StatefulSet redis-auth", result.stdout)
        self.assertNotIn("SWITCHED", result.stdout)

    def test_a_namesake_of_an_expected_object_is_unexpected(self):
        self.assert_back_on_kustomize(self.run_remote(application(), differ=[*EXPECTED, ("Deployment", "mysqld-exporter-2")]))

    def test_an_absent_expected_difference_rolls_back(self):
        self.assert_back_on_kustomize(self.run_remote(application(), differ=EXPECTED[1:]))

    def test_a_render_error_rolls_back_without_an_account_id(self):
        result = self.run_remote(application(), conditions=[{"type": "ComparisonError", "message": "bad 123456789012"}])
        self.assert_back_on_kustomize(result)
        self.assertNotIn("123456789012", result.stdout)

    def test_no_comparison_in_time_rolls_back(self):
        self.assert_back_on_kustomize(self.run_remote(application(), never_compared=True))

    def test_an_unknown_comparison_rolls_back(self):
        self.assert_back_on_kustomize(self.run_remote(application(), sync="Unknown"))

    def test_a_refused_source_patch_rolls_back(self):
        self.assert_back_on_kustomize(self.run_remote(application(), refuse='"path": "deploy/chart"'))

    def test_a_sync_that_does_not_settle_fails_and_keeps_the_chart(self):
        result = self.run_remote(application(), stuck=True)
        self.assertEqual(result.returncode, 5)
        self.assertIn("--rollback", result.stdout.splitlines()[-1])
        self.assertEqual(self.app["spec"]["source"]["path"], "deploy/chart")

    def test_a_sync_that_started_before_sync_went_off_ends_before_the_source_changes(self):
        result = self.run_remote(application(), sync_after_off=True)
        self.assertEqual(result.stdout.splitlines()[-1], "SWITCHED", result.stdout + result.stderr)
        self.assertNotIn("applied_chart_early", self.app)

    def test_an_unreadable_operation_is_not_idle(self):
        self.assert_untouched(self.run_remote(application(), unreadable="{.operation}"), "a sync is running")

    def test_a_refused_return_of_sync_rolls_back(self):
        refused = {"spec": {"syncPolicy": {"automated": {"prune": True, "selfHeal": True}}}}
        self.assert_back_on_kustomize(self.run_remote(application(), refuse_exact=refused))

    def test_a_refused_rollback_is_a_failure_that_the_rollback_mode_repairs(self):
        result = self.run_remote(application(), differ=[("StatefulSet", "redis-auth")], refuse='"path": "deploy/overlays/seoul"')
        self.assertEqual(result.returncode, 5, result.stdout)
        self.assertTrue(result.stdout.splitlines()[-1].startswith("FAILED: the rollback did not go through"))
        result = self.run_remote(self.app, mode="rollback")
        self.assertEqual(result.stdout.splitlines()[-1], "ROLLBACK DONE", result.stdout + result.stderr)
        self.assertEqual(self.app["spec"], application()["spec"])

    def test_rollback_repairs_an_overlay_source_whose_sync_stayed_off(self):
        app = application()
        del app["spec"]["syncPolicy"]["automated"]
        result = self.run_remote(app, mode="rollback")
        self.assertEqual(result.stdout.splitlines()[-1], "ROLLBACK DONE", result.stdout + result.stderr)
        self.assertEqual(self.app["spec"], application()["spec"])

    def test_rollback_of_an_untouched_application_changes_nothing(self):
        result = self.run_remote(application(), mode="rollback")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(self.patches, [])

    def test_a_signal_during_the_comparison_rolls_back(self):
        result = self.run_remote(application(), signal_at="comparing")
        self.assert_back_on_kustomize(result)
        self.assertIn("ended early with code 143", result.stdout)

    def test_a_signal_while_the_chart_syncs_is_a_failure_that_keeps_the_chart(self):
        result = self.run_remote(application(), signal_at="syncing", stuck=True)
        self.assertEqual(result.returncode, 5, result.stdout)
        self.assertTrue(result.stdout.splitlines()[-1].startswith("FAILED: ended while the chart was syncing"), result.stdout)
        self.assertEqual(self.app["spec"]["source"]["path"], "deploy/chart")

    def test_rollback_does_not_touch_an_override_of_another_form(self):
        for image in (f"danteplanner-backend={REGISTRY}:abc", "danteplanner-backend=other=x:*", "other=x:*", f"danteplanner-backend={REGISTRY}:abc:*"):
            app = application()
            del app["spec"]["syncPolicy"]["automated"]
            app["spec"]["source"]["kustomize"]["images"] = [image]
            self.assert_untouched(self.run_remote(app, mode="rollback"), "image override")

    def test_the_waits_end_by_the_clock_when_every_read_is_slow(self):
        result = self.run_remote(application(), never_compared=True, slow=3)
        self.assert_back_on_kustomize(result)
        calls = (self.work / "calls").read_text().splitlines()
        self.assertLessEqual(sum(line.startswith("get") for line in calls[calls.index("annotate "):]), 8)

    def test_the_other_site_is_not_touched(self):
        self.assert_untouched(self.run_remote(application(), site="oregon"), "STOPPED: no Application danteplanner-oregon")

    def test_an_unsettled_application_is_not_touched(self):
        app = application()
        app["status"]["health"]["status"] = "Progressing"
        self.assert_untouched(self.run_remote(app), "not Synced and Healthy")

    def test_a_running_sync_is_not_touched(self):
        app = application()
        app["status"]["operationState"]["phase"] = "Running"
        self.assert_untouched(self.run_remote(app), "a sync is running")

    def test_no_status_per_resource_is_not_touched(self):
        app = application()
        app["status"]["resources"] = []
        self.assert_untouched(self.run_remote(app), "no status per resource")

    def test_an_unknown_image_override_is_not_touched(self):
        app = application()
        app["spec"]["source"]["kustomize"]["images"] = ["other=x:*"]
        self.assert_untouched(self.run_remote(app), "image override")

    def test_dry_run_changes_nothing(self):
        result = self.run_remote(application(), mode="dry-run")
        self.assertEqual((result.returncode, result.stdout.splitlines()[-1]), (0, "DRY RUN: ready to switch"))
        self.assertEqual(self.patches, [])

    def test_a_second_switch_changes_nothing(self):
        self.run_remote(application())
        (self.work / "calls").unlink()
        result = self.run_remote(self.app)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(self.patches, [])

    def test_a_chart_source_whose_sync_stayed_off_is_a_failure_that_the_rollback_mode_repairs(self):
        self.run_remote(application())
        (self.work / "calls").unlink()
        del self.app["spec"]["syncPolicy"]["automated"]
        for mode in ("switch", "dry-run"):
            result = self.run_remote(self.app, mode=mode)
            self.assertEqual(result.returncode, 5, result.stdout)
            self.assertIn("--rollback", result.stdout.splitlines()[-1])
            self.assertEqual(self.patches, [])
        result = self.run_remote(self.app, mode="rollback")
        self.assertEqual(result.stdout.splitlines()[-1], "ROLLBACK DONE", result.stdout + result.stderr)
        self.assertEqual(self.app["spec"], application()["spec"])

    def test_rollback_restores_the_exact_spec(self):
        self.run_remote(application())
        result = self.run_remote(self.app, mode="rollback")
        self.assertEqual((result.returncode, result.stdout.splitlines()[-1]), (0, "ROLLBACK DONE"), result.stderr)
        self.assertEqual(self.app["spec"], application()["spec"])


with_second_shell(ChartSwitchTest, globals())

if __name__ == "__main__":
    unittest.main()
