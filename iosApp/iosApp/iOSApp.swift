import SwiftUI
import WidgetKit
import ComposeApp

@main
struct iOSApp: App {
    init() {
        StartKoin.shared.doInitKoin()
        // Bildirime dokununca ilgili ekran açılsın; uygulama öndeyken de gösterilsin.
        IosNotificationTaps.shared.install()
        // Bütçe ya da harcamalar değişince widget'ın verisi App Group'a yazılır ve widget yenilenir.
        IosWidgetSync.shared.start {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Widget: "cinch://quick-add" → uygulamanın üstünde hızlı kayıt alanı.
                .onOpenURL { url in
                    DeepLinks.shared.open(url: url.absoluteString)
                }
        }
    }
}
