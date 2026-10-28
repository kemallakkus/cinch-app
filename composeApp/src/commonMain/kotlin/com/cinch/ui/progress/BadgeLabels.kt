package com.cinch.ui.progress

import com.cinch.shared.gamification.Badge

val Badge.emoji: String
    get() = when (this) {
        Badge.FIRST_ENTRY -> "🌱"
        Badge.STREAK_3 -> "🔥"
        Badge.STREAK_7 -> "🏅"
        Badge.STREAK_30 -> "🏆"
        Badge.LOGGED_7 -> "📒"
        Badge.WEEK_ON_TRACK -> "🎯"
        Badge.FIRST_SAVING -> "🐷"
        Badge.GOAL_REACHED -> "🌟"
        Badge.DEBT_FINISHED -> "🎉"
        Badge.BACKUP_TAKEN -> "🛡️"
    }

val Badge.title: String
    get() = when (this) {
        Badge.FIRST_ENTRY -> "İlk adım"
        Badge.STREAK_3 -> "3 gün sınırda"
        Badge.STREAK_7 -> "7 gün sınırda"
        Badge.STREAK_30 -> "30 gün sınırda"
        Badge.LOGGED_7 -> "Defter tutan"
        Badge.WEEK_ON_TRACK -> "Hafta tamam"
        Badge.FIRST_SAVING -> "İlk birikim"
        Badge.GOAL_REACHED -> "Hedefe ulaştın"
        Badge.DEBT_FINISHED -> "Borç bitti"
        Badge.BACKUP_TAKEN -> "Güvende"
    }

val Badge.description: String
    get() = when (this) {
        Badge.FIRST_ENTRY -> "İlk harcamanı kaydet."
        Badge.STREAK_3 -> "3 gün üst üste günlük payının içinde kal."
        Badge.STREAK_7 -> "7 gün üst üste günlük payının içinde kal."
        Badge.STREAK_30 -> "30 gün üst üste günlük payının içinde kal."
        Badge.LOGGED_7 -> "7 gün üst üste harcama kaydet."
        Badge.WEEK_ON_TRACK -> "Bir haftayı haftalık sınırının altında kapat."
        Badge.FIRST_SAVING -> "Kumbaraya ilk paranı at."
        Badge.GOAL_REACHED -> "Bir kumbara hedefini tamamla."
        Badge.DEBT_FINISHED -> "Bir taksiti ya da borcu bitir."
        Badge.BACKUP_TAKEN -> "Ayarlar'dan ilk yedeğini al."
    }
