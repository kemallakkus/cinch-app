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
| `QuickEntryParser` | "195 market", "dün 120 su ekmek", "cuma 300 market", "625 ... yarısı geri geldi" → tutar/tarih/pay/kategori. Gün adı en yakın geçmiş o günü verir ("geçen cuma" bugünü atlar); "pazar" yalnız "pazar günü" / "geçen pazar" ise tarihtir (yoksa semt pazarı) | `parser/` |
| `Categorizer` + `KeywordDictionary` | Anahtar kelime sözlüğü + kullanıcı düzeltmelerinden öğrenme | `category/` |
| `BudgetEngine` | Haftalık sınır, günlük kalan pay, ay toplamı; paylaşımda yalnız kullanıcı payı sayılır | `budget/` |
| `PayoffSimulator` | Kart kapanışı (~%4.9/ay faiz+vergi), taksit, esnek ödeme (Eminevim) | `debt/` |
| `Reconciler` | Elle girilen güncel borç ile kayıt farkını bulur ("103 ₺ yazmayı unutmuşsun") | `reconcile/` |
| `CoachRules` | 7 günün yerel bildirimlerini üretir; sessiz saat + günde max 2 + cümle varyasyonu | `coach/` |
| `SpendingBreakdowns` | "Nereye gitti?": ayın serbest harcamalarının kategori dağılımı, geçen ayla kıyas, en çok para giden kelimeler | `budget/SpendingBreakdown.kt` |

## Veri katmanı ve DI (`shared/.../`)

| Bileşen | Sorumluluk | Dosya |
|---|---|---|
| `ExpenseRepository` / `CardRepository` / `BudgetRepository` / `IncomeRepository` | Arayüzler; tarih aralıkları iki uç dahil, `observe*` Flow döner | `domain/repository/` |
| `Sql*Repository` | SQLDelight implementasyonları; tarih epoch-day, tutar kuruş saklanır | `data/` |
| `SqlCorrectionStore` | Kategori düzeltmelerini kalıcı tutar (ilk erişimde belleğe alınır) | `data/` |
| `ObserveBudgetStatus` | Bütçe + harcamaları izler, `BudgetEngine` ile canlı `BudgetStatus` yayar | `budget/` |
| `ObserveMonthSummary` | Bu ayın özeti: gelir − sabit giderler − serbest harcamalar; sabit giderlerin ödendi/bekliyor durumu; sonraki gelir günü | `budget/` |
| `ObserveSpendingBreakdown` | Bir ayın `SpendingBreakdown`ını izler. İçinde bulunulan ay geçen ayın **aynı günleriyle** (1–20 Ekim ↔ 1–20 Eylül), biten ay geçen ayın tamamıyla kıyaslanır; sabit gider ödemeleri hariç | `budget/SpendingBreakdown.kt` |
| `FixedPayments` / `WeeklyLimitSuggestion` | Sabit gideri "ödendi" işaretleme (bağlı harcama kaydı) ve gelirden haftalık sınır önerisi | `budget/FixedPayments.kt` |
| `PlanCoachWeek` | Bütçe, son 30 gün, kart hesap kesim günleri ve en büyük gelirin gününden `CoachRules` planını üretir | `coach/` |
| `DataModule` / `DomainModule` / `PlatformModule` | Koin modülleri (annotation, `@Configuration`) | `di/` |

Koin **annotation tabanlı** ve DSL kullanılmıyor. Modülleri Koin compiler plugin topluyor, KSP gerekmiyor. Giriş noktası
`composeApp/.../di/CinchKoinApp.kt` (`@KoinApplication`) + `StartKoin.doInitKoin()`. Android'de
`CinchApplication` önce `initPlatform(context)` çağırır; iOS'ta `iOSApp.init` Koin'i başlatır.
Eksik bağımlılık derleme anında hata verir (compile safety).

