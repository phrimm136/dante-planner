import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
RULE_PATH = REPO_ROOT / ".github" / "scripts" / "rendered-crds.py"


def load_rule():
    rule_spec = importlib.util.spec_from_file_location("rendered_crds", RULE_PATH)
    rule_module = importlib.util.module_from_spec(rule_spec)
    rule_spec.loader.exec_module(rule_module)
    return rule_module


RENDERED_CRDS = load_rule()


def crd_text(group_name, kind_name, served_flags):
    version_lines = "".join(
        f"    - name: {version_name}\n      served: {str(is_served).lower()}\n"
        for version_name, is_served in served_flags.items()
    )
    return f"""---
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: {kind_name.lower()}s.{group_name}
spec:
  group: {group_name}
  names:
    kind: {kind_name}
  versions:
{version_lines}"""


def object_text(api_version, kind_name, name_text="probe"):
    return f"""---
apiVersion: {api_version}
kind: {kind_name}
metadata:
  name: {name_text}
  namespace: danteplanner
"""


EXTERNAL_SECRET = object_text("external-secrets.io/v1beta1", "ExternalSecret", "backend-jwt")
EXTERNAL_SECRET_CRD = crd_text("external-secrets.io", "ExternalSecret", {"v1beta1": True, "v1alpha1": False})
CONFIG_MAP = object_text("v1", "ConfigMap")


