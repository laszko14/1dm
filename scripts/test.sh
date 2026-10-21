#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes
javac -encoding UTF-8 -d build/classes $(find src/main/java src/test/java -name '*.java')
java -ea -cp build/classes org.local.tabgroups.TabGroupStoreTest
jar --create --file build/tab-groups-core.jar -C build/classes org/local/tabgroups/TabGroupStore.class -C build/classes 'org/local/tabgroups/TabGroupStore$Group.class'
echo "Generated build/tab-groups-core.jar (not an Android APK)"
