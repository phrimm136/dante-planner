import importlib.util
import re
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-remote-write-keep-list.py"
SCRIPT_SPEC = importlib.util.spec_from_file_location("keep_list", SCRIPT_PATH)
keep_list = importlib.util.module_from_spec(SCRIPT_SPEC)
SCRIPT_SPEC.loader.exec_module(keep_list)
rules_module = keep_list.rules_loader()
DROP_RULES = [("uri", re.compile("http_server_requests_seconds_bucket"), re.compile("/actuator/.*"))]


class DroppedSelectionTest(unittest.TestCase):
    def selections_of(self, expr_text):
        return keep_list.dropped_selections(keep_list.expr_selectors(rules_module, expr_text), DROP_RULES)

    def test_a_selector_whose_every_value_is_dropped_is_reported(self):
        self.assertEqual(self.selections_of(
            'sum(rate(http_server_requests_seconds_bucket{uri=~"/actuator/health|/actuator/info"}[5m]))'),
            [("http_server_requests_seconds_bucket", "uri", "=~", "/actuator/health|/actuator/info")])

    def test_a_selector_that_keeps_one_value_or_another_metric_is_not(self):
        self.assertEqual(self.selections_of('http_server_requests_seconds_bucket{uri=~"/actuator/health|/api"}'), [])
        self.assertEqual(self.selections_of('http_server_requests_seconds_count{uri="/actuator/health"}'), [])
        self.assertEqual(self.selections_of('http_server_requests_seconds_bucket{uri=~"/actuator/.+"}'), [])

    def test_an_expression_it_cannot_read_stops_the_check(self):
        with self.assertRaisesRegex(SystemExit, "cannot read the metrics of .*a selector without a metric name"):
            keep_list.expr_selectors(rules_module, '{__name__="up"}')

    def test_every_committed_rule_expression_loads(self):
        self.assertTrue(keep_list.exprs_from_rules(rules_module))


if __name__ == "__main__":
    unittest.main()
