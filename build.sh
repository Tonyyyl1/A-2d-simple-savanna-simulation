#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
# Build against a broadly available desktop Java runtime instead of the
# JDK version used on the build machine.
javac --release 17 -Xlint:unchecked -Xlint:deprecation -d build/classes *.java
