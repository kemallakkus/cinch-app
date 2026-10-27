package com.cinch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.CinchNavHost
import com.cinch.ui.root.RootViewModel
import com.cinch.ui.theme.CinchTheme
import org.koin.compose.viewmodel.koinViewModel

/** Android ve iOS'un ortak giriş noktası. */
@Composable
fun App() {
    CinchTheme {
        val viewModel = koinViewModel<RootViewModel>()
        val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()

        // Başlangıç ekranı belli olana kadar boş arka plan (yerel okuma, göz kırpması kadar sürer).
        startRoute?.let { CinchNavHost(startRoute = it) }
            ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    }
}
