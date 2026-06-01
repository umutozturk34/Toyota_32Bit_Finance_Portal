#!/bin/sh
# First-boot: generate a self-signed origin cert so the gateway can serve HTTPS to Cloudflare.
# Idempotent — skipped on subsequent boots.
set -e

CERT_DIR="/etc/nginx/certs"
if [ ! -f "$CERT_DIR/origin.crt" ]; then
  if ! command -v openssl >/dev/null 2>&1; then
    echo "[gateway-init] openssl missing, installing..."
    apk add --no-cache openssl >/dev/null
  fi
  echo "[gateway-init] generating self-signed origin cert (CN=${ORIGIN_CERT_CN:-localhost})..."
  mkdir -p "$CERT_DIR"
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$CERT_DIR/origin.key" \
    -out "$CERT_DIR/origin.crt" \
    -subj "/CN=${ORIGIN_CERT_CN:-localhost}"
  chmod 600 "$CERT_DIR/origin.key"
fi

exec /docker-entrypoint.sh "$@"
