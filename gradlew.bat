@ECHO OFF

REM Gradle start up script for Windows

SET APP_BASE_NAME=%~n0
SET DIR=%~dp0

IF EXIST "%JAVA_HOME%\bin\java.exe" (
  SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET JAVA_EXE="java.exe"
)

%JAVA_EXE% -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
