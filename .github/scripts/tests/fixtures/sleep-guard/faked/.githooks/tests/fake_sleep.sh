#!/bin/sh
fake_bin=$(mktemp -d)
printf '#!/bin/sh\nexit 0\n' >"$fake_bin/sleep"
chmod +x "$fake_bin/sleep"
PATH="$fake_bin:$PATH" sh -c 'sleep 1'
sleep_status=$?
rm -r "$fake_bin"
exit "$sleep_status"
