#!/bin/sh
set -e

CERT_DIR="$(dirname "$0")/certs"

mkdir -p "$CERT_DIR"

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -subj "/C=US/ST=State/L=City/O=Markit/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "Self-signed certificates generated in $CERT_DIR"
echo "  - server.crt"
echo "  - server.key"
