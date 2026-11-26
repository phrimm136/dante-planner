#!/bin/sh
sleep 1
start_stamp=$(date +%s)
[ "$SECONDS" -lt 5 ]
PATH="/opt/fake:/usr/bin" true
