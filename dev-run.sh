#!/usr/bin/env bash
# Relaunch the dev client reusing the Jagex session in ~/.jx-session.
#
# Closing the RuneLite window does NOT reliably exit the gradle-run JVM, so this script reaps any
# previous dev client by project path before launching, and detaches the new one cleanly. Use
# ./stop-dev.sh to stop it (don't rely on the window's X).
set -uo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-11-openjdk}"
LOG=/tmp/raid-recorder-dev-run.log

# Kill every java process for THIS project (never matches Bolt — different path). Returns kill count.
reap() {
  local killed=0 p cl
  for p in $(pgrep -x java 2>/dev/null || true); do
    cl=$(tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null || true)
    case "$cl" in
      *raid-recorder*) kill -9 "$p" 2>/dev/null && { echo "   reaped pid $p"; killed=$((killed + 1)); } ;;
    esac
  done
  return "$killed"
}

echo ">> Reaping old dev client(s)..."
reap || true
# Second pass: the gradle daemon forks the client a few seconds after launch, so a just-started
# client from a prior run can appear right after the first pass. Catch it.
sleep 3
reap || true

if [ -f "$HOME/.jx-session" ]; then
  # shellcheck disable=SC1091
  source "$HOME/.jx-session"
  echo ">> Reusing Jagex session for ${JX_DISPLAY_NAME:-unknown}"
else
  echo ">> No ~/.jx-session — dev client will show the login screen."
fi

# setsid + nohup fully detaches the run into its own session so closing this shell never blocks and
# the client keeps running until you stop-dev.sh (or the next dev-run.sh reaps it).
setsid nohup ./gradlew run >"$LOG" 2>&1 < /dev/null &
echo ">> Dev client launching in background (gradle pid $!). Logs: $LOG"
echo ">> Stop it with: ./stop-dev.sh"
