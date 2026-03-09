#!/bin/sh
APP_HOME=$(dirname "$0"); APP_HOME=$(cd "$APP_HOME" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD="${JAVA_HOME:-}/bin/java"; [ -z "$JAVA_HOME" ] && JAVACMD=java
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
