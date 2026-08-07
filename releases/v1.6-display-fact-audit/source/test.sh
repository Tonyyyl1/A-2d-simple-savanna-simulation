#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
./build.sh
./verify-display-contract.sh build/classes
sh ./verify-overview-frame-contract.sh build/classes
sh ./verify-display-fact-audit-contract.sh build/classes
legacy_dir=$(mktemp -d "${TMPDIR:-/tmp}/savanna-b1-overview-red.XXXXXX")
trap 'rm -rf "$legacy_dir"' EXIT HUP INT TERM
unzip -qq build/savanna-simulation-v1.6-dead-actor-consistency-b1-dev.jar \
    -d "$legacy_dir"
if legacy_output=$(sh ./verify-overview-frame-contract.sh "$legacy_dir" 2>&1); then
    echo "FAIL B1 unexpectedly passed the B2 overview-frame red gate" >&2
    exit 1
fi
printf '%s\n' "$legacy_output" | grep -q \
    'legacy B1 transparent-front race detected' || {
    echo "FAIL B1 red gate did not identify the legacy transparent-front race" >&2
    exit 1
}
echo "B1 expected-red overview-frame verification passed"
java -Djava.awt.headless=true -cp build/classes AllTests
