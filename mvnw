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
MVN_HOME="$DIR/.mvn/apache-maven-3.9.9"
if [ -x "$MVN_HOME/bin/mvn" ]; then
  # The bundled mvn shell script can fail in git-bash when JAVA_HOME has a
  # Windows trailing backslash. Fall back to the .cmd wrapper in that case.
  if "$MVN_HOME/bin/mvn" --version >/dev/null 2>&1; then
    exec "$MVN_HOME/bin/mvn" "$@"
  fi
fi
exec cmd.exe /c "$(cygpath -w "$DIR/runmvn.bat")" "$@"
