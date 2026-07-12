#!/usr/bin/env bash
# Borrow the live Jagex session from a Bolt-launched client and run the dev client with it.
#
# Usage:
#   1. In Bolt, launch RuneLite and log into your Jagex account / pick your character.
#   2. Run this script. It copies the JX_* session vars out of that client's process.
#   3. It tells you to CLOSE the Bolt RuneLite (you can only be logged in once), then
#      launches ./gradlew run with the session injected -> dev client auto-logs-in.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk}"
export JAVA_HOME

# Find the RuneLite client Bolt launched carrying a Jagex session.
# RuneLite's game login needs JX_SESSION_ID + JX_CHARACTER_ID (no access token required).
pid=""
for p in $(pgrep -f -i 'runelite|net\.runelite|client.*\.jar' | sort -u); do
  if tr '\0' '\n' < "/proc/$p/environ" 2>/dev/null | grep -q '^JX_SESSION_ID='; then
    pid=$p; break
  fi
done
# Fallback: any of our processes carrying a Jagex session.
if [ -z "$pid" ]; then
  for p in $(ls /proc | grep -E '^[0-9]+$'); do
    if tr '\0' '\n' < "/proc/$p/environ" 2>/dev/null | grep -q '^JX_SESSION_ID='; then
      pid=$p; break
    fi
  done
fi

if [ -z "$pid" ]; then
  echo "!! No Jagex session (JX_SESSION_ID) found in any client."
  echo "   Open Bolt, launch RuneLite, log in to your Jagex account, then re-run this." >&2
  exit 1
fi

# Snapshot the JX_ vars to a sourceable file (kept outside the repo).
tr '\0' '\n' < "/proc/$pid/environ" | grep '^JX_' \
  | sed -E "s/^([^=]+)=(.*)$/export \1='\2'/" > ~/.jx-session
echo ">> Grabbed $(grep -c '^export JX_' ~/.jx-session) JX_ vars from Bolt client (pid $pid) -> ~/.jx-session"

echo ">> Now CLOSE the RuneLite window you launched from Bolt (single-login limit)."
read -rp ">> Press Enter once it's closed to launch the dev client..."

# shellcheck disable=SC1090
source ~/.jx-session
echo ">> JAVA_HOME=$JAVA_HOME"
exec ./gradlew run
