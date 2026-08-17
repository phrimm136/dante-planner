#!/usr/bin/env bash
# Emits the staging endpoints and the Cloudflare Access service token as shell exports, read from
# Secrets Manager so neither the hostnames nor the credential live in this public repository.
#
#   eval "$(scripts/ops/access/staging-env.sh)"
#   yarn --cwd e2e playwright test tests/read-your-writes.spec.ts
#
# Without these the suites address localhost (e2e/src/staging.ts), so forgetting this step runs
# the local stack rather than silently pointing at nothing.
set -euo pipefail

region=${AWS_REGION:-us-west-2}
name=danteplanner/staging/e2e-endpoints

if ! bundle=$(aws secretsmanager get-secret-value --region "$region" --secret-id "$name" \
    --query SecretString --output text 2>/dev/null); then
  echo "cannot read $name — check the profile and that staging-urls-secret.sh has run" >&2
  exit 1
fi

# printf %q so a secret containing shell metacharacters cannot execute when the caller evals this.
printf %s "$bundle" | python3 -c '
import json, shlex, sys
for key, value in json.load(sys.stdin).items():
    print(f"export {key}={shlex.quote(str(value))}")
'
