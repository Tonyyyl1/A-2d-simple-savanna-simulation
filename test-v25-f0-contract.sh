#!/usr/bin/env sh
# Historical entry-point compatibility only. V25-F0 evidence stays immutable;
# P0-lite is the current routing contract and intentionally has no hidden
# 100/1000-step observer invocation.
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
printf '%s\n' 'V25-F0 contract is historical; delegating to the current P0-lite identity contract.'
exec "$ROOT/test-p0-lite-contract.sh" identity
