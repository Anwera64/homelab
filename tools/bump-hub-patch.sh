#!/usr/bin/env bash
# ==============================================================================
# Raises the hub's patch version by one (0.5.0 -> 0.5.1) when the working tree
# changes the hub's Python packages. Renovate runs it on its security PRs
# (renovate.json, vulnerabilityAlerts), after the fix and before the commit, so
# merging the fix releases it (release-tags.yml) and it can reach prod.
#
# Renovate's security settings apply to every alert, the client's too: a fix
# that leaves the hub's packages alone leaves its version alone.
# ==============================================================================
set -euo pipefail

VERSION_FILE=apps/household-hub/backend/app/__init__.py

if [ -z "$(git status --porcelain -- 'apps/household-hub/backend/requirements*')" ]; then
  echo "bump-hub-patch: the hub's packages are unchanged; version left as it is"
  exit 0
fi

current="$(sed -n 's/^__version__ = "\([0-9]\{1,\}\.[0-9]\{1,\}\.[0-9]\{1,\}\)"$/\1/p' "$VERSION_FILE")"
if [ -z "$current" ]; then
  echo "bump-hub-patch: no __version__ = \"X.Y.Z\" in $VERSION_FILE" >&2
  exit 1
fi

IFS=. read -r major minor patch <<< "$current"
next="$major.$minor.$((patch + 1))"
sed -i "s/^__version__ = \"$current\"$/__version__ = \"$next\"/" "$VERSION_FILE"
echo "bump-hub-patch: $current -> $next"