**Şema sürümleri:** `Cinch.sq` güncel şemadır; her değişiklik ayrıca `<eski sürüm>.sqm` dosyasına migration
olarak yazılır (`1.sqm`: gelir tablosu, bütçedeki maaş günü "Maaş" gelirine taşınır; `2.sqm`: sabit gider
tablosu ve harcamaya `fixed_expense_id`; `3.sqm`: sabit giderlere ödeme şekli, taksit ve borç alanları). Telefondaki eski
kurulum açılışta veri kaybetmeden yükseltilir; `MigrationTest` bunu doğrular. Yeni şema değişikliğinde
bir sonraki `<n>.sqm` eklenir, `Cinch.sq` güncellenir.

**Sabit giderler** (kira, fatura, abonelik, kredi taksiti) haftalık serbest harcama bütçesinden ayrıdır. "Ödendi"
işareti, sabit gidere bağlı bir harcama kaydı oluşturur (`Expense.fixedExpenseId`): günlük/haftalık paya ve
alışkanlık sayımına girmez, ay hesabına girer; kartla ödeniyorsa o kartın "bankayla karşılaştır" hesabına da yazılır.
Ödenmemiş sabit gider ay özetinde planlanan tutarıyla düşülür.

Üç ödeme şekli var (`FixedPlan`): **her ay** (bitişi yok), **taksitli** (toplam + önceden ödenen taksit; son
taksitte biter) ve **esnek borç** (Eminevim gibi: kalan borç, her ay farklı tutar; borç sıfırlanınca biter).
Taksit sayısı ve kalan borç saklanmaz, geçmiş ödemelerden hesaplanır (`DebtProgress`). Biten borç sonraki aylarda
ay hesabına ve hatırlatmalara girmez, "Biten borçlar"a geçer. Esnek borçta bitiş tahmini "genelde ödediğim"
tutarla, o yoksa son 3 ödemenin ortalamasıyla yapılır. Koç ödeme gününden bir gün önce (19:00) ve o gün
akşam hâlâ işaretlenmemişse (20:30) hatırlatır.

**Kategori uyarısı:** haftanın 5. günü (pazartesi başlayan haftada cuma) 17:30'da, bu hafta bir kategoriye geçen
haftanın aynı günlerinden en az %30 ve haftalık sınırın en az %10'u kadar fazla gidildiyse koç söyler ("Bu hafta
yemeğe geçen haftadan %40 fazla gitti"). Yalnız o gün ve o günün verisiyle üretilir, haftada en fazla bir kez.

## Ekranlar (`composeApp/.../`)

Alt menüde dört sekme var: **Bugün**, **Bu ay**, **Kartlar**, **Ayarlar**. Her sekmenin kendi geri yığını var;
sekme değişince ekran durumu korunur, alt menü yalnız sekme köklerinde görünür.

