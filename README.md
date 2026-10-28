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
| `QuickEntryParser` | "195 market", "dün 120 su ekmek", "cuma 300 market", "625 ... yarısı geri geldi" → tutar/tarih/pay/kategori. Boşluksuz yazılan tutar da okunur ("40market", "195tl"; ayrı tutar yoksa, "3kg" gibi ölçüler hariç: `GluedAmount`). Gün adı en yakın geçmiş o günü verir ("geçen cuma" bugünü atlar); "pazar" yalnız "pazar günü" / "geçen pazar" ise tarihtir (yoksa semt pazarı) | `parser/` |
| `Categorizer` + `KeywordDictionary` | Anahtar kelime sözlüğü + kullanıcı düzeltmelerinden öğrenme | `category/` |
| `BudgetEngine` | Haftalık sınır, günlük kalan pay, ay toplamı; paylaşımda yalnız kullanıcı payı sayılır | `budget/` |
| `PayoffSimulator` | Kart kapanışı (~%4.9/ay faiz+vergi), taksit, esnek ödeme (Eminevim) | `debt/` |
| `Reconciler` | Elle girilen güncel borç ile kayıt farkını bulur ("103 ₺ yazmayı unutmuşsun") | `reconcile/` |
| `CoachRules` | 7 günün yerel bildirimlerini üretir; sessiz saat + günde max 2 + cümle varyasyonu | `coach/` |
| `ReceiptParser` / `OcrLayout` | Fişten/faturadan okunan yazıdan tutar ("TOPLAM", "Ödenecek tutar"), tarih, son ödeme tarihi, mağaza adı, kategori ve kartla ödendi ipucu. `OcrLayout` sütunlara bölünmüş satırları ("TOPLAM" … "*96,15") görüntüdeki hizalarına göre birleştirir | `receipt/` |
| `CashFlows` / `ForecastCashFlow` | Para takvimi: bugünden 5 hafta gün gün giriş (maaş) ve çıkış (sabit giderler ödeme günlerinde, kart ödemesi okunan ekstrenin son ödeme gününde, günlük harcama payı); girilen bakiyeyle gün sonu bakiyesi, ilk eksiye düşülen gün, maaştan önce kalan ve "günde ne kadar kısmalı". Taksit ve esnek borcun kalanı aşılmaz, kartla ödenen sabit giderler ekstrede sayıldığı için ayrıca düşülmez | `budget/CashFlow.kt` |
| `BackupService` / `ExpenseCsv` / `BackupTracker` | Tek dosyalık JSON yedek (tüm tablolar, kimlikleriyle; geri yükleme tek işlemde, öğrenilen kategoriler ve kumbara dahil. Yedek biçimi sürüm 2; kumbarasız eski sürüm 1 dosyaları da açılır), Excel'e uygun CSV (`;` ayraç, `1234,56`, UTF-8 BOM), son yedek günü | `backup/` |
| `StatementReader` | Banka uygulamasındaki kart ekranının **fotoğraflarından** (uygulamalar ekran görüntüsünü engeller; başka telefonla çekilir) ekstre okur: kalan ekstre borcu, asgari, son ödeme ve kesim tarihleri, dönem içi harcama, gelecek taksitler, limit, son 4 hane, hareketler (taksit, ödeme). Bankaya özel şablon yok: ortak etiket sözlüğü + değeri etiketin kendi satırında, sağında ya da altında (aynı sütun) arama. İki sayı biçimi ("52.331,00" / "52,331.00"), yılsız tarihler ("6 Ekim", "26 EYL") | `statement/` |
| `OcrScan` / `OcrCodec` | Okunan satırlar konumlarıyla; rotada taşınmak için düz metne çevrilir (fotoğraf saklanmaz) | `receipt/` |
| `BillMatcher` | Abonelik faturasını sabit gidere eşler: ad faturada geçiyorsa ya da kurum adı hizmeti ele veriyorsa ("Enerjisa" → Elektrik, "İGDAŞ" → Doğalgaz) | `receipt/` |
| `StreakCalculator` / `WeeklyLeftovers` | **Sınır serisi**: dünden geriye, günün harcaması günlük payın (haftalık sınır ÷ 7) içinde kaldığı gün sayısı (harcamasız gün de sayılır, ilk kayıttan öncesi sayılmaz; bugün henüz seriyi bozmaz ama aşıldıysa işaretlenir). Kayıt serisi, en uzun seriler, sınırın altında kapanan hafta sayısı. Geçen haftadan artan (kayıt yoksa ya da sınır aşıldıysa yok) | `gamification/Streaks.kt` |
| `Badges` / `BadgeStore` | 10 rozet (ilk kayıt, 3/7/30 gün sınırda, 7 gün kayıt, hafta tamam, ilk birikim, hedefe ulaşma, borç bitirme, yedek). Kazanılan rozet ve günü saklanır, seri bozulsa da gitmez; yeni kazanılanlar bir kez döner (kutlama için). Kilitlilerde ilerleme (4/7 gün) | `gamification/Badges.kt` |
| `GoalProgress` | Kumbara hedefinin biriken tutarı, doluluk oranı, kalan; eksi hareket = çekilen para | `domain/Savings.kt` |
| `SpendingBreakdowns` | "Nereye gitti?": ayın serbest harcamalarının kategori dağılımı, geçen ayla kıyas, en çok para giden kelimeler | `budget/SpendingBreakdown.kt` |

