# Finance Portal — Roadmap

**Modular & Chronological Development Stages**

---

## Tamamlanan Versiyonlar

| Versiyon | Icerik | Durum |
|----------|--------|-------|
| v0.1.0 | Infrastructure (Spring Boot, React, Docker, PostgreSQL) | ✅ |
| v0.2.0 | Authentication & Keycloak (JWT, RBAC, LDAP) | ✅ |
| v0.3.0 | Admin Module (asset/user management) | ✅ |
| v0.4.0 | Crypto Module (CoinGecko + Binance) | ✅ |
| v0.5.0 | Stock Module (Yahoo Finance BIST) | ✅ |
| v0.6.0 | Forex Module (TCMB + Yahoo) | ✅ |
| v0.7.0 | Fund Module (TEFAS) | ✅ |
| v0.8.0 | Observability (OTel, Kafka, OpenSearch, Dashboards) | ✅ |
| v0.9.0 | Bond & Bill Module (EVDS, rate history, yield calculation) | ✅ |
| v0.10.0 | News Module (RSS, score-based categorization) | ✅ |
| v0.11.0 | Portfolio Module (BUY/SELL, WAC, snapshots, performance) | ✅ |
| v0.12.0 | Unified Endpoint & Frontend Refactor (Strategy pattern, SearchSuggestions, AnimatedIcons, chart comparison) | ✅ |

---

## Aktif Gelistirme Plani (15 Nisan — 25 Mayis)

### v0.13.0 — Emtia Modulu — ✅ TAMAMLANDI (15 Nisan → 22 Nisan | 8 gun)

