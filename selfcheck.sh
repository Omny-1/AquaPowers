#!/usr/bin/env bash
# Runs the offline logic checks. No Minecraft server required.
#
# These now run automatically as part of `mvn -o package`, so this script is just a shortcut for
# running them on their own. It no longer hardcodes a spigot-api version — that used to break with a
# confusing classpath error the moment the server was updated.
set -e
cd "$(dirname "$0")"
mvn -o test
