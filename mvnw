#!/bin/sh
# Maven Wrapper launch script

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
if [ -r "$MAVEN_WRAPPER_JAR" ]; then
    exec "$JAVA_HOME/bin/java" -jar "$MAVEN_WRAPPER_JAR" "$@"
fi

# Fallback: use system mvn
exec mvn "$@"
