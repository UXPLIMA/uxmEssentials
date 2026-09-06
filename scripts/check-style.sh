#!/usr/bin/env bash
#
# check-style.sh - writing-style guard.
#
# Fails when a file in the working tree holds a character that our style forbids.
# Run it before every commit.
#
# Exit codes:
#   0  the tree is clean
#   1  a forbidden character was found
#   2  the check itself could not run
#
# Two earlier versions of this script were wrong, and both failures are worth
# remembering:
#
#   1. It used `git grep -P` with a \x{...} class and hid the result with
#      `2>/dev/null`. The pattern is invalid without PCRE UTF mode, git exited
#      128, and the empty output read as "no hits". The guard reported success
#      over a real violation. Never silence the exit code of a check.
#   2. It held the forbidden characters literally, so it always matched itself.
#      A guard must not be its own violation.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

# U+2014 EM DASH and U+2013 EN DASH, written as escapes so that this file holds
# neither character.
em_dash=$'\u2014'
en_dash=$'\u2013'

hits="$(grep -rn "[${em_dash}${en_dash}]" . \
    --exclude-dir=.git \
    --exclude-dir=node_modules \
    --binary-files=without-match)"
status=$?

case "$status" in
    0)
        echo "Forbidden long dash. Use a colon, a comma, a full stop, or brackets."
        echo "$hits" | sed 's/^/  /'
        exit 1
        ;;
    1)
        echo "check-style: clean"
        exit 0
        ;;
    *)
        echo "check-style: grep failed with status ${status}" >&2
        exit 2
        ;;
esac
