#!/usr/bin/env sh
# Minimal Gradle wrapper script (requires gradle-wrapper.jar).
# Если Android Studio ругается на отсутствие wrapper-jar, она обычно предложит восстановить его на Sync.

DIR="$(cd "$(dirname "$0")" && pwd)"
java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
