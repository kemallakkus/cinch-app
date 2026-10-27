import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        StartKoin.shared.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
