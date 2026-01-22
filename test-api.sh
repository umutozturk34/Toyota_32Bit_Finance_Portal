#!/bin/bash
# Backend API Test Script

# Load environment variables
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin123}"

echo "🔐 JWT Token alınıyor..."
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8180/realms/finance-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=finance-frontend" | jq -r '.access_token')

echo "✅ Token alındı: ${ACCESS_TOKEN:0:50}..."
echo ""

echo "📡 Backend API'ye istek gönderiliyor (GET /api/users)..."
echo ""

RESPONSE=$(curl -s http://localhost:8080/api/users -H "Authorization: Bearer $ACCESS_TOKEN")

echo "$RESPONSE" | jq '.'
echo ""

USER_COUNT=$(echo "$RESPONSE" | jq -r '.data | length')
echo "✅ Toplam kullanıcı sayısı: $USER_COUNT"