| Ekran | Ne yapar | Dosyalar |
|---|---|---|
| Bütçe kurulumu | İlk açılışta (bütçe yoksa) alt menüsüz açılır; haftalık harcama sınırı ve hafta başlangıcı. Gelir ve sabit giderler girildiyse haftalık sınır önerir ("Bunu kullan"). Sonra Ayarlar'dan düzenlenir | `ui/budget/` |
| Bugün | Bugün kalan pay, haftalık ilerleme, ay toplamı; bu haftanın harcamaları (dokun → düzenle, sola kaydır → sil, "Geri al"); "Tüm geçmiş" | `ui/home/` |
| Harcama düzenleme | Tutar, açıklama, kategori, tarih (takvim), "senin payın" (tamamı / yarısı / hiç) ve kart; silme. Tutar değişip açıklamaya dokunulmadıysa açıklamadaki sayı da güncellenir ("195 market" → "200 market"). Kategori düzeltilirse kelime öğrenilir. Sabit gider ödemesinde yalnız tutar, tarih ve kart | `ui/history/ExpenseEdit*` |
| Geçmiş | Ay ay liste (gün başlıkları ve gün toplamı), kategori süzgeci; arama kutusu doluysa tüm geçmişte açıklama, not, kategori ve kart adında arar (Türkçe harf farkı gözetmez) | `ui/history/History*` |
| Nereye gitti? | "Bu ay"daki özet kartından açılır: halka grafik, geçen ayla kıyas, kategoriler (pay çubuğu, ↑/↓ değişim; dokun → o ayın o kategorideki kayıtları), en çok para giden kelimeler; ay ay gezilir | `ui/insights/` |
| Bu ay | Gelir − sabit giderler − harcamalar = elinde kalan; sonraki gelire kaç gün kaldığı; sabit giderler (yuvarlağa dokun → "ödendi", tutar düzeltilebilir; borçlarda ilerleme çubuğu, borca dokun → detay: ilerleme, bitiş tahmini, ödeme ekle/sil) ve gelirler, ekleme/düzenleme | `ui/month/` |
| Ayarlar | Haftalık bütçe, koç bildirimleri (izin durumu), verilerin nerede tutulduğu | `ui/settings/` |
| Hızlı kayıt | Alttaki alan: "195 market" yazdıkça tutar/kategori/tarih önizlenir; kategori düzeltilirse kelime öğrenilir. Kart adı geçerse ("195 market qnb") harcama o karta bağlanır, çipten değiştirilir. Tarih "dün", "cuma" yazınca değişir; 📅 çipinden takvimle de seçilir | `ui/home/QuickEntryBar.kt` |
| Kartlar | Kart listesi; ekleme/düzenleme (ad, hesap kesim günü, ödenmemiş eski borç, aylık faiz), silince harcamalar nakit kalır | `ui/cards/Cards*`, `CardEdit*` |
| Kart detayı | Bu ekstreye girecek harcamalar; "bankayla karşılaştır" (eksik farkı tek dokunuşla ekleme); eski borcun aylık ödemeyle ne zaman biteceği ve toplam faizi | `ui/cards/CardDetail*` |

**Görünüm (sıcak ve oyunsu):** krem zemin, mercan/şeftali vurgular; nane yeşili "iyi gidiyorsun", bal sarısı
"dikkat". Büyük yuvarlak köşeler, her kategoriye kendi pastel rengi, ana rakamlar degrade kartlarda ve değişince
sayarak akar. Bugün ekranında haftanın 7 günü için küçük çubuklar (günün harcaması / haftalık sınırın yedide biri).
Kayıt payın içinde kalınca ve bankayla karşılaştırma tutunca küçük bir konfeti patlar. Tema `ui/theme/CinchTheme.kt`
(`CinchTheme.colors`, `Category.colors()`), oyunsu parçalar `ui/common/Playful.kt` ve `Celebration.kt`.

Teknik terimler ekranda sade dille yazılır; gerektiğinde yanındaki "i" balonu açıklar (`ui/common/Components.kt`:
`InfoTip`, ayın günü için takvim ızgarası `DayOfMonthField`).

Navigation 3 (sekme başına geri yığını, `navigation/CinchNavHost.kt`); her ekran `Wrapper` (ViewModel + effect) →
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
Koin 4.2.2 (compiler plugin 1.2.1) · Navigation 3 1.2.0-rc01 · WorkManager 2.12.0 · Material ikonları 1.7.3
(`gradle/libs.versions.toml`)

## Sonraki adımlar

- İlk açılışta ekranı tanıtan odaklı balonlar (coach mark); yuvarlak hatlı özel yazı tipi (ör. Nunito)
- Bildirim saatlerini (sessiz saatler) saat seçiciyle ayarlama
- Bildirim ayarları (sessiz saatler, günlük sayı, türleri aç/kapa); bildirime dokununca ilgili ekrana gitme
- Abonelik (Play Billing + StoreKit, doğrulama RevenueCat), yedekleme (Drive / iCloud)
- Opsiyonel cihaz içi OCR fiş okuma (ML Kit / Apple Vision)
- iOS Xcode projesini oluşturma (`iosApp/` Swift kaynakları hazır)
