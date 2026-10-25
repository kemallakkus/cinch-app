package com.cinch

import androidx.compose.ui.window.ComposeUIViewController

/** iOS giriş noktası: SwiftUI tarafından çağrılır. */
fun MainViewController() = ComposeUIViewController { App() }
