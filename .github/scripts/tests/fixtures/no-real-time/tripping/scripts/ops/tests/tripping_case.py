import datetime
import time
import unittest
from time import monotonic

FAKE_TOOL = """#!/bin/sh
sleep 0.9
exec sleep "$FAKE_WAIT"
printf '%s\\n' "$(date -u -d '+5 seconds' +%Y-%m-%dT%H:%M:%SZ)"
run_stamp=$(date)
"""


class TrippingCase(unittest.TestCase):
    def test_waits_on_the_host_clock(self):
        started_at = time.monotonic()
        time.sleep(1)
        the_moment = datetime.datetime.now()
        self.assertLess(time.monotonic() - started_at, 15)
        assert elapsed_seconds < 3


FAKE_MODE = "mode"
FAKE_K3S = """#!/bin/sh
sleep 5
""" + FAKE_MODE + """
cat <<'BODY'
sleep 3
BODY
"""


def write_fake_sleep(fake_dir):
    (fake_dir / "sleep").write_text("#!/bin/sh\nsleep 2\n")


FAKE_NARROW = """#!/bin/sh
PATH=/usr/bin:/bin; export PATH
"""


def narrow_paths(fake_dir, child_env):
    child_env["PATH"] = f"{fake_dir}:/usr/bin:/bin"
    return {"PATH": str(fake_dir), "HOME": "/tmp"}, dict(PATH=fake_dir)
