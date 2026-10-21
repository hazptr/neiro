#!/usr/bin/env bash
# Merge a Mihon release (or branch) into the current Neiro branch.
#
#   scripts/sync-mihon.sh v0.22.0      # a Mihon tag
#   scripts/sync-mihon.sh main         # Mihon's main branch
#
# Merging rather than rebasing keeps Neiro's history and release tags intact, and each Mihon update's
# conflicts are resolved once. See PORTING.md for what to check afterwards.
set -euo pipefail

ref="${1:?usage: scripts/sync-mihon.sh <mihon tag or branch>}"
cd "$(git rev-parse --show-toplevel)"

if [ -n "$(git status --porcelain)" ]; then
    echo "Working tree is not clean; commit or stash first." >&2
    exit 1
fi

git remote get-url mihon >/dev/null 2>&1 || git remote add mihon https://github.com/mihonapp/mihon.git
git fetch --quiet --tags mihon
target="mihon/$ref"
git rev-parse --verify --quiet "$target" >/dev/null || target="$ref"

mihon_version() { sed -n 's/^ *versionName = "\([0-9.]*\)"$/\1/p' app/build.gradle.kts | head -1; }
before="$(mihon_version)"

if ! git merge --no-ff --no-edit -m "Merge Mihon $ref" "$target"; then
    echo
    echo "Conflicts to resolve:"
    git diff --name-only --diff-filter=U | sed 's/^/  /'
    echo
    echo "Resolve them keeping Neiro's hooks (see PORTING.md), then: git add -A && git commit --no-edit"
    exit 1
fi

after="$(mihon_version)"
if [ "$before" != "$after" ]; then
    # A new Mihon version: the Neiro release on top of it starts again at 1
    sed -i 's/^release=.*/release=1/' neiro.properties
    git commit --quiet -m "Neiro release 1 on Mihon $after" neiro.properties
    echo "Mihon $before -> $after: neiro.properties reset to release=1"
fi

echo "Merged Mihon $ref. Next: build, run the tests and the device checks in PORTING.md."
