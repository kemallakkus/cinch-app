package com.cinch.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulamayı dışarıdan açan bağlantılar (iOS widget'ı: "+ Harcama" → `cinch://quick-add`). Platform tarafı
 * [open] ile bırakır, uygulama işleyince [consume] eder.
 */
object DeepLinks {
    const val QUICK_ADD = "cinch://quick-add"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun open(url: String) {
        _pending.value = url
    }

    fun consume() {
        _pending.value = null
    }
}
