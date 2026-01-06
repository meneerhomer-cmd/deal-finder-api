#!/bin/sh
# Maven Wrapper script for Unix systems

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Download Maven wrapper if not present
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
        WRAPPER_URL=$(grep "wrapperUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
        echo "Downloading Maven Wrapper..."
        if command -v curl > /dev/null; then
            curl -sL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
        elif command -v wget > /dev/null; then
            wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
        else
            echo "Error: curl or wget required to download Maven wrapper"
            exit 1
        fi
    fi
fi

# Execute Maven
if [ -f "$MAVEN_WRAPPER_JAR" ]; then
    exec java $MAVEN_OPTS \
        -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
        -jar "$MAVEN_WRAPPER_JAR" "$@"
else
    # Fallback to system Maven
    exec mvn "$@"
fi
