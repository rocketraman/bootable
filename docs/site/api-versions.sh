#!/usr/bin/env bash
# Builds each released docs version's API reference, for the site build to publish under that version
# (extensions/dokka.js reads build/api-versions/<version>).
#
# For each release tag the site builds docs from (the newest patch of each minor release that has docs/antora.yml, as
# extensions/versions.js chooses them), it checks the tag out in a temporary git worktree, runs Dokka there, and copies
# the output to build/api-versions/<major>.<minor>. A version already there is skipped: a tag doesn't change, so CI can
# cache the directory.
#
# Usage, from docs/site: ./api-versions.sh
set -euo pipefail

site_dir="$(cd "$(dirname "$0")" && pwd)"
repo="$(git -C "$site_dir" rev-parse --show-toplevel)"
out="$site_dir/build/api-versions"
mkdir -p "$out"

# the newest patch tag of each minor release, newest release first
tags="$(git -C "$repo" tag --list 'v*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -t. -k1,1V -k2,2n -k3,3n -r \
  | awk -F. '{ minor = $1 "." $2 } !seen[minor]++')"

for tag in $tags; do
  git -C "$repo" cat-file -e "$tag:docs/antora.yml" 2>/dev/null || continue
  version="$(echo "${tag#v}" | cut -d. -f1,2)"
  if [ -d "$out/$version" ]; then
    echo "$version: already built"
    continue
  fi
  worktree="$(mktemp -d)"
  trap 'git -C "$repo" worktree remove --force "$worktree" 2>/dev/null || true' EXIT
  echo "$version: building the API reference at $tag"
  git -C "$repo" worktree add --detach --quiet "$worktree" "$tag"
  (cd "$worktree" && ./gradlew -q :dokkaGeneratePublicationHtml)
  cp -r "$worktree/build/dokka/html" "$out/$version"
  git -C "$repo" worktree remove --force "$worktree"
  trap - EXIT
done
