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
PR_GATE = yaml.safe_load((REPO_ROOT / ".github" / "workflows" / "pr-gate.yml").read_text(encoding="utf-8"))
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


class ReleaseJarTest(unittest.TestCase):
    def test_the_release_image_takes_the_runner_built_jar(self):
        job = WORKFLOW["jobs"]["build-push"]
        self.assertIn("build-jar", job["needs"])
        builds = [step["with"] for step in job["steps"]
                  if step.get("uses", "").startswith("docker/build-push-action@")]
        self.assertEqual(len(builds), 1)
        self.assertIn("JAR_STAGE=jar-prebuilt", builds[0].get("build-args", "").splitlines())
        self.assertIn("backend-jar=./backend", builds[0]["build-contexts"].splitlines())

    def test_the_job_that_runs_gradle_holds_no_credentials(self):
        gradle_jobs = [name for name, job in WORKFLOW["jobs"].items() if "gradlew" in yaml.safe_dump(job)]
        self.assertEqual(gradle_jobs, ["build-jar"])
        job = WORKFLOW["jobs"]["build-jar"]
        self.assertEqual(job["permissions"], {"contents": "read"})
        self.assertNotIn("environment", job)
        self.assertNotIn("secrets.", yaml.safe_dump(job))
        self.assertNotIn("secrets.", yaml.safe_dump(WORKFLOW["env"]))

    def test_the_jar_and_the_image_tag_carry_the_same_verified_commit(self):
        verified = "${{ github.event.workflow_run.head_sha || github.sha }}"
        refs = [step["with"].get("ref") for job in ("build-jar", "build-push")
                for step in WORKFLOW["jobs"][job]["steps"] if step.get("uses", "").startswith("actions/checkout@")]
        self.assertEqual(refs, [verified, verified])
        gradle_step, = [step for step in WORKFLOW["jobs"]["build-jar"]["steps"] if "gradlew -p" in step.get("run", "")]
        self.assertEqual(gradle_step["env"], {"GIT_SHA": verified})
        self.assertIn('"-PgitSha=$GIT_SHA"', gradle_step["run"])
        image, = [step["with"] for step in WORKFLOW["jobs"]["build-push"]["steps"]
                  if step.get("uses", "").startswith("docker/build-push-action@")]
        self.assertEqual([tag.split(":", 1)[1] for tag in image["tags"].split("\n") if tag], [verified, verified])

    def test_the_jar_job_restores_the_test_backend_gradle_cache_and_saves_none(self):
        def restores(workflow, job):
            return [step["with"] for step in workflow["jobs"][job]["steps"]
                    if step.get("uses", "").startswith("actions/cache/restore@")]

        saved_by, = restores(PR_GATE, "test-backend")
        restored, = restores(WORKFLOW, "build-jar")
        for field in ("key", "restore-keys"):
            expected = saved_by[field].replace("${{ github.workflow }}", PR_GATE["name"])
            expected = expected.replace("${{ github.job }}", "test-backend")
            self.assertEqual(restored[field], expected)
        self.assertEqual(restored["path"], saved_by["path"])
        saves = [step for step in PR_GATE["jobs"]["test-backend"]["steps"]
                 if step.get("uses", "").startswith("actions/cache/save@")]
        self.assertEqual([step["with"]["path"] for step in saves], [restored["path"]])
        self.assertNotRegex(yaml.safe_dump(WORKFLOW["jobs"]["build-jar"]), r"actions/cache(/save)?@")

    def test_every_file_the_runner_jar_build_reads_is_a_deploy_input(self):
        inputs = subprocess.run([str(DEPLOY_INPUTS), "--list-inputs"], check=True, capture_output=True,
                                text=True).stdout.splitlines()
        for path in ("backend/gradlew", "backend/gradle", "backend/lombok.config"):
            with self.subTest(path):
                self.assertIn(path, inputs)


if __name__ == "__main__":
    unittest.main()
