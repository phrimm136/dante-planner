import os
import shutil
import unittest

FIRST_SHELL = "/bin/sh"


def second_shell():
    first_path = os.path.realpath(FIRST_SHELL)
    for shell_name in ("dash", "bash"):
        shell_path = shutil.which(shell_name)
        if shell_path and os.path.realpath(shell_path) != first_path:
            return shell_path
    return ""


def with_second_shell(test_class, module_globals):
    shell_path = second_shell()
    class_name = f"{test_class.__name__}SecondShell"
    if shell_path:
        module_globals[class_name] = type(class_name, (test_class,), {"shell_path": shell_path,
                                                                      "__module__": module_globals["__name__"]})
        return

    @unittest.skip(f"no second POSIX shell (dash, or bash when {FIRST_SHELL} is dash) on PATH: "
                   f"{test_class.__name__} ran under {FIRST_SHELL} only")
    class SkippedSecondShell(unittest.TestCase):
        def test_second_shell(self):
            pass

    module_globals[class_name] = type(class_name, (SkippedSecondShell,), {"__module__": module_globals["__name__"]})
