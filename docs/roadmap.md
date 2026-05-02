# Finance Portal — Yol Haritası

Türkiye finansal piyasalarına odaklı, açık kaynaklı kişisel finans takip platformu. Kripto, hisse senedi, döviz, fon, emtia, tahvil ve bono varlıklarını tek bir uygulamada izleyin; portföyünüzü gerçek zamanlı verilerle yönetin.

**Hedef:** v0.20 (i18n) — Mayıs 2026 sonu

---

## Mevcut Yetenekler (v0.13.x)

### Kimlik & Yetkilendirme

- Keycloak tabanlı OAuth2 / OpenID Connect kimlik doğrulama
- JWT tabanlı API erişimi
- Rol bazlı yetkilendirme (USER / ADMIN)
- LDAP entegrasyonu (kurumsal kullanım için)
- Bucket4j ile katmanlı oran sınırlama (`API`, `ADMIN_READ`, `ADMIN_TRIGGER`)

### Piyasa Verileri

| Modül | Kaynak | Kapsam |
|-------|--------|--------|
| Kripto | CoinGecko + Binance | 40+ coin, 1 yıllık geçmiş, USD → TRY otomatik dönüşüm |
| Hisse Senedi | Yahoo Finance | BIST 100, BIST 30, MAIN_INDEX endeksleri |
| Döviz | TCMB + Yahoo Finance | Resmi kurlar + çapraz pariteler |
| Fon | TEFAS API | YAT (yatırım fonu) + BYF (borsa yatırım fonu), 5 yıl geçmiş |
| Emtia | Yahoo Finance | Değerli metaller (altın/gümüş/platin/paladyum) + futures (petrol, doğalgaz, bakır, buğday vb.); altın gram türevi |
| Tahvil & Bono | TCMB EVDS | Devlet tahvili, hazine bonosu; kupon oranı geçmişi, basit getiri hesabı, vade takvimi |
| Haber | RSS Akışları | Çoklu kaynak; skor bazlı otomatik kategorilendirme (kripto / parite / hisse / tahvil / makro / genel finans) |

### Portföy Yönetimi

- **Lot bazlı pozisyon modeli** — aynı varlık için birden fazla giriş (farklı tarih ve fiyatla)
- **Geçmişe yönelik kayıt** — bugün dahi 2015'ten girilen bir BTC için anlamlı performans grafiği
- **Anlık değerleme** — her piyasa güncellemesinde portföy değeri yeniden hesaplanır
- **Gün-içi granüler snapshot** — günde tek satır yerine her market güncellemesi ayrı kayıt
- **Performans grafiği** — toplam değer, K/Z, K/Z yüzdesi; varlık tipi filtresi
- **Dağılım grafiği** — varlık tipine veya varlık koduna göre pasta grafiği
- **Pozisyon detay sayfası** — tek varlık için tarihsel piyasa değeri grafiği, lot listesi
- **Tatil & no-data dayanıklılığı** — bayram günü TEFAS/Yahoo veri dönmediğinde graceful skip; değişim yüzdesi son 2 mum üstünden

### Yönetim Paneli

- Takip edilen varlık listesi (her piyasa türü için aktif/pasif yönetimi)
- Kullanıcı yönetimi (oluştur / güncelle / sil)
- Manuel veri yenileme tetikleyicileri (her market için snapshot / candle / full update)
- Görev geçmişi paneli (init, scheduler, manuel tetik kayıtları)

### Gözlemlenebilirlik

- OpenTelemetry ile dağıtık trace
- Kafka üzerinden log akışı
- OpenSearch + dashboard'lar
- Resilience4j circuit breaker, retry, rate limiter

---

## Yaklaşan Sürümler

### v0.14 — Kullanıcı Modülü

**Hedef:** Kullanıcı profili ve kişiselleştirme.

**Kapsam:**

- Profil bilgileri (görüntüle / düzenle)
- Kullanıcı tercihleri (tema, dil seçimi başlangıç değerleri)
- Kayıt akışı (Keycloak federasyonu üzerinden)
- Şifre sıfırlama
- Avatar yükleme (S3 storage hazır olunca)

---

### v0.15 — Bildirim Sistemi & Takip Listesi

**Hedef:** Kullanıcıya proaktif bilgi akışı.

**Kapsam:**

- E-posta ve uygulama içi (in-app) bildirim kanalları
- Fiyat alarmı (eşik üstü / altı; tetiklenince opsiyonel otomatik alış)
- Takip listesi (watchlist) — varlık üzerinde değişim yüzdesi eşiği
- Bildirim tercihleri (kategori bazlı toggle)
- Sidebar bildirim ikonu, okunmamış sayısı

---

### v0.16 — PDF Rapor Sistemi & AWS S3

**Hedef:** Periyodik portföy raporu.

**Kapsam:**

- Günlük / haftalık / aylık rapor üretimi (kullanıcı tercihine göre)
- iText ile PDF oluşturma
- AWS S3 (prod) / MinIO (geliştirme) storage
- Pre-signed URL ile güvenli indirme
- 90 gün retention politikası
- Rapor içeriği: portföy özeti, pozisyon tablosu, dağılım pasta grafiği, performans çizgi grafiği, dönem işlemleri
- Manuel "Rapor Oluştur" butonu

---

