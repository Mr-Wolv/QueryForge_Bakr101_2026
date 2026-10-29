@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "MVN_HOME=%SCRIPT_DIR%.mvn\apache-maven-3.9.9"
if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Maven distribution not found at "%MVN_HOME%"
  exit /b 1
)
"%MVN_HOME%\bin\mvn.cmd" %*
