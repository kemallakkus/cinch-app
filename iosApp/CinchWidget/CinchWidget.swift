import SwiftUI
import WidgetKit

// Uygulamanın App Group'a yazdığı anlık görüntüyü (IosWidgetSync.kt) gösterir; Kotlin burada çalışmaz.

private let appGroup = "group.com.cinch.app"
private let quickAddURL = URL(string: "cinch://quick-add")!
private let homeURL = URL(string: "cinch://home")!

struct TodaySnapshot {
    let hasBudget: Bool
    let day: String?
    let todayText: String
    let over: Bool
    let weekText: String
    let streak: Int

    static func load() -> TodaySnapshot {
        let defaults = UserDefaults(suiteName: appGroup)
        return TodaySnapshot(
            hasBudget: defaults?.bool(forKey: "widget.hasBudget") ?? false,
            day: defaults?.string(forKey: "widget.day"),
            todayText: defaults?.string(forKey: "widget.todayText") ?? "",
            over: defaults?.bool(forKey: "widget.over") ?? false,
            weekText: defaults?.string(forKey: "widget.weekText") ?? "",
            streak: defaults?.integer(forKey: "widget.streak") ?? 0
        )
    }

    static let sample = TodaySnapshot(
        hasBudget: true, day: nil, todayText: "640 ₺", over: false, weekText: "Bu hafta 1.820 ₺ / 4.500 ₺", streak: 7
    )

    /// Günlük pay her gün değişir; widget kendi başına hesaplayamaz. Dünden kalan görüntü belli edilir.
    var isStale: Bool {
        guard let day else { return false }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return day != formatter.string(from: Date())
    }
}

struct TodayEntry: TimelineEntry {
    let date: Date
    let snapshot: TodaySnapshot
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: Date(), snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(TodayEntry(date: Date(), snapshot: context.isPreview ? .sample : .load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        // Uygulama veri değişince yeniler; gece yarısı da "dünkü durum" diye işaretlemek için bir kez.
        let midnight = Calendar.current.startOfDay(for: Date()).addingTimeInterval(24 * 60 * 60 + 60)
        completion(Timeline(entries: [TodayEntry(date: Date(), snapshot: .load())], policy: .after(midnight)))
    }
}

private extension Color {
    static let cinchPeach = Color(red: 1.0, green: 0.89, blue: 0.80)
    static let cinchPink = Color(red: 1.0, green: 0.84, blue: 0.85)
    static let cinchInk = Color(red: 0.23, green: 0.04, blue: 0.0)
    static let cinchInkOver = Color(red: 0.36, green: 0.06, blue: 0.12)
    static let cinchCoral = Color(red: 0.77, green: 0.31, blue: 0.18)
}

struct TodayWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodayEntry

    private var snapshot: TodaySnapshot { entry.snapshot }
    private var ink: Color { snapshot.over ? .cinchInkOver : .cinchInk }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if !snapshot.hasBudget {
                Text("Cinch").font(.headline.bold())
                Text("Haftalık bütçeni kurmak için dokun").font(.subheadline)
                Spacer(minLength: 0)
            } else {
                Text(snapshot.over ? "Bugünkü payını aştın" : "Bugün harcayabileceğin")
                    .font(.caption)
                Text(snapshot.todayText)
                    .font(.system(size: family == .systemSmall ? 26 : 30, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                    .opacity(snapshot.isStale ? 0.5 : 1)
                Text(snapshot.isStale ? "Dünkü durum · güncellemek için aç" : snapshot.weekText)
                    .font(.caption2)
                    .lineLimit(2)
                Spacer(minLength: 4)
                HStack(alignment: .center) {
                    if snapshot.streak > 0 {
                        Text("🔥 \(snapshot.streak) gün").font(.footnote.weight(.medium))
                    }
                    Spacer(minLength: 0)
                    addButton
                }
            }
        }
        .foregroundStyle(ink)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        // Küçük widget tek dokunuş alır: doğrudan hızlı kayda gider. Ortada ise "+ Harcama" ayrı bağlantı.
        .widgetURL(family == .systemSmall && snapshot.hasBudget ? quickAddURL : homeURL)
    }

    @ViewBuilder
    private var addButton: some View {
        let label = Text(family == .systemSmall ? "+" : "+ Harcama")
            .font(.subheadline.bold())
            .foregroundStyle(.white)
            .padding(.horizontal, family == .systemSmall ? 12 : 14)
            .padding(.vertical, 7)
            .background(Capsule().fill(Color.cinchCoral))
        if family == .systemSmall {
            label
        } else {
            Link(destination: quickAddURL) { label }
        }
    }
}

struct TodayWidget: Widget {
    let kind = "TodayWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    entry.snapshot.over ? Color.cinchPink : Color.cinchPeach
                }
        }
        .configurationDisplayName("Bugün")
        .description("Bugün harcayabileceğin, haftanın durumu ve tek dokunuşla harcama ekleme.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct CinchWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
    }
}