## Veri katmanı ve DI (`shared/.../`)

| Bileşen | Sorumluluk | Dosya |
|---|---|---|
| `ExpenseRepository` / `CardRepository` / `BudgetRepository` / `IncomeRepository` | Arayüzler; tarih aralıkları iki uç dahil, `observe*` Flow döner | `domain/repository/` |
| `Sql*Repository` | SQLDelight implementasyonları; tarih epoch-day, tutar kuruş saklanır | `data/` |
| `SqlCorrectionStore` | Kategori düzeltmelerini kalıcı tutar (ilk erişimde belleğe alınır) | `data/` |
| `ObserveBudgetStatus` | Bütçe + harcamaları izler, `BudgetEngine` ile canlı `BudgetStatus` yayar | `budget/` |
| `ObserveMonthSummary` | Bu ayın özeti: gelir − sabit giderler − serbest harcamalar − bu ay kumbaraya ayrılan; sabit giderlerin ödendi/bekliyor durumu; sonraki gelir günü | `budget/` |
| `ObserveSpendingBreakdown` | Bir ayın `SpendingBreakdown`ını izler. İçinde bulunulan ay geçen ayın **aynı günleriyle** (1–20 Ekim ↔ 1–20 Eylül), biten ay geçen ayın tamamıyla kıyaslanır; sabit gider ödemeleri hariç | `budget/SpendingBreakdown.kt` |
| `FixedPayments` / `WeeklyLimitSuggestion` | Sabit gideri "ödendi" işaretleme (bağlı harcama kaydı) ve gelirden haftalık sınır önerisi | `budget/FixedPayments.kt` |
| `CardStatementRepository` | Okunan ekstreler; her okuma ayrı kayıt (ödeme ilerlemesi: "52.331 → 31.393") | `domain/repository/`, `data/SqlCardStatementRepository.kt` |
| `SavingsRepository` | Kumbara hedefleri ve para hareketleri (ekleme artı, çekme eksi); hedef silinince hareketleri de silinir | `domain/repository/`, `data/SqlSavingsRepository.kt` |
| `ObserveGamification` | Bütçe, son 120 günün harcamaları, kumbara, biten borçlar ve son yedekten seriler, rozet durumu, geçen haftanın artanı ve hedefler. Yan etkisiz; rozetleri kaydetmek ana ekran şeridinin işi | `gamification/` |
| `PlanCoachWeek` | Bütçe, son 30 gün, kart hesap kesim günleri ve en büyük gelirin gününden `CoachRules` planını üretir | `coach/` |
| `DataModule` / `DomainModule` / `PlatformModule` | Koin modülleri (annotation, `@Configuration`) | `di/` |

Koin **annotation tabanlı** ve DSL kullanılmıyor. Modülleri Koin compiler plugin topluyor, KSP gerekmiyor. Giriş noktası
`composeApp/.../di/CinchKoinApp.kt` (`@KoinApplication`) + `StartKoin.doInitKoin()`. Android'de
`CinchApplication` önce `initPlatform(context)` çağırır; iOS'ta `iOSApp.init` Koin'i başlatır.
Eksik bağımlılık derleme anında hata verir (compile safety).

