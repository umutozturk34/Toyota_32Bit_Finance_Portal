#!/bin/bash

# Keycloak Self-Registration Aktivasyonu
# Bu script Keycloak realm'inde self-registration özelliğini aktif eder

echo "🔧 Keycloak self-registration aktif ediliyor..."

# Keycloak admin token al
echo "🔑 Admin token alınıyor..."
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
  echo "❌ Admin token alınamadı!"
  exit 1
fi

echo "✅ Admin token alındı"

# Realm ayarlarını güncelle: registrationAllowed = true
echo "🔧 Realm ayarları güncelleniyor..."
curl -s -X PUT "http://localhost:8180/admin/realms/finance-realm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "realm": "finance-realm",
    "registrationAllowed": true,
    "registrationEmailAsUsername": false,
    "editUsernameAllowed": false,
    "resetPasswordAllowed": true,
    "rememberMe": true,
    "verifyEmail": false,
    "loginWithEmailAllowed": true,
    "duplicateEmailsAllowed": false
  }'

echo ""
echo "✅ Self-registration aktif edildi!"
echo ""
echo "📋 Yapılan değişiklikler:"
echo "  - registrationAllowed: true (Kullanıcılar kendi hesaplarını oluşturabilir)"
echo "  - registrationEmailAsUsername: false (Username ve email ayrı olabilir)"
echo "  - editUsernameAllowed: false (Username değiştirilemez)"
echo "  - resetPasswordAllowed: true (Şifre sıfırlama aktif)"
echo "  - rememberMe: true ('Beni hatırla' özelliği aktif)"
echo "  - verifyEmail: false (Email doğrulama gerekmiyor - test için)"
echo "  - loginWithEmailAllowed: true (Email ile giriş yapılabilir)"
echo "  - duplicateEmailsAllowed: false (Aynı email kullanılamaz)"
echo ""
echo "🎉 Artık kullanıcılar /register sayfasından hesap oluşturabilir!"
echo ""
echo "Test için:"
echo "  1. http://localhost/register adresine git"
echo "  2. 'Register with Keycloak' butonuna tıkla"
echo "  3. Keycloak registration formunu doldur"
echo "  4. Hesap oluşturulduktan sonra otomatik login olacaksın"
