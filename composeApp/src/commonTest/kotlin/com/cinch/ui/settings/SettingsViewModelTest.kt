package com.cinch.ui.settings

import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.backup.BackupException
import com.cinch.shared.backup.BackupSummary
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.backup.Backups
import com.cinch.shared.domain.Expense
import com.cinch.shared.prefs.InMemoryKeyValueStore
import com.cinch.ui.FakeBudgetRepository
import com.cinch.ui.FakeCardRepository
import com.cinch.ui.FakeClock
import com.cinch.ui.FakeExpenseRepository
import com.cinch.ui.FakeFixedExpenseRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import com.cinch.ui.withTestMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAccess(var access: NotificationAccess) : NotificationAccessChecker {
    var requested = false
    override suspend fun current(): NotificationAccess = access
    override fun markRequested() {
        requested = true
    }
}

/** Yedek dosyası yerine düz metin: "bozuk" geçersiz sayılır. */
private class FakeBackups : Backups {
    var restored: String? = null
    private val summary = BackupSummary("2026-10-12T10:00", expenses = 340, cards = 2, incomes = 1, fixedExpenses = 4, statements = 3, hasBudget = true)

    override suspend fun export(now: LocalDateTime): String = "yedek@$now"

    override fun peek(content: String): BackupSummary =
        if (content == "bozuk") throw BackupException("Bu dosya bir Cinch yedeği değil.") else summary

    override suspend fun restore(content: String): BackupSummary {
        restored = content
        return peek(content)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val today = LocalDate(2026, 10, 28)

    private class Fixture(
        val vm: SettingsViewModel,
        val access: FakeAccess,
        val backups: FakeBackups,
        val tracker: BackupTracker,
        val expenses: FakeExpenseRepository,
    )

    private fun testFixture(access: NotificationAccess = NotificationAccess.GRANTED, block: suspend TestScope.(Fixture) -> Unit) =
        withTestMain {
            runTest {
                val fake = FakeAccess(access)
                val backups = FakeBackups()
                val tracker = BackupTracker(InMemoryKeyValueStore())
                val expenses = FakeExpenseRepository()
                val vm = SettingsViewModel(
                    FakeBudgetRepository(BudgetConfig(weeklyLimit = Money.of(700, Currency.TRY))), fake, backups, tracker,
                    expenses, FakeCardRepository(expenses), FakeFixedExpenseRepository(), FakeClock(today),
                )
                backgroundScope.launch { vm.state.collect {} }
                runCurrent()
                block(Fixture(vm, fake, backups, tracker, expenses))
                vm.viewModelScope.cancel()
            }
        }

    private fun testVm(access: NotificationAccess, block: suspend TestScope.(SettingsViewModel, FakeAccess) -> Unit) =
        testFixture(access) { f -> block(f.vm, f.access) }

    @Test
    fun showsBudgetAndNotificationState() = testVm(NotificationAccess.GRANTED) { vm, _ ->
        assertEquals(Money.of(700, Currency.TRY), vm.state.value.budget?.weeklyLimit)
        assertEquals(NotificationAccess.GRANTED, vm.state.value.notifications)
    }

    @Test
    fun askable_requestsPermission() = testVm(NotificationAccess.ASKABLE) { vm, access ->
        vm.onEvent(SettingsEvent.NotificationsClicked)
        assertTrue(access.requested)
        assertEquals(SettingsEffect.RequestPermission, vm.effect.first())
    }

    @Test
    fun denied_opensSystemSettings_andRefreshPicksUpChange() = testVm(NotificationAccess.DENIED) { vm, access ->
        vm.onEvent(SettingsEvent.NotificationsClicked)
        assertEquals(SettingsEffect.OpenSystemSettings, vm.effect.first())

        access.access = NotificationAccess.GRANTED // kullanıcı ayarlardan açıp döndü
        vm.onEvent(SettingsEvent.Refresh)
        runCurrent()

        assertEquals(NotificationAccess.GRANTED, vm.state.value.notifications)
    }

    @Test
    fun backup_savesFile_thenRemembersTheDay() = testFixture { f ->
        assertNull(f.vm.state.value.lastBackup)
        f.vm.onEvent(SettingsEvent.BackupClicked)
        runCurrent()

        val save = f.vm.effect.first() as SettingsEffect.SaveFile
        assertEquals("cinch-yedek-2026-10-28.json", save.name)
        assertEquals("application/json", save.mimeType)
        assertTrue(save.content.decodeToString().startsWith("yedek@2026-10-28"))

        f.vm.onEvent(SettingsEvent.FileSaved(ok = true))
        runCurrent()
        assertEquals(today, f.vm.state.value.lastBackup)
        assertEquals(today, f.tracker.lastBackup())
        assertIs<SettingsEffect.Message>(f.vm.effect.first())
    }

    @Test
    fun cancelledSave_doesNotCountAsBackup() = testFixture { f ->
        f.vm.onEvent(SettingsEvent.BackupClicked)
        runCurrent()
        f.vm.effect.first()
        f.vm.onEvent(SettingsEvent.FileSaved(ok = false))
        runCurrent()
        assertNull(f.tracker.lastBackup())
    }

    @Test
    fun restore_asksBeforeReplacing() = testFixture { f ->
        f.vm.onEvent(SettingsEvent.RestoreClicked)
        assertEquals(SettingsEffect.OpenFile, f.vm.effect.first())

        f.vm.onEvent(SettingsEvent.FileOpened("yedek".encodeToByteArray()))
        runCurrent()
        assertEquals(340, f.vm.state.value.pendingRestore?.expenses)
        assertNull(f.backups.restored) // onay gelmeden değişmez

        f.vm.onEvent(SettingsEvent.RestoreConfirmed)
        runCurrent()
        assertEquals("yedek", f.backups.restored)
        assertNull(f.vm.state.value.pendingRestore)
        assertIs<SettingsEffect.Message>(f.vm.effect.first())
    }

    @Test
    fun invalidBackup_showsMessage() = testFixture { f ->
        f.vm.onEvent(SettingsEvent.FileOpened("bozuk".encodeToByteArray()))
        runCurrent()
        assertEquals(SettingsEffect.Message("Bu dosya bir Cinch yedeği değil."), f.vm.effect.first())
        assertNull(f.vm.state.value.pendingRestore)
    }

    @Test
    fun csv_containsAllExpenses() = testFixture { f ->
        f.expenses.add(Expense(date = today, grossAmount = Money.of(195, Currency.TRY), rawText = "195 market"))
        f.vm.onEvent(SettingsEvent.ExportCsvClicked)
        runCurrent()

        val save = f.vm.effect.first() as SettingsEffect.SaveFile
        assertEquals("text/csv", save.mimeType)
        assertTrue("195 market" in save.content.decodeToString())
    }
}
