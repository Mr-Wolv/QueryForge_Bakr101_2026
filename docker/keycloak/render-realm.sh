#!/bin/sh
set -eu

secret_file=/run/secrets/qf_oidc_client_secret
template=/opt/keycloak/data/template/queryforge-realm.json.template
realm_dir=/opt/keycloak/data/import

if [ ! -r "$secret_file" ]; then
    echo "OIDC client secret file is not readable" >&2
    exit 1
fi

client_secret=$(tr -d '\r\n' < "$secret_file")
case "$client_secret" in
    ''|REPLACE_ME*|*[!A-Za-z0-9_-]*)
        echo "OIDC client secret must be a non-placeholder base64url value" >&2
        exit 1
        ;;
esac
if [ "${#client_secret}" -lt 32 ]; then
    echo "OIDC client secret must contain at least 32 characters" >&2
    exit 1
fi

web_base_url=${QF_WEB_BASE_URL:-http://localhost:8080}
if ! printf '%s\n' "$web_base_url" | grep -Eq '^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$'; then
    echo "QF_WEB_BASE_URL must be an HTTP(S) origin with an optional port" >&2
    exit 1
fi

mkdir -p "$realm_dir"
sed -e "s|__QF_OIDC_CLIENT_SECRET__|$client_secret|" \
    -e "s|__QF_WEB_BASE_URL__|$web_base_url|g" "$template" \
    > "$realm_dir/queryforge-realm.json"
exec /opt/keycloak/bin/kc.sh "$@"
