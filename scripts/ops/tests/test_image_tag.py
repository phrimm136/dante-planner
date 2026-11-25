import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
DEPLOY_INPUTS = REPO_ROOT / ".github" / "scripts" / "deploy-inputs-changed.sh"
WORKFLOW = yaml.safe_load((REPO_ROOT / ".github" / "workflows" / "deploy-fleet.yml").read_text(encoding="utf-8"))
VALUES_FILES = ("deploy/chart/values-oregon.yaml", "deploy/chart/values-seoul.yaml")
TAG_LINE = re.compile(r'^  tag: "[0-9a-f]{40}"$', re.MULTILINE)
TAG = "0123456789abcdef0123456789abcdef01234567"
OTHER_TAG = "89abcdef0123456789abcdef0123456789abcdef"

FAKE_GH = """#!/bin/sh
for argument in "$@"; do
  case "$argument" in
    repos/*/contents/*)
      path=${argument#*/contents/}
      exec cat "$FAKE_BRANCH/${path%%\\?*}" ;;
  esac
done
exit 1
"""


def values_with(values_file, tag_lines):
    text = (REPO_ROOT / values_file).read_text(encoding="utf-8")
    changed, count = TAG_LINE.subn(lambda _: tag_lines, text)
    if count != 1:
        raise AssertionError(f"{values_file} holds {count} tag lines, want 1")
    return changed


MALFORMED = {
    "no tag line": "  pullPolicy: Always",
    "two tag lines": f'  tag: "{TAG}"\n  tag: "{TAG}"',
    "a second tag line at another depth": f'  tag: "{TAG}"\n    tag: "{OTHER_TAG}"',
    "a word": '  tag: "latest"',
    "39 hex": f'  tag: "{TAG[:39]}"',
    "41 hex": f'  tag: "{TAG}0"',
    "upper case": f'  tag: "{TAG.upper()}"',
    "no quotes": f"  tag: {TAG}",
    "a trailing comment": f'  tag: "{TAG}" # pinned',
    "a carriage return": f'  tag: "{TAG}"\r',
}


def write(root, relative_path, text):
    target = Path(root) / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(text.encode())


def step_script(job, step_name):
    scripts = [step["run"] for step in WORKFLOW["jobs"][job]["steps"] if step.get("name") == step_name]
    if len(scripts) != 1:
        raise AssertionError(f"{job} holds {len(scripts)} steps named {step_name}")
    return scripts[0]


class TempRepoCase(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)

    def git(self, *arguments):
        return subprocess.run(["git", "-C", str(self.root), "-c", "user.name=t", "-c", "user.email=t@example.invalid",
                               *arguments], check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, message):
        self.git("add", "-A")
        self.git("-c", "core.hooksPath=/dev/null", "commit", "-q", "--allow-empty", "-m", message)
        return self.git("rev-parse", "HEAD")


class DeployInputsChangedTest(TempRepoCase):
    def setUp(self):
        super().setUp()
        self.git("init", "-q")

    def run_script(self, verified, event="workflow_run"):
        environment = {key: value for key, value in os.environ.items() if key != "GITHUB_OUTPUT"}
        return subprocess.run([str(DEPLOY_INPUTS), verified, event], cwd=self.root, env=environment,
                              capture_output=True, text=True, check=False)

    def commit_values(self, tag_lines):
        write(self.root, VALUES_FILES[0], values_with(VALUES_FILES[0], tag_lines))
        return self.commit("values")

    def test_a_malformed_tag_fails_and_names_the_file(self):
        for case, tag_lines in MALFORMED.items():
            with self.subTest(case):
                run = self.run_script(self.commit_values(tag_lines))
                self.assertNotEqual(run.returncode, 0, run.stdout)
                self.assertIn(VALUES_FILES[0], run.stderr)
                self.assertNotIn("backend=", run.stdout)

    def test_a_missing_values_file_fails_and_names_the_file(self):
        run = self.run_script(self.commit("empty"))
        self.assertNotEqual(run.returncode, 0, run.stdout)
        self.assertIn(VALUES_FILES[0], run.stderr)
        self.assertNotIn("backend=", run.stdout)

    def test_a_tag_outside_this_history_deploys(self):
        run = self.run_script(self.commit_values(f'  tag: "{TAG}"'))
        self.assertEqual((run.returncode, run.stdout.splitlines()[-1]), (0, "backend=true"), run.stderr)

    def test_a_deployed_commit_deploys_only_when_an_image_input_changed(self):
        write(self.root, "backend/src/Main.java", "first")
        deployed = self.commit("release")
        bump = self.commit_values(f'  tag: "{deployed}"')
        run = self.run_script(bump)
        self.assertEqual((run.returncode, run.stdout.splitlines()[-1]), (0, "backend=false"), run.stderr)
        write(self.root, "backend/src/Main.java", "second")
        run = self.run_script(self.commit("change"))
        self.assertEqual((run.returncode, run.stdout.splitlines()[-1]), (0, "backend=true"), run.stderr)

    def test_a_manual_run_deploys_without_reading(self):
        run = self.run_script(self.commit("empty"), "workflow_dispatch")
        self.assertEqual((run.returncode, run.stdout.splitlines()[-1]), (0, "backend=true"), run.stderr)