class RenderedCrdsTest(unittest.TestCase):
    def run_rule(self, *file_texts, outside=frozenset()):
        with tempfile.TemporaryDirectory() as work_dir:
            file_paths = []
            for file_index, file_text in enumerate(file_texts):
                file_path = Path(work_dir) / f"render-{file_index}.yaml"
                file_path.write_text(file_text, encoding="utf-8")
                file_paths.append(str(file_path))
            rule_output = io.StringIO()
            with contextlib.redirect_stdout(rule_output):
                exit_code = RENDERED_CRDS.main(file_paths, set(outside))
        return exit_code, rule_output.getvalue().splitlines()

    def assert_passes(self, *file_texts, outside=frozenset()):
        self.assertEqual(self.run_rule(*file_texts, outside=outside), (0, []))

    def assert_fails(self, problem_lines, *file_texts, outside=frozenset()):
        self.assertEqual(self.run_rule(*file_texts, outside=outside), (1, problem_lines))

    def test_an_external_secret_with_its_crd_rendered_passes(self):
        self.assert_passes(EXTERNAL_SECRET_CRD + EXTERNAL_SECRET)

    def test_an_external_secret_without_its_crd_names_group_kind_and_object(self):
        self.assert_fails(
            [
                "external-secrets.io/v1beta1 ExternalSecret danteplanner/backend-jwt: "
                "no CRD of group external-secrets.io is rendered, and the group is not in OUTSIDE_GROUPS"
            ],
            EXTERNAL_SECRET,
        )

    def test_crd_and_objects_removed_together_pass(self):
        self.assert_passes(CONFIG_MAP)

    def test_a_listed_group_needs_no_rendered_crd(self):
        self.assert_passes(EXTERNAL_SECRET, outside={"external-secrets.io"})

    def test_a_crd_that_stops_serving_the_objects_version_names_both_versions(self):
        self.assert_fails(
            [
                "external-secrets.io/v1beta1 ExternalSecret danteplanner/backend-jwt: "
                "its rendered CRD does not serve v1beta1 (served: v1)"
            ],
            crd_text("external-secrets.io", "ExternalSecret", {"v1": True, "v1beta1": False}),
            EXTERNAL_SECRET,
        )

    def test_a_version_counts_as_served_only_when_every_render_of_the_crd_serves_it(self):
        exit_code, problem_lines = self.run_rule(
            EXTERNAL_SECRET_CRD,
            crd_text("external-secrets.io", "ExternalSecret", {"v1": True}),
            EXTERNAL_SECRET,
        )
        self.assertEqual(exit_code, 1)
        self.assertIn("does not serve v1beta1 (served: none)", problem_lines[0])

    def test_a_crd_of_another_kind_does_not_cover_the_object(self):
        self.assert_fails(
            [
                "external-secrets.io/v1beta1 ExternalSecret danteplanner/backend-jwt: "
                "CRDs of group external-secrets.io are rendered, but none defines kind ExternalSecret"
            ],
            crd_text("external-secrets.io", "SecretStore", {"v1beta1": True}),
            EXTERNAL_SECRET,
        )

    def test_a_listed_group_whose_crd_is_rendered_fails(self):
        self.assert_fails(
            ["OUTSIDE_GROUPS lists external-secrets.io, but a CRD of that group is rendered; remove the entry"],
            EXTERNAL_SECRET_CRD,
            EXTERNAL_SECRET,
            outside={"external-secrets.io"},
        )

    def test_a_listed_group_that_nothing_uses_fails(self):
        self.assert_fails(
            ["OUTSIDE_GROUPS lists traefik.io, but no rendered object uses that group; remove the entry"],
            CONFIG_MAP,
            outside={"traefik.io"},
        )

    def test_gateway_api_is_not_taken_for_a_built_in_group(self):
        self.assert_fails(
            [
                "gateway.networking.k8s.io/v1 HTTPRoute danteplanner/probe: "
                "no CRD of group gateway.networking.k8s.io is rendered, and the group is not in OUTSIDE_GROUPS"
            ],
            object_text("gateway.networking.k8s.io/v1", "HTTPRoute"),
        )

    def test_built_in_kinds_pass(self):
        self.assert_passes(
            "".join(
                object_text(api_version, kind_name)
                for api_version, kind_name in [
                    ("v1", "Service"),
                    ("apps/v1", "Deployment"),
                    ("batch/v1", "CronJob"),
                    ("autoscaling/v2", "HorizontalPodAutoscaler"),
                    ("policy/v1", "PodDisruptionBudget"),
                    ("rbac.authorization.k8s.io/v1", "ClusterRole"),
                    ("networking.k8s.io/v1", "NetworkPolicy"),
                    ("storage.k8s.io/v1", "StorageClass"),
                    ("scheduling.k8s.io/v1", "PriorityClass"),
                    ("coordination.k8s.io/v1", "Lease"),
                    ("discovery.k8s.io/v1", "EndpointSlice"),
                    ("admissionregistration.k8s.io/v1", "ValidatingWebhookConfiguration"),
                    ("apiregistration.k8s.io/v1", "APIService"),
                ]
            )
        )

    def test_a_rendered_crd_without_objects_passes(self):
        self.assert_passes(EXTERNAL_SECRET_CRD)

    def test_a_crd_in_another_file_covers_the_object_whatever_the_file_order(self):
        self.assert_passes(EXTERNAL_SECRET, EXTERNAL_SECRET_CRD)
        self.assert_passes(EXTERNAL_SECRET_CRD, EXTERNAL_SECRET)

    def test_the_same_objects_in_both_regions_pass(self):
        self.assert_passes(EXTERNAL_SECRET_CRD + EXTERNAL_SECRET, EXTERNAL_SECRET_CRD + EXTERNAL_SECRET)

    def test_problems_do_not_depend_on_file_order_or_repetition(self):
        tls_options = [object_text("traefik.io/v1alpha1", "TLSOption", f"option-{letter}") for letter in "abcdefgh"]
        forward_run = self.run_rule(*tls_options, EXTERNAL_SECRET, EXTERNAL_SECRET)
        self.assertEqual(forward_run, self.run_rule(EXTERNAL_SECRET, *reversed(tls_options)))
        self.assertEqual(
            [problem_line.split(":")[0] for problem_line in forward_run[1]],
            ["external-secrets.io/v1beta1 ExternalSecret danteplanner/backend-jwt"]
            + [f"traefik.io/v1alpha1 TLSOption danteplanner/option-{letter}" for letter in "abcdefgh"],
        )

    def test_objects_inside_a_list_are_read(self):
        self.assert_fails(
            [
                "external-secrets.io/v1beta1 ExternalSecret danteplanner/backend-jwt: "
                "no CRD of group external-secrets.io is rendered, and the group is not in OUTSIDE_GROUPS"
            ],
            """apiVersion: v1
kind: List
items:
  - apiVersion: external-secrets.io/v1beta1
    kind: ExternalSecret
    metadata:
      name: backend-jwt
      namespace: danteplanner
""",
        )

    def test_empty_documents_are_skipped(self):
        self.assert_passes("---\n# Source: empty.yaml\n---\n" + CONFIG_MAP + "---\n")

    def test_no_files_is_a_usage_error(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(RENDERED_CRDS.main([], set()), 2)


if __name__ == "__main__":
    unittest.main()
