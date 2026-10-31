package com.cinch.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cinch.App
import com.cinch.navigation.DeepLinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Bildirimden açıldıysa ilgili ekran; döndürme gibi yeniden kurulumda bağlantı tekrar işlenmez.
        if (savedInstanceState == null) openLink(intent)
        setContent { App() }
    }

    /** Uygulama açıkken bildirime dokunuldu. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        intent?.data?.toString()?.takeIf { it.startsWith("cinch://") }?.let(DeepLinks::open)
    }
}
