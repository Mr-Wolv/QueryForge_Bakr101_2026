#!/bin/sh
set -eu

app_password="$(cat /run/secrets/queryforge_app_password)"
monitor_password="$(cat /run/secrets/queryforge_monitor_password)"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=app_password="$app_password" \
  --set=monitor_password="$monitor_password" <<'SQL'
CREATE ROLE queryforge_app LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE queryforge TO queryforge_app;
GRANT USAGE ON SCHEMA public TO queryforge_app;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO queryforge_app;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO queryforge_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO queryforge_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO queryforge_app;

CREATE ROLE queryforge_monitor LOGIN PASSWORD :'monitor_password';
GRANT CONNECT ON DATABASE queryforge TO queryforge_monitor;
GRANT pg_monitor TO queryforge_monitor;
SQL
