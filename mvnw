#!/bin/sh
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
DIR=`cd "\`dirname "$PRG"\`" && pwd`

# Fix common Windows JAVA_HOME misconfiguration: strip trailing backslash
# so the Maven launcher can build a valid JAVACMD path.
if [ -n "$JAVA_HOME" ]; then
  JAVA_HOME_FIXED="${JAVA_HOME%\\}"
  if [ "$JAVA_HOME_FIXED" != "$JAVA_HOME" ]; then
    export JAVA_HOME="$JAVA_HOME_FIXED"
  fi
fi

MVN_HOME="$DIR/.mvn/apache-maven-3.9.9"
if [ -x "$MVN_HOME/bin/mvn" ]; then
  if "$MVN_HOME/bin/mvn" --version >/dev/null 2>&1; then
    exec "$MVN_HOME/bin/mvn" "$@"
  fi
fi

# Windows fallback: the bundled mvn shell script can fail in git-bash
# due to path/JAVA_HOME issues; mvn.cmd works via cmd.exe.
MVN_CMD="$MVN_HOME/bin/mvn.cmd"
if [ -f "$MVN_CMD" ]; then
  if command -v cygpath >/dev/null 2>&1; then
    MVN_CMD=$(cygpath -w "$MVN_CMD")
  fi
  exec cmd.exe /c "$MVN_CMD" "$@"
fi

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

echo "ERROR: Maven not found (tried bundled mvn/mvn.cmd and system PATH)" >&2
exit 1
