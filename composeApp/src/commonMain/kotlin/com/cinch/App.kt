package com.cinch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.CinchNavHost
import com.cinch.navigation.DeepLinks
import com.cinch.navigation.HomeRoute
import com.cinch.ui.home.QuickAddSheet
import com.cinch.notifications.CoachNotificationSync
import com.cinch.ui.root.RootViewModel
import com.cinch.ui.theme.CinchTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Android ve iOS'un ortak giriş noktası. */
@Composable
fun App() {
    CinchTheme {
        val viewModel = koinViewModel<RootViewModel>()
        val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()

        // Öne gelişte bildirim planı yenilenir: gün değişmiş olabilir ya da izin yeni verilmiş olabilir.
        val coachSync = koinInject<CoachNotificationSync>()
        LifecycleResumeEffect(coachSync) {
            coachSync.refresh()
            onPauseOrDispose { }
        }

        // Başlangıç ekranı belli olana kadar boş arka plan (yerel okuma, göz kırpması kadar sürer).
        val start = startRoute
        if (start == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        } else {
            Box(Modifier.fillMaxSize()) {
                CinchNavHost(start = start, onIntroFinished = viewModel::introFinished)

                // Widget'taki "+ Harcama" ve "dün kayıt yoktu" bildirimi: uygulamanın üstünde yalnız hızlı kayıt alanı.
                // Bütçe kurulmadan anlamsız. Diğer bağlantıları (sekme, ekran) ana sekme yapısı işler.
                val link by DeepLinks.pending.collectAsStateWithLifecycle()
                if (link == DeepLinks.QUICK_ADD) {
                    if (start == HomeRoute) QuickAddSheet(onClose = DeepLinks::consume) else LaunchedEffect(link) { DeepLinks.consume() }
                }
            }
        }
    }
}
