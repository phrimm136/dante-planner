#!/bin/bash

input=$(cat)

tool_name=$(echo "$input" | jq -r '.tool_name // empty')
if [[ "$tool_name" != "Bash" ]]; then
    exit 0
fi

command=$(echo "$input" | jq -r '.tool_input.command // empty')
if [[ "$command" != *git* ]]; then
    exit 0
fi

trunk="${TRUNK_GUARD_BRANCH:-main}"
project_dir="${CLAUDE_PROJECT_DIR:-$(dirname "$(dirname "$(dirname "$(realpath "$0")")")")}"
project_common_dir=$(git -C "$project_dir" rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || exit 0
base_dir=$(echo "$input" | jq -r '.cwd // empty')
base_dir="${base_dir:-$PWD}"

dirs=$(printf '%s' "$command" | perl -0777 -ne '
    s/<<-?\x27?(\w+)\x27?.*?\n\1//gs;
    s/(\s-C\s+)\x27([^\x27]*)\x27/$1$2/g;
    s/(\s-C\s+)\x22([^\x22]*)\x22/$1$2/g;
    s/\x27[^\x27]*\x27//g;
    s/\x22[^\x22]*\x22//g;
    while (/(?<![\w.\/-])git((?:\s+(?:-[Cc]\s+\S+|-\S+))*)\s+commit\b/g) {
        my $opts = $1;
        my $dir = ".";
        while ($opts =~ /(?:^|\s)-C\s+(\S+)/g) {
            my $path = $1;
            $dir = ($path =~ m{^/}) ? $path : "$dir/$path";
        }
        print "$dir\n";
    }
')

while IFS= read -r dir; do
    [[ -n "$dir" ]] || continue
    git_dir=$(git -C "$base_dir" -C "$dir" rev-parse --path-format=absolute --git-dir 2>/dev/null) || continue
    common_dir=$(git -C "$base_dir" -C "$dir" rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || continue
    [[ "$common_dir" == "$project_common_dir" ]] || continue
    branch=$(git -C "$base_dir" -C "$dir" rev-parse --abbrev-ref HEAD 2>/dev/null)
    if [[ "$git_dir" == "$common_dir" ]]; then
        echo "commit-on-trunk-guard: the main checkout never receives commits; create a worktree from origin/main (git worktree add <path> -b <branch> origin/main) and commit there" >&2
        exit 2
    fi
    if [[ "$branch" == "$trunk" ]]; then
        echo "commit-on-trunk-guard: $trunk is the trunk; commits land through a pull request from a short-lived branch" >&2
        exit 2
    fi
done <<< "$dirs"

exit 0
