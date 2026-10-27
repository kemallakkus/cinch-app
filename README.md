# Cinch — Kişisel Bütçe Koçu

Compose Multiplatform (Kotlin, Android + iOS) ile geliştirilen, **tamamen cihaz içinde çalışan
(local-first)** kişisel bütçe koçu. AI ve backend yoktur; tüm mantık `shared` modülündeki saf
Kotlin domain çekirdeğinde yaşar. Veritabanı SQLDelight; para biçimi yerel ayara göre (TR/EN).

## Durum

Domain çekirdeği, SQLDelight repository katmanı, Koin DI, ilk ekran akışı (bütçe kurulumu → ana ekran →
hızlı kayıt) ve koç bildirimleri (Android + iOS, izin akışıyla) hazır.

## Modül yapısı

```
cinch-app/
├─ shared/        KMP kütüphane: domain çekirdeği + SQLDelight (jvm/android/ios hedefleri)
├─ composeApp/    KMP kütüphane: ortak Compose UI (App) + Android Activity + iOS ViewController
├─ androidApp/    İnce com.android.application modülü (APK; composeApp'e bağlı)
└─ iosApp/        SwiftUI kabuğu (Xcode projesi IDE'de üretilir)
```

> AGP 9'da `com.android.application` + `kotlin.multiplatform` aynı modülde kullanılamadığından
> Android uygulaması ayrı ince bir application modülüdür; Compose UI ve `MainActivity`
> `composeApp` KMP kütüphanesinde yaşar.

## Domain çekirdeği (`shared/.../`)

| Bileşen | Sorumluluk | Dosya |
|---|---|---|
| `Money` / `MoneyFormatter` | Kuruş bazlı tutar (float hatasız), TR/EN biçim | `money/` |
| `QuickEntryParser` | "195 market", "dün 120 su ekmek", "625 ... yarısı geri geldi" → tutar/tarih/pay/kategori | `parser/` |
| `Categorizer` + `KeywordDictionary` | Anahtar kelime sözlüğü + kullanıcı düzeltmelerinden öğrenme | `category/` |
| `BudgetEngine` | Haftalık sınır, günlük kalan pay, ay toplamı; paylaşımda yalnız kullanıcı payı sayılır | `budget/` |
| `PayoffSimulator` | Kart kapanışı (~%4.9/ay faiz+vergi), taksit, esnek ödeme (Eminevim) | `debt/` |
| `Reconciler` | Elle girilen güncel borç ile kayıt farkını bulur ("103 ₺ yazmayı unutmuşsun") | `reconcile/` |
| `CoachRules` | 7 günün yerel bildirimlerini üretir; sessiz saat + günde max 2 + cümle varyasyonu | `coach/` |

## Veri katmanı ve DI (`shared/.../`)

| Bileşen | Sorumluluk | Dosya |
|---|---|---|
| `ExpenseRepository` / `CardRepository` / `BudgetRepository` | Arayüzler; tarih aralıkları iki uç dahil, `observe*` Flow döner | `domain/repository/` |
| `Sql*Repository` | SQLDelight implementasyonları; tarih epoch-day, tutar kuruş saklanır | `data/` |
| `SqlCorrectionStore` | Kategori düzeltmelerini kalıcı tutar (ilk erişimde belleğe alınır) | `data/` |
| `ObserveBudgetStatus` | Bütçe + harcamaları izler, `BudgetEngine` ile canlı `BudgetStatus` yayar | `budget/` |
| `PlanCoachWeek` | Bütçe, son 30 gün ve kart ekstre günlerinden `CoachRules` planını üretir | `coach/` |
| `DataModule` / `DomainModule` / `PlatformModule` | Koin modülleri (annotation, `@Configuration`) | `di/` |

Koin **annotation tabanlı** ve DSL kullanılmıyor. Modülleri Koin compiler plugin topluyor, KSP gerekmiyor. Giriş noktası
`composeApp/.../di/CinchKoinApp.kt` (`@KoinApplication`) + `StartKoin.doInitKoin()`. Android'de
`CinchApplication` önce `initPlatform(context)` çağırır; iOS'ta `iOSApp.init` Koin'i başlatır.
Eksik bağımlılık derleme anında hata verir (compile safety).

## Ekranlar (`composeApp/.../`)

