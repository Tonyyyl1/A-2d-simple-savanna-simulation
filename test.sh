#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
./build.sh
java AllTests
