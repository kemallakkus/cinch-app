package com.cinch.ui.onboarding

import com.cinch.shared.prefs.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachMarksTest {

    private val steps = listOf(
        CoachMark("a", "A", "a"),
        CoachMark("b", "B", "b"),
        CoachMark("c", "C", "c"),
    )

    @Test
    fun next_isFirstUnseenWhoseTargetIsOnScreen() {
        assertEquals("a", nextCoachMark(steps, seen = emptySet(), present = setOf("a", "b", "c"))?.key)
        assertEquals("b", nextCoachMark(steps, seen = setOf("a"), present = setOf("a", "b", "c"))?.key)
        // Hedefi henüz ekranda olmayan atlanır ama görüldü sayılmaz.
        assertEquals("c", nextCoachMark(steps, seen = setOf("a"), present = setOf("a", "c"))?.key)
        assertNull(nextCoachMark(steps, seen = setOf("a", "c"), present = setOf("a", "c")))
        assertEquals("b", nextCoachMark(steps, seen = setOf("a", "c"), present = setOf("b"))?.key)
    }

    @Test
    fun progress_remembersEachMark_andResetShowsAllAgain() {
        val progress = CoachMarkProgress(InMemoryKeyValueStore())
        val home = CoachMarkSteps.HOME.first().key
        val cards = CoachMarkSteps.CARDS.last().key
        assertFalse(progress.isSeen(home))

        progress.markSeen(home)
        progress.markSeen(cards)
        assertTrue(progress.isSeen(home))
        assertFalse(progress.isSeen(CoachMarkSteps.HOME[1].key))

        val before = progress.resets.value
        progress.resetAll()
        assertFalse(progress.isSeen(home))
        assertFalse(progress.isSeen(cards))
        assertEquals(before + 1, progress.resets.value) // açık ekranlar yeniden okusun
    }

    @Test
    fun allKeysAreUnique() {
        assertEquals(CoachMarkSteps.ALL.size, CoachMarkSteps.ALL.map { it.key }.toSet().size)
    }
}
