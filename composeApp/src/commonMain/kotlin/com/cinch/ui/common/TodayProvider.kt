package com.cinch.ui.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Bugünün tarihi. Testte sabit güne, uygulamada cihazın saat dilimine bağlanır. */
fun interface TodayProvider {
    fun today(): LocalDate
}

object SystemTodayProvider : TodayProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
