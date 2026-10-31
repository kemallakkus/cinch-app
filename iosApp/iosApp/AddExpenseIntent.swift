import AppIntents
import WidgetKit
import ComposeApp

/// Siri, Kısayollar, Spotlight ve Eylem düğmesi: uygulamayı açmadan "195 market" kaydeder.
struct AddExpenseIntent: AppIntent {
    static let title: LocalizedStringResource = "Harcama ekle"
    static let description = IntentDescription("Cinch'i açmadan harcama kaydeder: \"195 market\", \"dün 120 taksi\" gibi.")
    static let openAppWhenRun: Bool = false

    @Parameter(title: "Harcama", requestValueDialog: IntentDialog("Ne harcadın? Örneğin 195 market"))
    var text: String

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let message = try await QuickAddBridge.shared.save(text: text)
        WidgetCenter.shared.reloadAllTimelines()
        return .result(dialog: IntentDialog(stringLiteral: message))
    }
}

struct CinchShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: [
                "\(.applicationName) harcama ekle",
                "\(.applicationName) ile harcama yaz",
            ],
            shortTitle: "Harcama ekle",
            systemImageName: "plus.circle.fill"
        )
    }
}
