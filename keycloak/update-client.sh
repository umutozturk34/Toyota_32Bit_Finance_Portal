#!/bin/bash
# Frontend client'i güncelle

# Load environment variables from .env
if [ -f "../.env" ]; then
    export $(grep -v '^#' ../.env | xargs)
fi

KEYCLOAK_URL="http://localhost:8180"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin123}"

TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

CLIENT_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/finance-realm/clients" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[] | select(.clientId=="finance-frontend") | .id')

echo "Client ID: $CLIENT_ID"

curl -s -X PUT "$KEYCLOAK_URL/admin/realms/finance-realm/clients/$CLIENT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "finance-frontend",
    "enabled": true,
    "publicClient": true,
    "directAccessGrantsEnabled": true,
    "standardFlowEnabled": true,
    "redirectUris": ["http://localhost:5173/*", "http://localhost:3000/*", "http://localhost/*"],
    "webOrigins": ["*"]
  }'

echo ""
echo "✅ Client güncellendi"
