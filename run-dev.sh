#!/usr/bin/env sh
set -eu

DIR="${1:?usage: run-dev.sh <accepted-ecology-candidate-dir> [simulation-args...]}"
shift
ROOT="$(cd "$(dirname "$0")" && pwd)"
printf '%s\n' 'Compatibility wrapper: run-dev.sh accepts only an explicit sealed ecology candidate directory.'
exec "$ROOT/run-ecology-candidate.sh" "$DIR" "$@"
