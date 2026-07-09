#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
javac -Xlint:unchecked -Xlint:deprecation *.java
