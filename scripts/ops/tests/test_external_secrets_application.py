import json
import re
import shlex
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
TERRAFORM_DIR = REPO_ROOT / "terraform"
APPLICATION_PATH = "deploy/chart/templates/external-secrets.yaml"
MODULE_PATH = "terraform/modules/fleet/variables.tf"
BOOT_PATH = "terraform/modules/fleet/user-data/cp.sh.tftpl"
ANNOTATE_PATH = "terraform/modules/fleet/user-data/scrape-annotate-install.sh"
VARIABLE_NAME = "external_secrets_chart_version"
DEFAULT_PATTERN = re.compile(rf'^variable "{VARIABLE_NAME}" \{{\n(?:[^}}]*\n)?\s*default\s*=\s*"([^"]+)"$', re.MULTILINE)
ASSIGNMENT_PATTERN = re.compile(rf"^[ \t]*{VARIABLE_NAME}[ \t]*=[ \t]*(\S+)", re.MULTILINE)
OTHER_VERSION = "0.0.1"
COMMAND_PATTERN = re.compile(r"^.*\bhelm upgrade .*\bexternal-secrets(?:.*\\\n)*.*$", re.MULTILINE)
PATCH_PATTERN = re.compile(r"^.*-n external-secrets .*$", re.MULTILINE)
KNOWN_PATCH = re.compile(
    r"until \$KUBECTL -n external-secrets patch deployment external-secrets --type merge -p '([^']*)'; do sleep 10; done")
FLEET_SOURCE_PATTERN = re.compile(r'^\s*source\s*=\s*"\.\./modules/fleet"$', re.MULTILINE)


def fleet_stacks():
    return sorted(main_file.parent for main_file in TERRAFORM_DIR.glob("*/main.tf")
                  if FLEET_SOURCE_PATTERN.search(main_file.read_text(encoding="utf-8")))


def read_texts():
    file_paths = [REPO_ROOT / APPLICATION_PATH, REPO_ROOT / MODULE_PATH]
    for stack_dir in fleet_stacks():
        file_paths += [*stack_dir.glob("*.tf"), *stack_dir.glob("envs/*.tfvars")]
    return {str(file_path.relative_to(REPO_ROOT)): file_path.read_text(encoding="utf-8") for file_path in file_paths}


def application_version(file_texts):
    return yaml.safe_load(file_texts[APPLICATION_PATH])["spec"]["source"]["targetRevision"]


def stack_of(file_path):
    parent = Path(file_path).parent
    return str(parent.parent if parent.name == "envs" else parent)


def version_problems(file_texts):
    chart_version = application_version(file_texts)
    stack_paths = {stack_of(file_path) for file_path in file_texts} - {str(Path(APPLICATION_PATH).parent)}
    problem_texts = []
    for stack_path in sorted(stack_paths):
        defaults = DEFAULT_PATTERN.findall(file_texts[f"{stack_path}/variables.tf"])
        if defaults != [chart_version]:
            problem_texts.append(f"{stack_path}/variables.tf defaults {VARIABLE_NAME} to {defaults}, "
                                 f"{APPLICATION_PATH} installs {chart_version}")
        if stack_path == str(Path(MODULE_PATH).parent):
            continue
        assignments = {file_path: ASSIGNMENT_PATTERN.findall(file_text)
                       for file_path, file_text in file_texts.items()
                       if stack_of(file_path) == stack_path}
        expected_assignments = {file_path: [f"var.{VARIABLE_NAME}"] if file_path == f"{stack_path}/main.tf" else []
                                for file_path in assignments}
        problem_texts += [f"{file_path} sets {VARIABLE_NAME} to {found}, want {expected_assignments[file_path]}"
                          for file_path, found in sorted(assignments.items())
                          if found != expected_assignments[file_path]]
    return problem_texts


def boot_install(boot_text):
    (command_text,) = COMMAND_PATTERN.findall(boot_text)
    words = shlex.split(command_text.replace("\\\n", " "))
    fixed_words, set_words = [], []
    while words:
        word = words.pop(0)
        if word == "--set":
            set_words.append(words.pop(0))
        elif word != "retry":
            fixed_words.append(word)
    return fixed_words, set_words


def installed_values(boot_text, annotate_text):
    (patch_line,) = PATCH_PATTERN.findall(annotate_text)
    patch = json.loads(KNOWN_PATCH.fullmatch(patch_line).group(1))
    annotations = patch["spec"]["template"]["metadata"].pop("annotations")
    if patch != {"spec": {"template": {"metadata": {}}}}:
        raise ValueError(f"the converger patches more than annotations: {patch}")
    chart_values = {"podAnnotations": annotations}
    for set_word in boot_install(boot_text)[1]:
        key_path, value_text = set_word.split("=", 1)
        *parent_keys, last_key = key_path.split(".")
        target = chart_values
        for parent_key in parent_keys:
            target = target.setdefault(parent_key, {})
        target[last_key] = yaml.safe_load(value_text)
    return chart_values


