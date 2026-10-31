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
└─ iosApp/        SwiftUI kabuğu, WidgetKit eklentisi, Siri kısayolu; Xcode projesi `project.yml`den (XcodeGen) üretilir
```

> AGP 9'da `com.android.application` + `kotlin.multiplatform` aynı modülde kullanılamadığından
> Android uygulaması ayrı ince bir application modülüdür; Compose UI ve `MainActivity`
> `composeApp` KMP kütüphanesinde yaşar.

## Domain çekirdeği (`shared/.../`)

| Bileşen | Sorumluluk | Dosya |
|---|---|---|
| `Money` / `MoneyFormatter` | Kuruş bazlı tutar (float hatasız), TR/EN biçim | `money/` |
| `QuickEntryParser` | "195 market", "dün 120 su ekmek", "cuma 300 market", "625 ... yarısı geri geldi" → tutar/tarih/pay/kategori. Boşluksuz yazılan tutar da okunur ("40market", "195tl"; ayrı tutar yoksa, "3kg" gibi ölçüler hariç: `GluedAmount`). Gün adı en yakın geçmiş o günü verir ("geçen cuma" bugünü atlar); "pazar" yalnız "pazar günü" / "geçen pazar" ise tarihtir (yoksa semt pazarı) | `parser/` |
| `Categorizer` + `KeywordDictionary` | Anahtar kelime sözlüğü (dil paketinden) + kullanıcı düzeltmelerinden öğrenme | `category/` |
| `LanguagePack` / `LanguagePacks` | Ayrıştırıcıların bütün kelimeleri tek yerde, dil dil: kategori sözlüğü, hızlı kayıt (bugün/dün/önceki gün, gün adları, "geçen", paylaşım ve iade kalıpları, para birimi ve ölçü ekleri), fiş (toplam etiketleri ve puanları, dışlananlar, KDV, son ödeme, mağaza adında atlananlar), banka ekranı (alan adları, ay adları), "Nereye gitti?" bağlaçları. Türkçe ve İngilizce var; `active(dil)` telefonun dilini önce koyup Türkçe ve İngilizceyle birleştirir: kullanıcı hangi dilde yazarsa yazsın anlaşılır, çakışan kelimede telefonun dili geçerli. Yeni dil = yeni paket, ayrıştırıcı koduna dokunulmaz | `language/` |
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
| `CardOverviews` / `ObserveCardOverviews` | Kartın durumu (banka kartı, okunmadı, ödenecek, son ödeme geçti, ödendi, okuma eski), ödenecek tutar, son ödemeye kalan gün, bu dönemin harcaması ve banka okumasına göre eksik; kart listesi ve detay aynı hesabı kullanır. `paidReading`: "Ödedim" için sıfır borçlu okuma | `cards/` |
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
| `DataModule` / `DomainModule` / `PlatformModule` | Koin modülleri (annotation, `@Configuration`); `DomainModule` telefonun dilinden (`deviceLanguageCode`) birleşik dil paketini kurup ayrıştırıcılara verir | `di/` |

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
| Tanıtım | İlk açılışta, bütçe kurulumundan önce dört kaydırmalı sayfa: günlük pay (sayarak gelen tutar), "yaz, Cinch anlasın" (harf harf yazılan örnek ve anlaşılan kayıt), seri/rozet/kumbara, verilerin telefonda kalması. "Geç" ya da "Bütçemi kuralım" ile biter, bir daha gösterilmez; bütçesi olan (eski sürümden gelen) kullanıcı görmez | `ui/onboarding/IntroScreen.kt`, `ui/root/` |
| Ekran balonları | Bugün, Bu ay ve Kartlar'da ilk girişte ekranı karartıp tek bir öğeyi aydınlatan balonlar (bugünkü pay, hızlı kayıt, seri, geçmiş; ay hesabı, para takvimi, sabit giderler; kart ekleme, ekstre okuma). Her balon bir kez gösterilir; hedefi henüz ekranda olmayan balon (ör. kart eklenmeden "Ekstre oku") hedef ilk göründüğünde çıkar. "Geç" o ekranın hepsini kapatır; Ayarlar'dan yeniden gösterilir | `ui/onboarding/CoachMarks.kt`, `CoachMarkSteps.kt` |
| Bütçe kurulumu | İlk açılışta (tanıtımdan sonra, bütçe yoksa) alt menüsüz açılır; haftalık harcama sınırı ve hafta başlangıcı. Gelir ve sabit giderler girildiyse haftalık sınır önerir ("Bunu kullan"). Sonra Ayarlar'dan düzenlenir | `ui/budget/` |
| Bugün | Bugün kalan pay, haftalık ilerleme, ay toplamı; bu haftanın harcamaları (dokun → düzenle, sola kaydır → sil, "Geri al"); "Tüm geçmiş". Üstte oyunsu şerit: "🔥 7 gün sınırda" (→ Başarılar), "💰 Kumbara 2.340 ₺" (→ Kumbara); bugün payı aşılmışken seri en az 3 günse "seriyi korumak için bugün dur" uyarısı; geçen haftadan artan varsa "Kumbaraya at" kartı (hedefe tek dokunuş, "Şimdi değil" ya da "Hedef oluştur"). Yeni rozet kazanılınca konfeti ve mesaj (bir kez) | `ui/home/`, `ui/progress/ProgressStrip*` |
| Başarılar | Sınır serisi (bugün, en uzun, kayıt serisi, sınırda kapanan haftalar) ve rozetler: kazanılanlar günüyle, kilitliler ne gerektiği ve ilerleme çubuğuyla | `ui/progress/Achievements*` |
| Kumbara | Hedefler (ad, simge, hedef tutar): ilerleme, kalan, son hareketler (✕ ile silinir); "+ Ekle" (geçen haftanın artanı önerilir), "Çek" (biriken kadar), düzenle/sil. Hedef dolunca konfeti. Para bankadan çıkmaz; ayrılan tutar "Bu ay elinde kalan"dan düşer | `ui/savings/` |
| Harcama düzenleme | Tutar, açıklama, kategori, tarih (takvim), "senin payın" (tamamı / yarısı / hiç) ve kart; silme. Tutar değişip açıklamaya dokunulmadıysa açıklamadaki sayı da güncellenir ("195 market" → "200 market"). Kategori düzeltilirse kelime öğrenilir. Sabit gider ödemesinde yalnız tutar, tarih ve kart | `ui/history/ExpenseEdit*` |
| Geçmiş | Ay ay liste (gün başlıkları ve gün toplamı), kategori süzgeci; arama kutusu doluysa tüm geçmişte açıklama, not, kategori ve kart adında arar (Türkçe harf farkı gözetmez) | `ui/history/History*` |
| Fiş / fatura okuma | Hızlı kayıt alanındaki 📷: "Fiş ya da fatura tara" (ML Kit belge tarayıcı: kenar bulur, kırpar) ya da "Galeriden seç". Yazı telefonda okunur, fotoğraf saklanmaz, internete gitmez. Onay ekranında tutar, açıklama, kategori, tarih ve kart dolu gelir; kullanıcı kontrol edip kaydeder. Faturada eşleşen sabit gider seçili gelir ve "Bu ay"da ödendi işaretlenir (bu ay zaten ödendiyse uyarır). "Bu ay"daki "📷 Fatura" düğmesi de aynı akışı açar. iOS'ta VisionKit belge tarayıcı (simülatörde yoksa galeriye düşer), `PHPickerViewController` ve Vision metin tanıma (Türkçe destekleniyorsa tr-TR, yoksa en-US); konumlar ML Kit'teki gibi sol üst köşeden piksel olarak verilir, ekstre okuma da aynı yolu kullanır | `receipt/` (platform), `ui/receipt/` |
| Nereye gitti? | "Bu ay"daki özet kartından açılır: halka grafik, geçen ayla kıyas, kategoriler (pay çubuğu, ↑/↓ değişim; dokun → o ayın o kategorideki kayıtları), en çok para giden kelimeler; ay ay gezilir | `ui/insights/` |
| Bu ay | Gelir − sabit giderler − harcamalar − kumbaraya ayrılan ("💰 Kumbaraya" satırı) = elinde kalan; Kumbara kartı; sonraki gelire kaç gün kaldığı; sabit giderler (yuvarlağa dokun → "ödendi", tutar düzeltilebilir; borçlarda ilerleme çubuğu, borca dokun → detay: ilerleme, bitiş tahmini, ödeme ekle/sil) ve gelirler, ekleme/düzenleme | `ui/month/` |
| Ayarlar | Haftalık bütçe, koç bildirimleri (izin durumu) ve **bildirim tercihleri** (sessiz saatler, günün payı saati, günde en fazla, gruplar), verilerin nerede tutulduğu; **yedek al** (sistemin kaydetme penceresi: telefon, Drive…), **yedekten geri yükle** (önce içindekileri gösterip onay ister), **harcamaları Excel'e aktar** (CSV). Son yedek 30 günü geçince uyarı rengiyle; **ekran tanıtımlarını yeniden göster**. Yedek ve CSV iOS'ta sistemin dosya penceresiyle (Dosyalar, iCloud Drive…) | `ui/settings/`, `files/` (platform) |
| Hızlı kayıt | Alttaki alan: "195 market" yazdıkça tutar/kategori/tarih önizlenir; kategori düzeltilirse kelime öğrenilir. Kart adı geçerse ("195 market qnb") harcama o karta bağlanır, çipten değiştirilir. Tarih "dün", "cuma" yazınca değişir; 📅 çipinden takvimle de seçilir | `ui/home/QuickEntryBar.kt` |
| Kartlar | Kart listesi: her kutucukta kredi kartının **ödenecek tutarı ve son ödemeye kalan gün** ("52.331 ₺ · 6 Eki, 2 gün kaldı"), ekstre okunmadıysa bu dönemin harcaması ve "📸 Ekstreni okut", banka kartında bu ayın harcaması; son ödeme 2 gün içinde ya da geçtiyse vurgulu. Ekleme tek adım: ad ve kartın nasıl ödediği ("Ay sonunda ekstreyle ödenir" / "Hesabımdan hemen çıkar"); kesim günü ve faiz "Gelişmiş"te (banka ekranından okunur), elle eski borç yalnız eski kayıtlarda. Yeni kart eklenince kartın sayfası açılır. Silince harcamalar nakit kalır | `ui/cards/Cards*`, `CardEdit*`, `CardLabels.kt` |
| Para takvimi | "Bu ay"daki "📅 Para takvimi": hesaptaki parayı yazınca "Maaşa (15 Kas) kadar yetiyor, maaştan önceki gün ~3.200 ₺ kalır" ya da "27 Eki'de eksiye düşüyorsun; günde ~300 ₺ az harcarsan yetiyor". Altında hareketli günler ve gün sonu bakiyesi; ekstresi okunmamış kredi kartı varsa uyarır | `ui/cashflow/` |
| Ekstre okuma | Kart detayında "🖼️ Fotoğraf seç" / "📷 Tara" ya da Kartlar'daki "📸 Ekstre oku": banka uygulamasındaki kart ekranının fotoğrafları (en fazla 6; özet, "daha fazla", hareketler). Onay ekranında kart (son 4 haneden kendiliğinden), son ödeme, kalan borç, asgari, kesim tarihi; bankadaki hareketler bu kartın kayıtlarıyla eşlenir: eşleşen ✅, eksik ➕ (seçili gelir, kaydedince eklenir), taksit 🔁 (seçilmeden), ödeme 💸 | `ui/statement/` |
| Kart detayı | Üç blok. **1. Ne kadar, ne zaman** (kartın renginde): ödenecek tutar, son ödemeye kalan gün, ödeme ilerlemesi ("52.331 borcun 20.938'ini ödedin"), "yalnız asgariyi ödersen ~X faiz", limit; **"Ödedim"** (bankadan ödendi, ekran yeniden okutulmadıysa: son okumanın sıfır borçlu kopyası, hatırlatmalar susar) ve "Bankadan güncelle". Okuma yoksa ya da son ödemeden 10 günden fazla geçtiyse okutma çağrısı. **2. Sıradaki ekstre:** son kesimden bu yana bu karta yazılanlar; banka ekranı bu dönemde okunduysa tek sonuç: tutuyor ya da "X eksik" (hareketleri okut / farkı ekle); okutamayan için tutarı elle yazma katlanmış. **3. Geçmiş:** son 6 ekstrenin grafiği, gelecek taksitler. Borç planı ("Tamamını ödeyemeyecek misin?") okunan kalan borçla; son ödeme geçip ödenmediyse açık gelir | `ui/cards/CardDetail*` |

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

## Ana ekran widget'ı ve uygulamayı açmadan kayıt

### Android (`composeApp/src/androidMain/.../widget/`)

`TodayWidget` (Glance): "Bugün harcayabileceğin 640 ₺" (aşıldıysa kırmızımsı zeminde "Bugünkü payını aştın"), bu
haftanın harcaması / sınır, 🔥 sınır serisi. Bütçe kurulmadıysa "kurmak için dokun". Widget'a dokununca uygulama,
**"+ Harcama"** düğmesine dokununca `QuickAddActivity` açılır: ana ekranın üstünde yalnız hızlı kayıt alanı
(klavye açık, "195 market" yaz → kaydet → "Kaydedildi ✓", pencere kapanır; boşluğa dokununca da kapanır). Ana
ekranın `HomeViewModel`'ini kullanır, kart/kategori/tarih çözümü aynıdır.

Güncelleme: `TodayWidgetUpdater` (uygulama açılışında kurulur) bütçe ya da harcama değişince 1 sn bekleyip
widget'ı yeniler; gün değişimi için Android ayrıca yarım saatte bir yeniler (`res/xml/today_widget_info.xml`).
Gösterilenler ortak koddan gelir (`widget/TodaySnapshot.kt`: `LoadTodaySnapshot`, `widgetChanges`); hızlı kayıt
penceresi de ortaktır (`ui/home/QuickAddSheet.kt`).

### iOS (`iosApp/CinchWidget/`, `composeApp/src/iosMain/.../widget/IosWidgetSync.kt`)

WidgetKit eklentisi Kotlin çalıştırmaz: uygulama, bütçe ya da harcama değişince anlık görüntüyü App Group'a
(`group.com.cinch.app`, `UserDefaults`) yazar ve `WidgetCenter.reloadAllTimelines()` ile widget'ı yeniler
(`IosWidgetSync`, Swift'ten `iOSApp.init`'te başlatılır). Küçük ve orta boy: küçükte dokunmak doğrudan hızlı kayda
(`cinch://quick-add`), ortada "+ Harcama" hızlı kayda, gerisi uygulamaya gider. Hızlı kayıt bağlantısı uygulamanın
üstünde `QuickAddSheet`'i açar (`navigation/DeepLinks.kt`). Günlük pay her gün değiştiği hâlde widget kendi
hesaplayamadığı için, gün dönünce eski görüntü soluk ve "Dünkü durum · güncellemek için aç" notuyla gösterilir.

