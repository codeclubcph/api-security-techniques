#!/bin/sh
# Gradle wrapper script — auto-downloads Gradle 8.7 on first run
# https://docs.gradle.org/current/userguide/gradle_wrapper.html

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVA_EXE="${JAVA_HOME:-}/bin/java"

if [ ! -f "$JAVA_EXE" ]; then
    JAVA_EXE="$(which java)"
fi

if [ -z "$JAVA_EXE" ]; then
    echo "ERROR: JAVA_HOME is not set and no 'java' found in PATH."
    exit 1
fi

exec "$JAVA_EXE" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
