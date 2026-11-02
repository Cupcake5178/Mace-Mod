#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /workspace/.tools/java25/bin/java ]]; then
    export JAVA_HOME=/workspace/.tools/java25
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.cache/gradle}"
java_options=()
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    mapfile -t proxy_parts < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
if p.hostname:
    print(p.hostname)
    print(p.port or 80)
PY
)
    if [[ "${#proxy_parts[@]}" -eq 2 ]]; then
        java_options+=("-Dhttps.proxyHost=${proxy_parts[0]}" "-Dhttps.proxyPort=${proxy_parts[1]}")
        java_options+=("-Dhttp.proxyHost=${proxy_parts[0]}" "-Dhttp.proxyPort=${proxy_parts[1]}")
    fi
fi
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
    java_options+=("-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
fi
exec ./gradlew "${java_options[@]}" "$@"