class ExternalSecretsApplicationTest(unittest.TestCase):
    def setUp(self):
        self.file_texts = read_texts()
        self.version = application_version(self.file_texts)

    def problems_after(self, file_path, old_text, new_text):
        self.assertIn(old_text, self.file_texts[file_path])
        return version_problems({**self.file_texts,
                                 file_path: self.file_texts[file_path].replace(old_text, new_text)})

    def test_the_application_installs_the_version_the_boot_script_installs(self):
        self.assertEqual([stack_dir.name for stack_dir in fleet_stacks()], ["oregon", "seoul"])
        self.assertEqual(version_problems(self.file_texts), [])

    def test_the_boot_script_installs_the_version_of_the_module_variable(self):
        cp_text = (TERRAFORM_DIR / "modules/fleet/cp.tf").read_text(encoding="utf-8")
        boot_text = (TERRAFORM_DIR / "modules/fleet/user-data/cp.sh.tftpl").read_text(encoding="utf-8")
        self.assertRegex(cp_text, rf"(?m)^\s*eso_chart_version\s*=\s*var\.{VARIABLE_NAME}$")
        self.assertIn("  --version ${eso_chart_version} \\\n", boot_text)

    def test_the_application_carries_the_boot_flags_and_the_scrape_annotations(self):
        application = yaml.safe_load(self.file_texts[APPLICATION_PATH])
        boot_text, annotate_text = ((REPO_ROOT / file_path).read_text(encoding="utf-8")
                                    for file_path in (BOOT_PATH, ANNOTATE_PATH))
        source = application["spec"]["source"]
        self.assertEqual(boot_install(boot_text)[0], [
            "helm", "upgrade", "--install", source["helm"]["releaseName"], f"{source['chart']}/{source['chart']}",
            "--namespace", application["spec"]["destination"]["namespace"], "--create-namespace",
            "--version", "${eso_chart_version}"])
        self.assertEqual(source["helm"]["valuesObject"], installed_values(boot_text, annotate_text))

    def test_a_flag_or_patch_of_another_shape_fails(self):
        boot_text, annotate_text = ((REPO_ROOT / file_path).read_text(encoding="utf-8")
                                    for file_path in (BOOT_PATH, ANNOTATE_PATH))
        known_install, known_values = boot_install(boot_text)[0], installed_values(boot_text, annotate_text)
        flag_line = "  --set installCRDs=true \\\n"
        self.assertIn(flag_line, boot_text)
        for added_line in ("  --set-string webhook.podAnnotations.x=y \\\n", "  -f values.yaml \\\n"):
            with self.subTest(added_line):
                self.assertNotEqual(boot_install(boot_text.replace(flag_line, flag_line + added_line))[0], known_install)
        for old_text, new_text in ((flag_line, flag_line + "    --set webhook.replicaCount=2 \\\n"),
                                   ("--set nodeSelector.role=app", "--set nodeSelector.role=data")):
            with self.subTest(new_text):
                self.assertNotEqual(installed_values(boot_text.replace(old_text, new_text), annotate_text), known_values)
        self.assertNotEqual(
            boot_install(boot_text.replace("--namespace external-secrets", "--namespace other"))[0], known_install)
        self.assertNotEqual(installed_values(boot_text, annotate_text.replace("8080", "8081")), known_values)
        second_patch = "until $KUBECTL -n external-secrets patch deployment external-secrets-webhook -p '{}'; do sleep 10; done\n"
        with self.assertRaises(ValueError):
            installed_values(boot_text, annotate_text + second_patch)

    def test_a_raised_application_fails_against_every_default(self):
        problem_texts = self.problems_after(APPLICATION_PATH, f"targetRevision: {self.version}",
                                            f"targetRevision: {OTHER_VERSION}")
        self.assertEqual([problem_text.split()[0] for problem_text in problem_texts],
                         [MODULE_PATH, "terraform/oregon/variables.tf", "terraform/seoul/variables.tf"])

    def test_a_raised_default_fails(self):
        for stack_path in ("terraform/modules/fleet", "terraform/oregon", "terraform/seoul"):
            with self.subTest(stack_path):
                (problem_text,) = self.problems_after(f"{stack_path}/variables.tf", f'"{self.version}"',
                                                      f'"{OTHER_VERSION}"')
                self.assertIn(f"{stack_path}/variables.tf defaults {VARIABLE_NAME} to ['{OTHER_VERSION}']", problem_text)

    def test_a_stack_that_sets_another_version_fails(self):
        for stack_path in ("terraform/oregon", "terraform/seoul"):
            with self.subTest(stack_path):
                (problem_text,) = self.problems_after(f"{stack_path}/main.tf", f"var.{VARIABLE_NAME}",
                                                      f'"{OTHER_VERSION}"')
                self.assertIn(f"{stack_path}/main.tf sets {VARIABLE_NAME}", problem_text)
                settings_path = f"{stack_path}/envs/prod.tfvars"
                edited_texts = {**self.file_texts,
                                settings_path: self.file_texts[settings_path] + f'\n{VARIABLE_NAME} = "{OTHER_VERSION}"\n'}
                self.assertEqual(version_problems(edited_texts),
                                 [f"{settings_path} sets {VARIABLE_NAME} to ['\"{OTHER_VERSION}\"'], want []"])


if __name__ == "__main__":
    unittest.main()