**Şema sürümleri:** `Cinch.sq` güncel şemadır; her değişiklik ayrıca `<eski sürüm>.sqm` dosyasına migration
olarak yazılır (`1.sqm`: gelir tablosu, bütçedeki maaş günü "Maaş" gelirine taşınır; `2.sqm`: sabit gider
tablosu ve harcamaya `fixed_expense_id`; `3.sqm`: sabit giderlere ödeme şekli, taksit ve borç alanları; `4.sqm`: kartlara son 4 hane, okunan ekstreler tablosu; `5.sqm`: kart türü, kredi / banka; `6.sqm`: kumbara hedefleri ve para hareketleri; şema sürümü 7). Telefondaki eski
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

**Yedek:** son yedek 30 günden eskiyse ya da hiç yedek yokken 20'den fazla kayıt birikmişse haftada bir (haftanın 2.
günü 19:00) hatırlatılır.

**Kart son ödemesi:** okunan son ekstrenin son ödeme tarihinden 3 gün önce 10:00'da ve o gün 09:30'da, kalan borç ve
asgariyle hatırlatılır. Borç sıfır okunduysa (ödenmiş) hatırlatılmaz; ödedikten sonra ekranı yeniden okutmak yeter.

**Kumbara:** geçen hafta kayıt girilip sınırın altında kalındıysa haftanın ilk günü 10:30'da artan söylenir
("Geçen hafta 340 ₺ artırdın 🎉 Kumbarana atmak için Cinch'e bir dokun").

**Kategori uyarısı:** haftanın 5. günü (pazartesi başlayan haftada cuma) 17:30'da, bu hafta bir kategoriye geçen
haftanın aynı günlerinden en az %30 ve haftalık sınırın en az %10'u kadar fazla gidildiyse koç söyler ("Bu hafta
yemeğe geçen haftadan %40 fazla gitti"). Yalnız o gün ve o günün verisiyle üretilir, haftada en fazla bir kez.

## Ekranlar (`composeApp/.../`)

Alt menüde dört sekme var: **Bugün**, **Bu ay**, **Kartlar**, **Ayarlar**. Her sekmenin kendi geri yığını var;
sekme değişince ekran durumu korunur, alt menü yalnız sekme köklerinde görünür.