iOS'ta widget metin alamadığından **uygulamayı açmadan kayıt** Siri / Kısayollar / Spotlight / Eylem düğmesiyle
yapılır: "Cinch harcama ekle" → "Ne harcadın?" → "195 market" → "Kaydedildi: 195,00 ₺ 🛒 Market"
(`iosApp/iosApp/AddExpenseIntent.swift` → `QuickAddBridge` → `SaveQuickEntry`, ana ekranla aynı ayrıştırma).

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

**Tercihler** (Ayarlar → Bildirim tercihleri, `NotificationPreferencesStore`): sessiz saatler (bu saatlere düşen
bildirim sessiz saat bitişine kayar; kapatılabilir), "Günün payı" saati, günde en fazla 1–4 bildirim ve sekiz grup
(günün payı, kayıt hatırlatması, sınır uyarıları, haftalık kapanış ve kumbara, seri ve alışkanlıklar, ödeme günleri,
maaş günü, yedek) tek tek aç/kapa. Kapatılan grup günlük sayıya girmez, yerine açık olanlar gelir. Tercih değişince
plan hemen yeniden kurulur (`CoachNotificationSync` tercih akışını izler).

**Dokununca ilgili ekran** (`NotificationType.link`, `navigation/DeepLinks.kt`): kumbara → Kumbara, kart son
ödemesi ve ekstre günü → Kartlar, sabit gider ve maaş → Bu ay, kategori/alışkanlık uyarısı → Nereye gitti?, seri →
Başarılar, yedek → Ayarlar, "dün kayıt yoktu" → hızlı kayıt alanı. Android'de bildirim `MainActivity`'yi bağlantıyla
açar (uygulama açıksa `onNewIntent`), iOS'ta bağlantı bildirimin `userInfo`sunda, `IosNotificationTaps` delegesi
açar (uygulama öndeyken gelen bildirim de gösterilir).

