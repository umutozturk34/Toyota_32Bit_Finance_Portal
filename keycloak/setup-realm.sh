#!/bin/bash

# Keycloak Realm Setup Script
# Bu script finance-realm'i ve gerekli tüm ayarları otomatik olarak yapılandırır

set -e

KEYCLOAK_URL="http://localhost:8180"
ADMIN_USER="admin"
ADMIN_PASS="admin123"
REALM_NAME="finance-realm"

echo "🔐 Keycloak Admin Token alınıyor..."
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
  echo "❌ Token alınamadı! Keycloak çalışıyor mu?"
  exit 1
fi

echo "✅ Token alındı"

# 1. Realm oluştur
echo "📦 finance-realm oluşturuluyor..."
REALM_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "realm": "'"$REALM_NAME"'",
    "enabled": true,
    "displayName": "Toyota Finance Portal",
    "registrationAllowed": true,
    "resetPasswordAllowed": true,
    "rememberMe": true,
    "accessTokenLifespan": 3600,
    "ssoSessionIdleTimeout": 1800
  }')

HTTP_CODE=$(echo "$REALM_RESPONSE" | tail -n 1)
if [ "$HTTP_CODE" == "201" ] || [ "$HTTP_CODE" == "409" ]; then
  echo "✅ Realm oluşturuldu veya zaten mevcut"
else
  echo "⚠️  Realm oluşturma durumu: HTTP $HTTP_CODE"
fi

# 2. Backend client oluştur (bearerOnly)
echo "🔧 Backend client oluşturuluyor..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "finance-backend",
    "enabled": true,
    "bearerOnly": true,
    "directAccessGrantsEnabled": true,
    "publicClient": false
  }' > /dev/null

echo "✅ Backend client oluşturuldu"

# 3. Frontend client oluştur (public)
echo "🔧 Frontend client oluşturuluyor..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "finance-frontend",
    "enabled": true,
    "publicClient": true,
    "directAccessGrantsEnabled": true,
    "standardFlowEnabled": true,
    "redirectUris": [
      "http://localhost:5173/*",
      "http://localhost:3000/*",
      "http://localhost/*",
      "http://localhost:80/*"
    ],
    "webOrigins": [
      "http://localhost:5173",
      "http://localhost:3000",
      "http://localhost",
      "http://localhost:80"
    ]
  }' > /dev/null

echo "✅ Frontend client oluşturuldu"

# 4. Roller oluştur
echo "👥 Roller oluşturuluyor..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "USER",
    "description": "Normal user role"
  }' > /dev/null

curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ADMIN",
    "description": "Administrator role"
  }' > /dev/null

echo "✅ USER ve ADMIN rolleri oluşturuldu"

# 5. Admin kullanıcısı oluştur
echo "👤 Admin kullanıcısı oluşturuluyor..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "email": "admin@finance.com",
    "firstName": "Admin",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": "admin123",
      "temporary": false
    }]
  }' > /dev/null

echo "✅ Admin kullanıcısı oluşturuldu"

# 6. Normal kullanıcı oluştur
echo "👤 Normal kullanıcı oluşturuluyor..."
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user",
    "email": "user@finance.com",
    "firstName": "Normal",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": "user123",
      "temporary": false
    }]
  }' > /dev/null

echo "✅ Normal kullanıcı oluşturuldu"

# 7. Kullanıcılara rol ata
echo "🔐 Kullanıcılara roller atanıyor..."

# Admin kullanıcısının ID'sini al
ADMIN_USER_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users?username=admin" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# USER rolünü al
USER_ROLE=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles/USER" \
  -H "Authorization: Bearer $TOKEN")

# ADMIN rolünü al
ADMIN_ROLE=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles/ADMIN" \
  -H "Authorization: Bearer $TOKEN")

# Admin kullanıcısına her iki rolü de ata
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$ADMIN_USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$USER_ROLE, $ADMIN_ROLE]" > /dev/null

# Normal kullanıcının ID'sini al
NORMAL_USER_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users?username=user" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# Normal kullanıcıya USER rolünü ata
curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$NORMAL_USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$USER_ROLE]" > /dev/null

echo "✅ Roller atandı"

# 8. TOTP (2FA) ayarlarını yapılandır
echo "🔐 2FA (TOTP) ayarları yapılandırılıyor..."
curl -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM_NAME" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "otpPolicyType": "totp",
    "otpPolicyAlgorithm": "HmacSHA1",
    "otpPolicyDigits": 6,
    "otpPolicyPeriod": 30,
    "otpPolicyLookAheadWindow": 1
  }' > /dev/null

echo "✅ 2FA ayarları yapılandırıldı"

echo ""
echo "🎉 Keycloak realm setup tamamlandı!"
echo ""
echo "📋 Erişim Bilgileri:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Keycloak Admin Console: $KEYCLOAK_URL"
echo "Realm: $REALM_NAME"
echo ""
echo "Admin Kullanıcı:"
echo "  Username: admin"
echo "  Password: admin123"
echo "  Email: admin@finance.com"
echo "  Roles: USER, ADMIN"
echo ""
echo "Normal Kullanıcı:"
echo "  Username: user"
echo "  Password: user123"
echo "  Email: user@finance.com"
echo "  Roles: USER"
echo ""
echo "Backend Client ID: finance-backend (bearerOnly)"
echo "Frontend Client ID: finance-frontend (public)"
echo ""
echo "✅ Test URL: $KEYCLOAK_URL/realms/$REALM_NAME"