| Ekran | Ne yapar | Dosyalar |
|---|---|---|
| Bütçe kurulumu | İlk açılışta (bütçe yoksa) alt menüsüz açılır; haftalık harcama sınırı ve hafta başlangıcı. Gelir ve sabit giderler girildiyse haftalık sınır önerir ("Bunu kullan"). Sonra Ayarlar'dan düzenlenir | `ui/budget/` |
| Bugün | Bugün kalan pay, haftalık ilerleme, ay toplamı; bu haftanın harcamaları (dokun → düzenle, sola kaydır → sil, "Geri al"); "Tüm geçmiş". Üstte oyunsu şerit: "🔥 7 gün sınırda" (→ Başarılar), "🐷 Kumbara 2.340 ₺" (→ Kumbara); bugün payı aşılmışken seri en az 3 günse "seriyi korumak için bugün dur" uyarısı; geçen haftadan artan varsa "Kumbaraya at" kartı (hedefe tek dokunuş, "Şimdi değil" ya da "Hedef oluştur"). Yeni rozet kazanılınca konfeti ve mesaj (bir kez) | `ui/home/`, `ui/progress/ProgressStrip*` |
| Başarılar | Sınır serisi (bugün, en uzun, kayıt serisi, sınırda kapanan haftalar) ve rozetler: kazanılanlar günüyle, kilitliler ne gerektiği ve ilerleme çubuğuyla | `ui/progress/Achievements*` |
| Kumbara | Hedefler (ad, simge, hedef tutar): ilerleme, kalan, son hareketler (✕ ile silinir); "+ Ekle" (geçen haftanın artanı önerilir), "Çek" (biriken kadar), düzenle/sil. Hedef dolunca konfeti. Para bankadan çıkmaz; ayrılan tutar "Bu ay elinde kalan"dan düşer | `ui/savings/` |
| Harcama düzenleme | Tutar, açıklama, kategori, tarih (takvim), "senin payın" (tamamı / yarısı / hiç) ve kart; silme. Tutar değişip açıklamaya dokunulmadıysa açıklamadaki sayı da güncellenir ("195 market" → "200 market"). Kategori düzeltilirse kelime öğrenilir. Sabit gider ödemesinde yalnız tutar, tarih ve kart | `ui/history/ExpenseEdit*` |
| Geçmiş | Ay ay liste (gün başlıkları ve gün toplamı), kategori süzgeci; arama kutusu doluysa tüm geçmişte açıklama, not, kategori ve kart adında arar (Türkçe harf farkı gözetmez) | `ui/history/History*` |
| Fiş / fatura okuma | Hızlı kayıt alanındaki 📷: "Fiş ya da fatura tara" (ML Kit belge tarayıcı: kenar bulur, kırpar) ya da "Galeriden seç". Yazı telefonda okunur, fotoğraf saklanmaz, internete gitmez. Onay ekranında tutar, açıklama, kategori, tarih ve kart dolu gelir; kullanıcı kontrol edip kaydeder. Faturada eşleşen sabit gider seçili gelir ve "Bu ay"da ödendi işaretlenir (bu ay zaten ödendiyse uyarır). "Bu ay"daki "📷 Fatura" düğmesi de aynı akışı açar. Şimdilik yalnız Android; iOS'ta düğmeler gizli | `receipt/` (platform), `ui/receipt/` |
| Nereye gitti? | "Bu ay"daki özet kartından açılır: halka grafik, geçen ayla kıyas, kategoriler (pay çubuğu, ↑/↓ değişim; dokun → o ayın o kategorideki kayıtları), en çok para giden kelimeler; ay ay gezilir | `ui/insights/` |
| Bu ay | Gelir − sabit giderler − harcamalar − kumbaraya ayrılan ("🐷 Kumbaraya" satırı) = elinde kalan; Kumbara kartı; sonraki gelire kaç gün kaldığı; sabit giderler (yuvarlağa dokun → "ödendi", tutar düzeltilebilir; borçlarda ilerleme çubuğu, borca dokun → detay: ilerleme, bitiş tahmini, ödeme ekle/sil) ve gelirler, ekleme/düzenleme | `ui/month/` |
| Ayarlar | Haftalık bütçe, koç bildirimleri (izin durumu), verilerin nerede tutulduğu; **yedek al** (sistemin kaydetme penceresi: telefon, Drive…), **yedekten geri yükle** (önce içindekileri gösterip onay ister), **harcamaları Excel'e aktar** (CSV). Son yedek 30 günü geçince uyarı rengiyle | `ui/settings/`, `files/` (platform) |
| Hızlı kayıt | Alttaki alan: "195 market" yazdıkça tutar/kategori/tarih önizlenir; kategori düzeltilirse kelime öğrenilir. Kart adı geçerse ("195 market qnb") harcama o karta bağlanır, çipten değiştirilir. Tarih "dün", "cuma" yazınca değişir; 📅 çipinden takvimle de seçilir | `ui/home/QuickEntryBar.kt` |
| Kartlar | Kart listesi; ekleme/düzenleme: **kredi kartı** (ad, hesap kesim günü, ödenmemiş eski borç, aylık faiz) ya da **banka kartı** (yalnız ad: ekstre, borç, faiz yok; hareketlerden eksik harcama bulma için). Silince harcamalar nakit kalır | `ui/cards/Cards*`, `CardEdit*` |
| Para takvimi | "Bu ay"daki "📅 Para takvimi": hesaptaki parayı yazınca "Maaşa (15 Kas) kadar yetiyor, maaştan önceki gün ~3.200 ₺ kalır" ya da "27 Eki'de eksiye düşüyorsun; günde ~300 ₺ az harcarsan yetiyor". Altında hareketli günler ve gün sonu bakiyesi; ekstresi okunmamış kredi kartı varsa uyarır | `ui/cashflow/` |
| Ekstre okuma | Kart detayında "🖼️ Fotoğraf seç" / "📷 Tara" ya da Kartlar'daki "📸 Ekstre oku": banka uygulamasındaki kart ekranının fotoğrafları (en fazla 6; özet, "daha fazla", hareketler). Onay ekranında kart (son 4 haneden kendiliğinden), son ödeme, kalan borç, asgari, kesim tarihi; bankadaki hareketler bu kartın kayıtlarıyla eşlenir: eşleşen ✅, eksik ➕ (seçili gelir, kaydedince eklenir), taksit 🔁 (seçilmeden), ödeme 💸 | `ui/statement/` |
| Kart detayı | Okunan son ekstre: son ödeme ve kalan gün, kalan borç ve ödeme ilerlemesi, "yalnız asgariyi ödersen ~X faiz", limit doluluğu / aşımı, gelecek taksitler, **kayıt tutma oranı** ("kart harcamalarının %92'sini girmişsin"), **son 6 ekstrenin grafiği** ve öncekine göre değişim. Bu ekstreye girecek harcamalar; "bankayla karşılaştır" (eksik farkı tek dokunuşla ekleme); eski borcun aylık ödemeyle ne zaman biteceği ve toplam faizi | `ui/cards/CardDetail*` |

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

