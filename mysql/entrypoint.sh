#!/bin/bash
set -e

printf '[client]\nuser=%s\npassword=%s\nhost=localhost\n' \
  "${MYSQL_USER}" "${MYSQL_PASSWORD}" > /root/.my.cnf
chmod 600 /root/.my.cnf

exec docker-entrypoint.sh "$@"
