# shellcheck shell=bash
# ERR-trap installer and the deploy stages' exit codes.

EXIT_PREREQUISITES=10
EXIT_ENV_SETUP=20
EXIT_CONTAINER_DEPLOY=30
EXIT_CRON_SETUP=40
EXIT_SHELL_SETUP=50

# install_err_trap [exit_code]
# Reports the failing line on stderr. With an exit code the shell terminates
# with it; without one the original status propagates through `set -e`.
install_err_trap() {
    local name
    name="$(basename "$0")"
    local report="echo \"[ERROR] ${name} failed at line \$LINENO (exit code: \$?)\" >&2"
    if [ $# -gt 0 ]; then
        trap "${report}; exit $1" ERR
    else
        trap "${report}" ERR
    fi
}
