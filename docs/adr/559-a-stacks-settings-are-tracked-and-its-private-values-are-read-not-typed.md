# 559 a-stacks-settings-are-tracked-and-its-private-values-are-read-not-typed
epic: none · pr: none

## Decisions
- @terraform @inputs — A stack's inputs come from three places and none of them is a private file: settings that are not private live in a tracked file per environment inside the stack, the account id comes from the profile the plan runs under, and a private value is read by Terraform from the account's parameter store. Every input used to sit in one gitignored var-file per stack and environment. That file existed on one laptop, the staging copies lived as whole-file blobs in the parameter store, and the scheduled drift check needed five repository secrets that were never created, so it never ran. A setting such as the engine version or the revision the cluster syncs changed with no diff and no review. Re-check: `git ls-files 'terraform/*/envs/*.tfvars'` lists a file for every moved stack.
  REJECTED: splitting each var-file into a tracked half and an ignored half — the ignored half is still a file one laptop holds and CI needs as a secret, which is the part that failed.
  REJECTED: keeping the account id in the tracked file — the repository is public.
  REJECTED: the secrets store for every private value — it bills per secret, and most of these values (zone ids, hostnames, the ids of resources in the account) are private, not secret; a value that already lives in a secrets bundle stays there.
  REJECTED: passing private values as environment variables set by the CLI — CI and the laptop would each need their own copy of the lookup, and a plan made by hand would see none of them.
  INVARIANT: a moved stack has one source per variable — the CLI refuses any second var-file, and refuses a command-line value for a variable the settings file assigns or for the account id; tests cover each spelling.
  INVARIANT: the account a moved stack plans against is the account of the selected profile — the CLI passes that profile's account id and no other source can set it, so the provider's allowed-account guard catches only a profile entry that names the wrong account.
  INVARIANT: a tracked settings file holds no account id, ARN, resource id or hostname — a test reads every tracked settings file for those shapes; a private value of another shape is a reviewer's to catch. An address range is not such a shape: the fleet's own private ranges and the edge provider's published ranges are already in the repository.
  NON-GOAL: a stack that is not yet moved — it keeps its gitignored var-file until it gains a settings directory, and the CLI tells the two kinds apart by that directory; the old path is removed when the last stack moves.
  NON-GOAL: a per-run value such as "apply now" — it stays a command-line value, because a tracked permanent setting would turn every later change into an unscheduled one.

## Takeaway
- takeaway: a file that mixes private values with behavior hides the behavior; split by who may read the value, and let the tool that needs a private value fetch it, so no person carries it between machines.