**Branch:** `feat/emtia` (merge'e hazir)

**Gerçeklesen kapsam (orijinal plandan genisledi):**

Backend — finance-commodity modul:
- Yahoo Finance commodity data (metaller: XAUTRY/XAGTRY/XPTTRY/XPDTRY; futures: BZ=F/HG=F/ZW=F/NG=F/vs.)
- `CommodityDerivativeRule` registry (YAML-driven) — gram derivatives: XAUTRYG (altin gram), XAGTRYG (gumus gram). Diger coin derivatives (GOLD_TAM/YARIM/CEYREK/CUMHURIYET) kullanici talebiyle kaldirildi — piyasa gercekligi ile uyumsuzdu
- `SyntheticPriceCalculator` (finance-common) USD × USDTRY conversion — stock ve forex ile paylasimli
- `ExchangeRateProvider` port (finance-common) + `ForexExchangeRateProvider` impl — commodity artik finance-forex modulune bagli degil
- `YahooSymbolResolver` bean — domain code → Yahoo symbol cevrimi tek noktada
- `CommoditySegmentResolver` — PRECIOUS_METAL / OTHER segment'i config map + derivative codes'tan turetir, `CommoditySegment.fromCode` string-pattern factory kaldirildi
- `PreciousMetalDerivativeCalculator` — snapshot + candle regeneration, registry-driven (if/else dispatch yok)
- `CommodityPricingStrategy` — stock/fund/crypto commission pattern (forex spread degil), `commodity-rate: 0.015`
- `CommodityMarketAssetProvider`, `CommodityResponseMapper`, `CommodityMetadata` sealed record
- `CommodityScheduler` (3x daily cron, Europe/Istanbul), `CommodityDataService` (admin trigger), `CommodityQueryService` (history endpoint)
- `AssetType.COMMODITY` + `MarketType.COMMODITY` + `TrackedAssetType.COMMODITY` enum'lari
- Flyway migrations V36-V50: table create, USD price storage, silver+wheat seed, copper seed, OHLC+volume, segment+categories, metal code rename, derivative code rename, sellingPrice column drop

Frontend:
- `features/commodity/` — CommoditiesPage (tab'li segment filter: Tumu / Kiymetli Metaller / Digerleri), CommodityDetail
- `commodityService` TanStack Query ile
- MarketDataPage overview'da commodity movers (ASSET_TYPE_COLORS turuncu)
- SearchInput + SearchSuggestions commodity destegi (XAU → XAUTRY match)
- BuyModal / SellModal commodity destegi (fractional trading aktif)
- Admin panel: AdminTrackedAssetsPage'e COMMODITY tab, TasksPanel'e Commodity action group
- UI bug fixes: SellModal double-commission fix, BuyModal stale price refetch

**Toplam:** ~2500 LOC backend + tests, ~37 commit (orijinal Commit 1-7 plani + refactor arc'i R1-R4 + cesitli fix/UI).

---

### v0.14.0 — Notification Sistemi + Takip Listesi (16-20 Nisan | 5 gun)

**Veritabani (V34-V36):**
- `notification_preferences` — user_sub, channel (EMAIL/IN_APP), category, enabled
- `notifications` — user_sub, title, body, category, channel, read, created_at
- `price_alerts` — user_sub, asset_type, asset_code, target_price, direction (ABOVE/BELOW), triggered, auto_buy, auto_buy_amount
- `watchlist` — user_sub, asset_type, asset_code, change_threshold

**Backend:**
- `finance-notification` modul
- `NotificationService` — CRUD, mark read, list (paginated)
- `NotificationDispatcher` — tercih kontrol, kanal yonlendirme
- `EmailNotificationService` — JavaMailSender (Mailpit dev, SMTP prod)
- `InAppNotificationService` — DB'ye kaydet
- `PriceAlertService` — CRUD + checkAlerts()
- `WatchlistService` — CRUD + checkChanges()
- `NotificationPreferenceService` — kullanici tercihleri
- `NotificationCheckPort.onMarketUpdate()` — Observer pattern, scheduler sonrasi kontrol
- Endpoint'ler: /notifications, /price-alerts, /watchlist, /notification-preferences

**Frontend:**
- Sidebar'da bildirim ikonu (okunmamis sayisi badge)
- /notifications sayfasi
- /price-alerts sayfasi — alarm olustur/sil/duzenle
- /watchlist sayfasi — varlik ekle/cikar, esik ayarla
- Ayarlar sayfasi — tercihler (email, in-app toggle)
- Detay sayfalarinda "Alarm Ekle" ve "Takip Et" butonlari

---

### v0.15.0 — PDF Rapor Sistemi + AWS S3 (21-25 Nisan | 5 gun)

**Backend:**
- `PdfGenerationService` — iText ile PDF olusturma
- `ReportStorageService` — AWS S3 upload/download (dev: MinIO Docker, prod: S3)
- `ReportScheduler` — gunluk/haftalik/aylik (kullanici tercihine gore)
- `portfolio_reports` tablosu — user_sub, report_type, period, s3_key, file_size
- Pre-signed URL ile guvenli download
- 90 gun retention (S3 lifecycle)
- Flyway migration V37-V38

**PDF Icerigi:**
- Portfolyo ozeti (toplam deger, K/Z, nakit)
- Pozisyon tablosu (varlik, miktar, maliyet, piyasa degeri, K/Z)
- Dagilim pasta grafigi
- Performans cizgi grafigi
- Islem listesi (donem ici)
- Header: kullanici adi, tarih araligi

**Frontend:**
- Rapor frekansi secimi (ayarlar)
- Rapor gecmisi listesi (tarih, tip, boyut, indir)
- Manuel "Rapor Olustur" butonu

---

### v0.16.0 — Alis Emri + Market Saatleri (26-28 Nisan | 3 gun)

**Market Saat Kurallari:**
- BIST (Stock): 10:00-18:00 Hafta ici
- TEFAS (Fund): 10:00-13:30 Hafta ici
- Crypto: 7/24
- Forex: 7/24
- Commodity: 7/24

**Backend:**
- `MarketHoursService` — market acik/kapali kontrol, config'den saat okuma
- Kapali market'te alis/satis → `PENDING_ORDER` status
- `pending_orders` tablosu — user_sub, asset_type, asset_code, side, quantity/amount, created_at
- `OrderExecutionScheduler` — market acilinca pending order'lari execute et
- Fiyat alarmi tetiklenince auto_buy → pending order veya anlik islem (market durumuna gore)
- Flyway migration V39

**Frontend:**
- Market durumu gostergesi (acik/kapali badge)
- Kapali market'te "Emir Ver" butonu (Satin Al yerine)
- Bekleyen emirler listesi
- Emir iptal

---

### v0.17.0 — Grafik Kaydetme + MongoDB (29 Nisan - 1 Mayis | 3 gun)

**Altyapi:**
- Docker Compose'a MongoDB ekleme
- `spring-boot-starter-data-mongodb` dependency

**Backend:**
- `ChartDrawing` document (userSub, assetType, assetCode, drawings, indicators, fibTools)
- `ChartDrawingRepository extends MongoRepository`
- CRUD endpoint: GET/PUT /api/v1/chart-drawings/{assetType}/{assetCode}
- Kullanici bazli izolasyon (JWT sub)

**Frontend:**
- Chart acildiginda backend'den cizim yukle
- Cizim degistiginde debounced auto-save
- "Cizimleri Sil" butonu

---

### v0.18.0 — Vadeli Islem Modulu (2-11 Mayis | 10 gun)

**Veritabani (V40-V43):**
- `derivative_contracts` — asset_type, asset_code, contract_size, expiry_date, margin_rate
- `derivative_positions` — user_sub, contract_id, side (LONG/SHORT), quantity, entry_price, margin_amount
- `derivative_transactions` — islem gecmisi
- `margin_calls` — teminat tamamlama kayitlari

**Backend:**
- `finance-derivative` modul
- `DerivativeContractService` — kontrat tanimlama
- `DerivativePositionService` — pozisyon acma/kapatma
- `MarginService` — teminat hesaplama, margin call kontrol
- `DailySettlementService` — gunluk uzlasma (mark-to-market)
- `DerivativeScheduler` — gunluk uzlasma + vade kontrol + margin call
- Kaldirac hesaplamasi
- Vade sonu otomatik kapatma

**Frontend:**
- Vadeli islem sayfasi — kontrat listesi
- Pozisyon acma formu (LONG/SHORT, lot, kaldirac)
- Acik pozisyonlar tablosu (K/Z, teminat durumu)
- Margin call bildirimi
- Vade takvimi

---

### v0.19.0 — i18n + Kullanici Tercihleri + Tanitim (12-16 Mayis | 5 gun)

**i18n:**
- react-i18next entegrasyonu
- TR ve EN translation dosyalari
- Mevcut label map'ler (ASSET_TYPE_LABELS, BOND_TYPE_LABELS, FUND_TYPE_LABELS, SEGMENT_LABELS) translation'a tasima
- Keycloak tema i18n (FreeMarker locale switch)
- Dil secimi header'da

**User Preferences:**
- `user_preferences` tablosu (V44) — user_sub, language, theme, notification settings
- CRUD endpoint
- Frontend ayarlar sayfasi

**Tanitim Ekrani:**
- Ilk giris onboarding tour (React Joyride veya custom stepper)
- Temel ozelliklerin tanitimi (portfolio, chart, alarm)
- "Bir daha gosterme" tercihi

---

### Test + Debug + Polish (17-21 Mayis | 5 gun)

- Tum endpoint'lerin Swagger'dan test edilmesi
- Frontend tum sayfalarin cross-browser testi
- Performance profiling (buyuk portfolio, cok haber)
- Edge case'ler (bos portfolio, 0 bakiye, expired token)
- Responsive tasarim kontrol
- Dark/light mode tutarlilik

---

### Buffer (22-25 Mayis | 4 gun)

- Geciken isler icin yedek sure
- Son dakika bug fix
- Dokumandasyon finalizasyonu

---