**Bildirimden kayıt (Android):** "Günün payı" ve "Dün kayıt yoktu" bildirimlerinin altında "Harcama yaz" yanıt
alanı: "195 market" yazıp gönderince uygulama açılmadan kaydedilir, bildirim "Kaydedildi ✓ 195,00 ₺ 🛒 Market"
olur (`QuickReplyReceiver` → `SaveQuickEntry`). "Dün kayıt yoktu"ya tarih yazmadan verilen yanıt düne yazılır.

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

# iOS Xcode projesi (bir kez, project.yml değişince yeniden)
brew install xcodegen
cd iosApp && xcodegen
```

iOS projesi `iosApp/project.yml`den üretilir: uygulama (`com.cinch.app`) ve widget eklentisi
(`com.cinch.app.widget`), ikisinde de App Group `group.com.cinch.app`, en düşük iOS 17. Xcode'da imzalama için
`DEVELOPMENT_TEAM`'i doldur (ya da "Signing & Capabilities"ten takım seç); Kotlin framework'ü derleme öncesi adımda
Gradle üretir (`embedAndSignAppleFrameworkForXcode`).

## Araç sürümleri

Kotlin 2.4.21 · AGP 9.4.1 · Compose Multiplatform 1.12.1 · Gradle 9.8.0 · SQLDelight 2.4.1 ·
Koin 4.2.2 (compiler plugin 1.2.1) · Navigation 3 1.2.0-rc01 · WorkManager 2.12.0 · Glance 1.1.1 · Material ikonları 1.7.3
(`gradle/libs.versions.toml`)

## Sonraki adımlar

- Ekran metinlerini Compose kaynak dosyalarına taşıma (`values/strings.xml`, `values-en/strings.xml`); şimdilik arayüz Türkçe, ayrıştırıcılar Türkçe + İngilizce
- Fatura eşlemedeki kurum adları (`BillMatcher`: Enerjisa, İGDAŞ…) dile değil ülkeye bağlı; başka ülke için ülke paketi
- Yuvarlak hatlı özel yazı tipi (ör. Nunito)
- Abonelik (Play Billing + StoreKit, doğrulama RevenueCat), yedekleme (Drive / iCloud)
- iOS widget'ının gece kendi güncellenmesi (BGAppRefreshTask ile günlük payı yeniden yazma)
- Kumbara hedefine tarih koyup "haftada ne kadar atmalısın" önerisi
- Android'de de Kısayollar karşılığı: "Cinch'e harcama ekle" için App Actions / Google Asistan
