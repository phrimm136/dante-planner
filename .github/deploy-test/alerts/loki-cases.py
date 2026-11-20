#!/usr/bin/env python3
"""Push each Loki case's lines to a Loki instance and check its instant query: fires wants a sample > 0, quiet wants none."""

import json
import sys
import time
import urllib.parse
import urllib.request

SLOT_SECONDS = 600


def push_lines(loki_url, case_lines, slot_ns):
    push_doc = {"streams": [
        {"stream": line_entry["stream"], "values": [[str(slot_ns + line_index), line_entry["line"]]]}
        for line_index, line_entry in enumerate(case_lines)
    ]}
    push_request = urllib.request.Request(
        f"{loki_url}/loki/api/v1/push", data=json.dumps(push_doc).encode(),
        headers={"Content-Type": "application/json"}, method="POST",
    )
    with urllib.request.urlopen(push_request, timeout=10) as push_response:
        if push_response.status != 204:
            raise RuntimeError(f"push answered HTTP {push_response.status}")


def instant_query(loki_url, query_text, query_seconds):
    query_string = urllib.parse.urlencode({"query": query_text, "time": str(query_seconds)})
    with urllib.request.urlopen(f"{loki_url}/loki/api/v1/query?{query_string}", timeout=10) as query_response:
        query_doc = json.loads(query_response.read())
    if query_doc.get("status") != "success" or query_doc["data"]["resultType"] != "vector":
        raise RuntimeError(f"query did not return a vector: {query_doc}")
    return query_doc["data"]["result"]


def main():
    loki_url = sys.argv[1].rstrip("/")
    loki_cases = [json.loads(case_text) for case_text in sys.stdin if case_text.strip()]
    first_slot = int(time.time()) - (len(loki_cases) + 1) * SLOT_SECONDS
    failed_cases = []
    for case_number, loki_case in enumerate(loki_cases):
        case_label = f"{loki_case['rule']} {loki_case['case']}[{loki_case['index']}]"
        slot_seconds = first_slot + case_number * SLOT_SECONDS
        push_lines(loki_url, loki_case["lines"], slot_seconds * 1_000_000_000)
        query_result = instant_query(loki_url, loki_case["expr"], slot_seconds + 1)
        positive_samples = [sample_entry for sample_entry in query_result if float(sample_entry["value"][1]) > 0]
        case_passed = bool(positive_samples) if loki_case["case"] == "fires" else not positive_samples
        if case_passed:
            print(f"  ok {case_label}")
        else:
            print(f"  FAILED {case_label}: query returned {json.dumps(query_result)}")
            failed_cases.append(case_label)
    print(f"{len(loki_cases) - len(failed_cases)} of {len(loki_cases)} Loki cases passed")
    return 1 if failed_cases or not loki_cases else 0


if __name__ == "__main__":
    sys.exit(main())
