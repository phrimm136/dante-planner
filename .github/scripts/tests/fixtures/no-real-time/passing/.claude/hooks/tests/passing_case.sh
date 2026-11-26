#!/bin/sh
# sleep 5
printf '%s\n' "the run sleeps in no test"
epoch_text=$(date -u -d @1767225600 +%Y)
PATH="/opt/fake:$PATH" true
