@echo off
REM Minimal Gradle wrapper script (requires gradle-wrapper.jar).
set DIR=%~dp0
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