class WorkflowStepCase(TempRepoCase):
    def setUp(self):
        super().setUp()
        write(self.root, "bin/gh", FAKE_GH)
        (self.root / "bin" / "gh").chmod(0o755)
        for values_file in VALUES_FILES:
            write(self.root, values_file, values_with(values_file, f'  tag: "{OTHER_TAG}"'))

    def run_step(self, job, step_name):
        self.output_file = self.root / "output"
        self.output_file.write_text("")
        environment = {**os.environ, "PATH": f"{self.root}/bin:{os.environ['PATH']}", "FAKE_BRANCH": str(self.root),
                       "VALUES_FILES": WORKFLOW["env"]["VALUES_FILES"], "GIT_SHA": TAG, "REPO": "o/r",
                       "REF_NAME": "main", "GITHUB_OUTPUT": str(self.output_file)}
        return subprocess.run(["bash", "-e", "-c", step_script(job, step_name)], cwd=self.root, env=environment,
                              capture_output=True, text=True, check=False)


class LearnBumpTest(WorkflowStepCase):
    def learn(self):
        return self.run_step("settle-down", "Learn whether the bump is on the branch")

    def test_a_malformed_tag_in_either_file_fails_the_step(self):
        for values_file in VALUES_FILES:
            for case, tag_lines in MALFORMED.items():
                with self.subTest(case, values_file=values_file):
                    write(self.root, values_file, values_with(values_file, tag_lines))
                    run = self.learn()
                    self.assertNotEqual(run.returncode, 0, run.stdout)
                    self.assertIn(values_file, run.stdout)
                    self.assertEqual(self.output_file.read_text(), "")
            write(self.root, values_file, values_with(values_file, f'  tag: "{OTHER_TAG}"'))

    def test_a_file_that_cannot_be_read_fails_the_step(self):
        (self.root / VALUES_FILES[1]).unlink()
        run = self.learn()
        self.assertNotEqual(run.returncode, 0, run.stdout)
        self.assertEqual(self.output_file.read_text(), "")

    def test_landed_only_when_both_files_carry_the_tag(self):
        for oregon_tag, seoul_tag, landed in ((OTHER_TAG, OTHER_TAG, "false"), (TAG, OTHER_TAG, "false"),
                                              (OTHER_TAG, TAG, "false"), (TAG, TAG, "true")):
            with self.subTest(landed=landed, oregon=oregon_tag, seoul=seoul_tag):
                for values_file, tag in zip(VALUES_FILES, (oregon_tag, seoul_tag)):
                    write(self.root, values_file, values_with(values_file, f'  tag: "{tag}"'))
                run = self.learn()
                self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertEqual(self.output_file.read_text(), f"landed={landed}\n")


class BumpTest(WorkflowStepCase):
    def bump(self):
        return self.run_step("bump-tag", "Bump image tag in BOTH values files")

    def test_the_bump_changes_only_the_tag_line_of_both_files(self):
        run = self.bump()
        self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
        for values_file in VALUES_FILES:
            self.assertEqual((self.root / values_file).read_text(encoding="utf-8"),
                             values_with(values_file, f'  tag: "{TAG}"'))

    def test_a_file_without_exactly_one_tag_line_fails_the_bump(self):
        for case in ("no tag line", "two tag lines", "a second tag line at another depth", "no quotes"):
            with self.subTest(case):
                write(self.root, VALUES_FILES[1], values_with(VALUES_FILES[1], MALFORMED[case].replace(TAG, OTHER_TAG)))
                run = self.bump()
                self.assertNotEqual(run.returncode, 0, run.stdout)
                self.assertIn(VALUES_FILES[1], run.stdout)

    def test_the_bump_installs_nothing_and_the_commit_adds_both_files(self):
        steps = [step.get("name", step.get("uses", "")) for step in WORKFLOW["jobs"]["bump-tag"]["steps"]]
        self.assertEqual(steps[1:], ["Bump image tag in BOTH values files", "Commit the tag bump"])
        self.assertEqual(WORKFLOW["env"]["VALUES_FILES"].split(), list(VALUES_FILES))
        self.assertIn("git add $VALUES_FILES\n", step_script("bump-tag", "Commit the tag bump"))


if __name__ == "__main__":
    unittest.main()
