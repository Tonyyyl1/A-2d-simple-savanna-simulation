#!/usr/bin/env sh
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
printf '%s\n' 'Compatibility wrapper: run-current-dev.sh delegates only to the machine-routed accepted baseline.'
exec "$ROOT/run-accepted-baseline.sh" "$@"
