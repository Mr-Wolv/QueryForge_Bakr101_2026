#!/bin/sh
set -eu

: "${QF_SMTP_HOST:?QF_SMTP_HOST is required}"
: "${QF_SMTP_FROM:?QF_SMTP_FROM is required}"
: "${QF_SMTP_USERNAME:?QF_SMTP_USERNAME is required}"
: "${QF_ALERT_EMAIL:?QF_ALERT_EMAIL is required}"

case "$QF_SMTP_HOST" in
  *[!A-Za-z0-9._:-]*|'')
    echo "QF_SMTP_HOST must contain only a hostname and optional port" >&2
    exit 1
    ;;
esac

for email in "$QF_SMTP_FROM" "$QF_ALERT_EMAIL"; do
  if ! printf '%s\n' "$email" | grep -Eq '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'; then
    echo "SMTP sender and alert recipient must be valid email addresses" >&2
    exit 1
  fi
done

case "$QF_SMTP_USERNAME" in
  *[!A-Za-z0-9._+@-]*|'')
    echo "QF_SMTP_USERNAME contains unsupported characters" >&2
    exit 1
    ;;
esac

test -r /run/secrets/smtp_password
envsubst '${QF_SMTP_HOST} ${QF_SMTP_FROM} ${QF_SMTP_USERNAME} ${QF_ALERT_EMAIL}' \
  < /etc/alertmanager/alertmanager.yml.tmpl \
  > /tmp/alertmanager.yml
exec /bin/alertmanager \
  --config.file=/tmp/alertmanager.yml \
  --storage.path=/alertmanager \
  --web.listen-address=0.0.0.0:9093
