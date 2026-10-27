package com.cinch.shared.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Cihazın yerel saati. Testte sabit bir ana bağlanır. */
interface LocalClock {
    fun now(): LocalDateTime
    fun today(): LocalDate = now().date
}

object SystemLocalClock : LocalClock {
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