### v0.17 — Emir Yönetimi & Market Saatleri

**Hedef:** Market kapalı iken verilen alış/satış emirlerinin yönetimi.

**Kapsam:**

- BIST (10:00–18:00 hafta içi), TEFAS (10:00–13:30 hafta içi), 7/24 piyasalar (kripto, döviz, emtia)
- `MarketHoursService` — açık/kapalı kontrol, kullanıcıya market durumu göstergesi
- Kapalı market'te emir → `PENDING_ORDER` durumu
- Market açılınca otomatik gerçekleşim (scheduler)
- Bekleyen emirler listesi, iptal akışı
- Fiyat alarmından gelen otomatik alış için emir entegrasyonu

---

### v0.18 — Grafik Çizimleri & MongoDB

**Hedef:** Teknik analiz çizimlerinin kullanıcıya özel saklanması.

**Kapsam:**

- MongoDB altyapısı (Docker Compose'a entegre)
- Trend çizgileri, fibonacci araçları, indikatörler
- Kullanıcı bazlı izolasyon (JWT sub claim)
- Otomatik debounced kaydetme
- "Çizimleri Sil" butonu
- Cihaz arası senkronizasyon

---

### v0.19 — Vadeli İşlem Modülü

**Hedef:** Türev araçlarda kaldıraçlı pozisyon yönetimi.

**Kapsam:**

- `derivative_contracts`, `derivative_positions`, `margin_calls` veri modeli
- Long / Short pozisyon açma
- Marjin hesaplama, marjin tamamlama uyarıları
- Günlük uzlaşma (mark-to-market)
- Vade sonu otomatik kapatma
- Kaldıraç oranları
- Açık pozisyonlar tablosu, K/Z gerçek zamanlı

---

### v0.20 — Çoklu Dil Desteği & Onboarding

**Hedef:** Uluslararası kullanıcıya açılım ve ilk-giriş deneyimi.

**Kapsam:**

- `react-i18next` entegrasyonu
- Türkçe + İngilizce çeviri dosyaları
- Mevcut sabit etiketlerin (varlık tipi, tahvil tipi, fon tipi, segment) i18n'e taşınması
- Keycloak teması için locale switch
- Header'da dil seçici
- İlk giriş tanıtım turu (React Joyride veya custom stepper)
- "Bir daha gösterme" tercihi

---

## Mimari Prensipler

- **Modüler monolit** — her varlık türü kendi Maven modülünde (`finance-crypto`, `finance-stock`, `finance-portfolio` vb.); ortak kod `finance-common`'da
- **Strategy pattern** — `MarketAssetProvider`, `MarketHistoryProvider`, `AssetPricingStrategy` ile piyasa türü genişletmesi açık-kapalı prensibine uygun
- **Sealed DTO'lar** — `MarketAssetMetadata` sealed interface; tip güvenliği `Map<String, Object>` yerine
- **Port & Adapter** — modüller arası bağımlılığı tersine çevirmek için `AssetPricingPort`, `HistoricalPricingPort`, `PortfolioSnapshotPort`
- **Optimistic locking** — `@Version` ile portföy yarış koşulu koruması
- **Defansif hata yönetimi** — Resilience4j circuit breaker + retry; piyasa kapalıyken graceful skip
- **API sözleşmesi** — tüm yanıtlar `ApiResponse<T>` zarfında; entity sınıfları controller'dan dışarı sızmaz

---

## Teknoloji Yığını

### Backend

- Java 21
- Spring Boot 4
- PostgreSQL 15 (Flyway migration)
- Redis (önbellek)
- Apache Kafka (log akışı)
- Keycloak (kimlik)
- Resilience4j
- Maven multi-module

### Frontend

- React 19
- Vite
- TailwindCSS 4
- Zustand (client state)
- TanStack Query (server state)
- ECharts + LightweightCharts
- Framer Motion

### DevOps

- Docker Compose orkestrasyonu
- OpenTelemetry + OpenSearch dashboards
- Nginx reverse proxy
- GitHub Actions CI

---

## Sürüm Geçmişi

| Sürüm | Modül | Açıklama |
|-------|-------|----------|
| v0.13 | Emtia | Yahoo Finance commodity entegrasyonu, türev hesaplama, segment sınıflandırma; geniş refactor sprintleri |
| v0.12 | Birleşik Market | Tek `/api/v1/market` endpoint'i, frontend yenilemesi, search suggestions |
| v0.11 | Portföy | Pozisyon yönetimi, snapshot, performans grafiği |
| v0.10 | Haber | RSS kaynakları, kategori sınıflandırma |
| v0.9 | Tahvil & Bono | EVDS entegrasyonu, kupon geçmişi, getiri hesabı |
| v0.8 | Gözlemlenebilirlik | OpenTelemetry, Kafka, OpenSearch |
| v0.7 | Fon | TEFAS YAT + BYF |
| v0.6 | Döviz | TCMB + Yahoo çapraz pariteler |
| v0.5 | Hisse | Yahoo Finance BIST |
| v0.4 | Kripto | CoinGecko + Binance |
| v0.3 | Yönetim Paneli | Takip listesi, kullanıcı yönetimi |
| v0.2 | Kimlik | Keycloak, JWT, LDAP |
| v0.1 | Altyapı | Spring Boot, React, Docker, PostgreSQL |