| Ekran | Ne yapar | Dosyalar |
|---|---|---|
| Bütçe kurulumu | İlk açılışta (bütçe yoksa) açılır; haftalık sınır, hafta başlangıcı, maaş günü. Ana ekrandaki "Bütçe" ile düzenlenir | `ui/budget/` |
| Ana ekran | Bugün kalan pay, haftalık ilerleme, ay toplamı; bu haftanın harcamaları (sola kaydır → sil, "Geri al") | `ui/home/` |
| Hızlı kayıt | Alttaki alan: "195 market" yazdıkça tutar/kategori önizlenir; kategori düzeltilirse kelime öğrenilir | `ui/home/QuickEntryBar.kt` |

Navigation 3 (tek geri yığını, `navigation/CinchNavHost.kt`); her ekran `Wrapper` (ViewModel + effect) →
`Screen(state, onEvent)` düzeninde. ViewModel'ler `di/UiModule.kt`'de `@KoinViewModel` ile tanımlı ve
`commonTest`'te sahte repository'lerle test ediliyor. Metinler şimdilik Türkçe ve koda gömülü.

## Koç bildirimleri (`composeApp/.../notifications/`)

`CoachRules` 7 günlük planı üretir; `CoachNotificationSync` bunu platform zamanlayıcısıyla eşit tutar.
Eşitleme farka göre yapılır: plandan düşen iptal edilir, eksik eklenir, aynısı kalana dokunulmaz.
Plan şu durumlarda yeniden kurulur: Koin açılışı, bütçe/kart/harcama değişikliği ve uygulamanın öne gelmesi.

| | Android | iOS |
|---|---|---|
| Zamanlama | Bildirim başına bir WorkManager işi (`AndroidNotificationScheduler`); yeniden başlatmadan sonra WorkManager geri kurar | `UNCalendarNotificationTrigger` (`IosNotificationScheduler`), en fazla 64 bekleyen |
| Mesaj | Gösterim anında güncel veriyle yeniden üretilir (`CoachNotificationPlanner.resolveDue`); kural artık geçerli değilse gösterilmez | Eklendiği anda sabit; mesaj değişince bildirim yenilenir |
| Uygulama açılmazsa | Günlük `CoachReplanWorker` planı ileri taşır | Plan son açılıştaki haliyle kalır (7 gün) |
| İzin | Android 13+ `POST_NOTIFICATIONS`; bir kez sorulur, sonra ayarlara yönlendirir | `requestAuthorization`; reddedilince ayarlara yönlendirir |

İzin ana ekrandaki karttan, ne işe yarayacağı anlatıldıktan sonra istenir ("Şimdi değil" kartı 7 gün gizler).
Küçük tercihler (`KeyValueStore`) Android'de SharedPreferences, iOS'ta NSUserDefaults'ta tutulur.

## Çalıştırma

```bash
# Birim testleri (hızlı, JVM hedefi — Android/iOS derlemesi gerektirmez)
./gradlew :shared:jvmTest

# ViewModel testleri (JVM üzerinde Android host test)
./gradlew :composeApp:testAndroidHostTest

# Android APK
./gradlew :androidApp:assembleDebug

# iOS framework (simülatör)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Araç sürümleri

Kotlin 2.4.21 · AGP 9.4.1 · Compose Multiplatform 1.12.1 · Gradle 9.8.0 · SQLDelight 2.4.1 ·
Koin 4.2.2 (compiler plugin 1.2.1) · Navigation 3 1.2.0-rc01 · WorkManager 2.12.0
(`gradle/libs.versions.toml`)

## Sonraki adımlar

- Veritabanı migration'ları (ilk sürüm yayınlanmadan önce; şema şu an `CREATE TABLE IF NOT EXISTS`)
- Kayıtlı harcamayı düzenleme (tutar/kategori); kart seçimi ve kart ekranı
- Bildirim ayarları (sessiz saatler, günlük sayı, türleri aç/kapa); bildirime dokununca ilgili ekrana gitme
- Abonelik (Play Billing + StoreKit, doğrulama RevenueCat), yedekleme (Drive / iCloud)
- Opsiyonel cihaz içi OCR fiş okuma (ML Kit / Apple Vision)
- iOS Xcode projesini oluşturma (`iosApp/` Swift kaynakları hazır)
