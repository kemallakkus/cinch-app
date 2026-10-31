package com.cinch.navigation

import com.cinch.notifications.link
import com.cinch.shared.coach.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeepLinksTest {

    @Test
    fun everyNotificationOpensSomewhere() {
        NotificationType.entries.forEach { type ->
            val link = type.link
            if (link != DeepLinks.QUICK_ADD) assertNotNull(linkTarget(link), type.name)
        }
        assertEquals(DeepLinks.QUICK_ADD, NotificationType.FORGOT_LOG.link) // "dün kayıt yoktu" → hemen yaz
    }

    @Test
    fun linksOpenTheRightTabAndScreen() {
        assertEquals(LinkTarget(TopTab.TODAY, SavingsRoute), linkTarget(NotificationType.WEEK_LEFTOVER.link))
        assertEquals(LinkTarget(TopTab.CARDS), linkTarget(NotificationType.CARD_DUE.link))
        assertEquals(LinkTarget(TopTab.MONTH), linkTarget(NotificationType.FIXED_UNPAID.link))
        assertEquals(LinkTarget(TopTab.MONTH, InsightsRoute()), linkTarget(NotificationType.CATEGORY_SPIKE.link))
        assertEquals(LinkTarget(TopTab.SETTINGS), linkTarget(NotificationType.BACKUP_REMINDER.link))
        assertNull(linkTarget("cinch://bilinmeyen"))
        assertNull(linkTarget(DeepLinks.QUICK_ADD)) // sekme değil, katman
    }
}
