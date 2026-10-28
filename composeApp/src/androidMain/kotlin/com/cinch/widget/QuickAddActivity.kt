package com.cinch.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.ui.common.display
import com.cinch.ui.home.HomeEffect
import com.cinch.ui.home.HomeViewModel
import com.cinch.ui.home.QuickEntryBar
import com.cinch.ui.theme.CinchTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Widget'taki "+ Harcama"dan açılan küçük pencere: ana ekranın üstünde yalnız hızlı kayıt alanı. Kaydedince ya da
 * boşluğa dokununca kapanır; uygulamanın kendisi açılmaz.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CinchTheme { QuickAddSheet(onClose = ::finish) }
        }
    }
}

@Composable
private fun QuickAddSheet(onClose: () -> Unit, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            if (effect is HomeEffect.EntrySaved) {
                Toast.makeText(context, "Kaydedildi ✓", Toast.LENGTH_SHORT).show()
                currentOnClose()
            }
        }
    }

    // Boşluğa dokununca kapanır; pencerenin kendisine dokunmak kapatmaz.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            // Başlık kayıt alanının kendi panelinin içinde: iki ayrı kart üst üste binmesin.
            QuickEntryBar(
                state = state,
                onEvent = viewModel::onEvent,
                autoFocus = true,
                header = {
                    val remaining = state.status?.todayRemaining
                    if (remaining != null) {
                        Text(
                            if (remaining.isNegative) "Bugünkü payını ${remaining.absolute.display()} aştın"
                            else "Bugün harcayabileceğin: ${remaining.display()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (remaining.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                },
            )
        }
    }
}
