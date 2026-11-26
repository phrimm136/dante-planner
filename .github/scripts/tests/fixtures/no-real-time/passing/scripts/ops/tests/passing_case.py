import os
import re
import subprocess
import time
import unittest

FAKE_SLEEP = """#!/bin/sh
advance "$1"
"""

FAKE_LEASE = """#!/bin/sh
printf '%s\\n' "$(date -u -d "@$(( $(cat "$FAKE_DIR/clock") + 5 ))" +%Y-%m-%dT%H:%M:%SZ)"
held_since=$(date -u -r "$LOCK_DIR" +%Y-%m-%dT%H:%M:%SZ)
# sleep 30 in a comment does not count
"""


class PassingCase(unittest.TestCase):
    def test_scripts_time_as_an_input(self):
        tool_names = ("sleep", "date", "timeout")
        launch_text = time.strftime("%Y-%m-%d", time.gmtime(1767225600))
        fake_run = subprocess.run(["true"], timeout=60)
        self.assertEqual(fake_run.returncode, 0, "the date in this message is prose")
        self.assertIn("ssm execution timeout", ["ssm execution timeout"], (tool_names, launch_text))


KNOWN_PATCH = re.compile(r"until kubectl get crd; do sleep 10; done")
BOOT_TEXT = "until kubectl get crd; do sleep 10; done\n"


FAKE_PASSTHROUGH = f"""#!/bin/sh
PATH=${{PATH#*:}}; export PATH; exec date "$@"
"""


def guarded_paths(fake_dir, child_env):
    child_env["PATH"] = ":".join((str(fake_dir), os.environ.get("TOOLING_SLEEP_GUARD_DIR", ""), "/usr/bin"))
    return {**os.environ, "PATH": f"{fake_dir}:{os.environ['PATH']}"}, dict(PATH=f"{fake_dir}:{os.environ['PATH']}")