## Ana ekran widget'ı (Android, `composeApp/src/androidMain/.../widget/`)

`TodayWidget` (Glance): "Bugün harcayabileceğin 640 ₺" (aşıldıysa kırmızımsı zeminde "Bugünkü payını aştın"), bu
haftanın harcaması / sınır, 🔥 sınır serisi. Bütçe kurulmadıysa "kurmak için dokun". Widget'a dokununca uygulama,
**"+ Harcama"** düğmesine dokununca `QuickAddActivity` açılır: ana ekranın üstünde yalnız hızlı kayıt alanı
(klavye açık, "195 market" yaz → kaydet → "Kaydedildi ✓", pencere kapanır; boşluğa dokununca da kapanır). Ana
ekranın `HomeViewModel`'ini kullanır, kart/kategori/tarih çözümü aynıdır.

Güncelleme: `TodayWidgetUpdater` (uygulama açılışında kurulur) bütçe ya da harcama değişince 1 sn bekleyip
widget'ı yeniler; gün değişimi için Android ayrıca yarım saatte bir yeniler (`res/xml/today_widget_info.xml`).
iOS'ta widget yok (WidgetKit ayrı bir Swift hedefi gerektirir).

## Koç bildirimleri (`composeApp/.../notifications/`)

`CoachRules` 7 günlük planı üretir; `CoachNotificationSync` bunu platform zamanlayıcısıyla eşit tutar.
Eşitleme farka göre yapılır: plandan düşen iptal edilir, eksik eklenir, aynısı kalana dokunulmaz.
Plan şu durumlarda yeniden kurulur: Koin açılışı, bütçe/kart/harcama değişikliği ve uygulamanın öne gelmesi.

Bazı bildirimler önceden planlanır (`leadDays`): "dün kayıt yoktu", "sınıra yaklaşıyorsun" ve seri bir gün sonra,
alışkanlık uyarısı iki gün sonra (haftada bir, haftanın 3. günü). Plan her gün yeniden kurulduğundan
`PlanCoachWeek`, bugüne düşenleri planlandıkları günün gözünden ama güncel veriyle yeniden kurup bugünün planına
ekler. Böylece yeniden planlama onları ertesi güne itmez, Android de gösterirken bulur. Sınıra yaklaşma uyarısı
yeni haftaya taşınmaz.

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
Koin 4.2.2 (compiler plugin 1.2.1) · Navigation 3 1.2.0-rc01 · WorkManager 2.12.0 · Glance 1.1.1 · Material ikonları 1.7.3
(`gradle/libs.versions.toml`)

## Sonraki adımlar

- İlk açılışta ekranı tanıtan odaklı balonlar (coach mark); yuvarlak hatlı özel yazı tipi (ör. Nunito)
- Bildirim saatlerini (sessiz saatler) saat seçiciyle ayarlama
- Bildirim ayarları (sessiz saatler, günlük sayı, türleri aç/kapa); bildirime dokununca ilgili ekrana gitme
- Abonelik (Play Billing + StoreKit, doğrulama RevenueCat), yedekleme (Drive / iCloud)
- iOS'ta fiş okuma: VisionKit tarayıcı + Vision metin tanıma (`composeApp/src/iosMain/.../receipt/ReceiptCapture.ios.kt` şimdilik boş)
- iOS'ta yedek dosyası seçici: `UIDocumentPickerViewController` (`composeApp/src/iosMain/.../files/FileTransfer.ios.kt` şimdilik boş)
- iOS'ta ana ekran widget'ı (WidgetKit; veriyi App Group ile paylaşma) ve hızlı kayıt için App Intent
- Kumbara hedefine tarih koyup "haftada ne kadar atmalısın" önerisi
- iOS Xcode projesini oluşturma (`iosApp/` Swift kaynakları hazır)
