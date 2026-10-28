package com.cinch.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.notifications.NotificationAccess
import com.cinch.notifications.NotificationAccessChecker
import com.cinch.shared.backup.BackupException
import com.cinch.shared.backup.BackupSummary
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.backup.Backups
import com.cinch.shared.backup.ExpenseCsv
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.label
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * @param lastBackup son yedeğin alındığı gün; hiç alınmadıysa null.
 * @param pendingRestore seçilen yedeğin özeti: onay penceresi açık.
 */
@Immutable
data class SettingsState(
    val budget: BudgetConfig? = null,
    val notifications: NotificationAccess? = null,
    val today: LocalDate? = null,
    val lastBackup: LocalDate? = null,
    val pendingRestore: BackupSummary? = null,
    val isWorking: Boolean = false,
)

sealed interface SettingsEvent {
    /** Ekran öne geldi: kullanıcı telefon ayarlarından bildirimi açıp dönmüş olabilir. */
    data object Refresh : SettingsEvent
    data object NotificationsClicked : SettingsEvent
    data object BackupClicked : SettingsEvent
    data object ExportCsvClicked : SettingsEvent
    data object RestoreClicked : SettingsEvent

    /** Dosya seçicisinden dönüş: kaydedildi mi. */
    data class FileSaved(val ok: Boolean) : SettingsEvent

    /** Dosya seçicisinden dönüş: seçilen dosyanın içeriği (vazgeçildiyse null). */
    class FileOpened(val content: ByteArray?) : SettingsEvent
    data object RestoreConfirmed : SettingsEvent
    data object RestoreDismissed : SettingsEvent
}

sealed interface SettingsEffect {
    data object RequestPermission : SettingsEffect
    data object OpenSystemSettings : SettingsEffect

    /** Kullanıcıya nereye kaydedeceğini sor. */
    class SaveFile(val name: String, val mimeType: String, val content: ByteArray) : SettingsEffect

    /** Kullanıcıya yedek dosyasını seçtir. */
    data object OpenFile : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}

class SettingsViewModel(
    budgets: BudgetRepository,
    private val access: NotificationAccessChecker,
    private val backups: Backups,
    private val tracker: BackupTracker,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val fixedExpenses: FixedExpenseRepository,
    private val clock: LocalClock,
) : ViewModel() {

    private enum class SaveKind { BACKUP, CSV }

    private data class Ui(
        val lastBackup: LocalDate? = null,
        val pendingRestore: BackupSummary? = null,
        val isWorking: Boolean = false,
    )

    private val notifications = MutableStateFlow<NotificationAccess?>(null)
    private val ui = MutableStateFlow(Ui(lastBackup = tracker.lastBackup()))

    /** Kaydetme penceresi açıkken neyin kaydedildiği (yedekse son yedek günü yazılır). */
    private var saving: SaveKind? = null
    private var csvCount = 0
    private var restoreContent: String? = null

    val state: StateFlow<SettingsState> = combine(budgets.observe(), notifications, ui) { budget, access, ui ->
        SettingsState(
            budget = budget,
            notifications = access,
            today = clock.today(),
            lastBackup = ui.lastBackup,
            pendingRestore = ui.pendingRestore,
            isWorking = ui.isWorking,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.Refresh -> refresh()
            SettingsEvent.NotificationsClicked -> when (notifications.value) {
                NotificationAccess.ASKABLE -> {
                    access.markRequested()
                    _effect.trySend(SettingsEffect.RequestPermission)
                }
                // Açıkken de telefon ayarlarına gider: orada kapatılabilir.
                NotificationAccess.GRANTED, NotificationAccess.DENIED -> _effect.trySend(SettingsEffect.OpenSystemSettings)
                null -> Unit
            }
            SettingsEvent.BackupClicked -> backup()
            SettingsEvent.ExportCsvClicked -> exportCsv()
            SettingsEvent.RestoreClicked -> _effect.trySend(SettingsEffect.OpenFile)
            is SettingsEvent.FileSaved -> fileSaved(event.ok)
            is SettingsEvent.FileOpened -> fileOpened(event.content)
            SettingsEvent.RestoreConfirmed -> restore()
            SettingsEvent.RestoreDismissed -> {
                restoreContent = null
                ui.update { it.copy(pendingRestore = null) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch { notifications.value = access.current() }
        ui.update { it.copy(lastBackup = tracker.lastBackup()) }
    }

    private fun backup() = work {
        val content = backups.export(clock.now())
        saving = SaveKind.BACKUP
        _effect.send(SettingsEffect.SaveFile("cinch-yedek-${clock.today()}.json", "application/json", content.encodeToByteArray()))
    }

    private fun exportCsv() = work {
        val all = expenses.observeAll().first()
        val csv = ExpenseCsv.build(
            expenses = all,
            categoryLabel = { it.label },
            cardNames = cards.all().associate { it.id to it.name },
            fixedNames = fixedExpenses.all().associate { it.id to it.name },
        )
        saving = SaveKind.CSV
        csvCount = all.size
        _effect.send(SettingsEffect.SaveFile("cinch-harcamalar-${clock.today()}.csv", "text/csv", csv.encodeToByteArray()))
    }

    private fun fileSaved(ok: Boolean) {
        val kind = saving ?: return
        saving = null
        if (!ok) {
            _effect.trySend(SettingsEffect.Message("Kaydedilmedi."))
            return
        }
        when (kind) {
            SaveKind.BACKUP -> {
                val today = clock.today()
                tracker.markBackedUp(today)
                ui.update { it.copy(lastBackup = today) }
                _effect.trySend(SettingsEffect.Message("Yedek kaydedildi ✓ Telefon değişirse buradan geri yüklersin."))
            }
            SaveKind.CSV -> _effect.trySend(SettingsEffect.Message("$csvCount harcama CSV olarak kaydedildi."))
        }
    }

    private fun fileOpened(content: ByteArray?) {
        if (content == null) return
        val text = content.decodeToString()
        try {
            val summary = backups.peek(text)
            restoreContent = text
            ui.update { it.copy(pendingRestore = summary) }
        } catch (e: BackupException) {
            _effect.trySend(SettingsEffect.Message(e.message ?: "Bu dosya geri yüklenemiyor."))
        }
    }

    private fun restore() {
        val content = restoreContent ?: return
        restoreContent = null
        ui.update { it.copy(pendingRestore = null) }
        work {
            try {
                val summary = backups.restore(content)
                _effect.send(
                    SettingsEffect.Message("Geri yüklendi: ${summary.expenses} harcama, ${summary.cards} kart, ${summary.fixedExpenses} sabit gider."),
                )
            } catch (e: BackupException) {
                _effect.send(SettingsEffect.Message(e.message ?: "Geri yüklenemedi."))
            }
        }
    }

    /** Aynı anda tek iş; bitince düğmeler yeniden açılır. */
    private fun work(block: suspend () -> Unit) {
        if (ui.value.isWorking) return
        ui.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                ui.update { it.copy(isWorking = false) }
            }
        }
    }
}
