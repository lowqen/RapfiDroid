#!/usr/bin/env bash
# Refreshes the APK's copy of the official joseki curriculum from Zenyth Web.
#
# The app reads the published file at run time
# (https://rapfiweb.pages.dev/joseki/curriculum.json, JosekiRepository); the
# copy in assets is only what a fresh install shows before its first fetch, or
# offline. Run this before a release so that copy is the current one:
#
#   tools/sync_curriculum.sh            # from the published site
#   tools/sync_curriculum.sh FILE       # from a local file (Zenyth Web's public/joseki/curriculum.json)
#
# The maintainer edits the curriculum in Zenyth Web's curriculum studio and
# publishes it with `pnpm curriculum:publish` (Zenyth Web docs/공식-정석-관리.md).
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
dest="$here/app/src/main/assets/joseki/curriculum.json"
url="https://rapfiweb.pages.dev/joseki/curriculum.json"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
if [ $# -ge 1 ]; then
  cp "$1" "$tmp"
else
  curl -fsSL "$url" -o "$tmp"
fi

# The same first check the app makes: the right format, and at least one lesson.
grep -q '"format": *"rapfiweb-curriculum"' "$tmp" || { echo "not a rapfiweb-curriculum file" >&2; exit 1; }
grep -q '"lessons": *\[ *{' "$tmp" || grep -q '"lessons": *\[$' "$tmp" || { echo "no lessons in the file" >&2; exit 1; }

if cmp -s "$tmp" "$dest"; then
  echo "assets/joseki/curriculum.json is already current"
else
  cp "$tmp" "$dest"
  version="$(grep -m1 -o '"version": *"[^"]*"' "$dest" | sed 's/.*"\([^"]*\)"$/\1/')"
  echo "assets/joseki/curriculum.json updated (version $version) - run the unit tests, then commit it"
fi
