#!/usr/bin/env bash
# Stop the dev RuneLite client (and its gradle launcher) without touching your Bolt client.
# Use this instead of the window's X, which doesn't reliably exit the gradle-run JVM.
set -uo pipefail
killed=0
for p in $(pgrep -x java 2>/dev/null || true); do
  cl=$(tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null || true)
  case "$cl" in
    *raid-recorder*) kill -9 "$p" 2>/dev/null && { echo "killed pid $p"; killed=$((killed+1)); } ;;
  esac
done
[ "$killed" -eq 0 ] && echo "no dev client running" || echo "stopped $killed process(es)"
