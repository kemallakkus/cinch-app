package com.cinch.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cinch.ui.home.QuickAddSheet
import com.cinch.ui.theme.CinchTheme

/**
 * Widget'taki "+ Harcama"dan açılan küçük pencere: ana ekranın üstünde yalnız hızlı kayıt alanı. Kaydedince ya da
 * boşluğa dokununca kapanır; uygulamanın kendisi açılmaz.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CinchTheme {
                QuickAddSheet(
                    onClose = ::finish,
                    onSaved = {
                        Toast.makeText(this, "Kaydedildi ✓", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }
}
