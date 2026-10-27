# Cinch — Kişisel Bütçe Koçu

Compose Multiplatform (Kotlin, Android + iOS) ile geliştirilen, **tamamen cihaz içinde çalışan
(local-first)** kişisel bütçe koçu. AI ve backend yoktur; tüm mantık `shared` modülündeki saf
Kotlin domain çekirdeğinde yaşar. Veritabanı SQLDelight; para biçimi yerel ayara göre (TR/EN).

## Durum

Domain çekirdeği, SQLDelight repository katmanı ve Koin DI hazır (**90 birim testi yeşil**).

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

## Çalıştırma

```bash
# Birim testleri (hızlı, JVM hedefi — Android/iOS derlemesi gerektirmez)
./gradlew :shared:jvmTest

# Android APK
./gradlew :androidApp:assembleDebug

# iOS framework (simülatör)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Araç sürümleri

Kotlin 2.4.21 · AGP 9.4.1 · Compose Multiplatform 1.12.1 · Gradle 9.8.0 · SQLDelight 2.4.1 ·
Koin 4.2.2 (compiler plugin 1.2.1)
(`gradle/libs.versions.toml`)

## Sonraki adımlar

- UI ekranları + navigasyon (ViewModel'ler `@KoinViewModel` ile)
- Veritabanı migration'ları (ilk sürüm yayınlanmadan önce; şema şu an `CREATE TABLE IF NOT EXISTS`)
- Maaş günü ayarı (`PlanCoachWeek` şimdilik boş liste veriyor)
- Yerel bildirim planlayıcı (Android `WorkManager` / iOS `UNUserNotificationCenter`)
- Abonelik (Play Billing + StoreKit, doğrulama RevenueCat), yedekleme (Drive / iCloud)
- Opsiyonel cihaz içi OCR fiş okuma (ML Kit / Apple Vision)
- iOS Xcode projesini oluşturma (`iosApp/` Swift kaynakları hazır)
