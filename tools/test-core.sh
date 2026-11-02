#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
test_javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
mkdir -p build/core-test
find src/main/java src/test/java -name '*.java' -print > build/core-test/sources.txt
"$test_javac" --release 25 -d build/core-test @build/core-test/sources.txt
"$test_java" -ea -cp build/core-test dev.macepilot.core.CoreTests

