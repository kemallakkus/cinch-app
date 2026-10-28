package com.cinch.shared.backup

import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.db.CinchDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Tek dosyalık yedek: tüm tablolar, satır satır ve kimlikleriyle. Uygulama sürümü ilerledikçe alan eklenebilir;
 * bilinmeyen alanlar yok sayılır, eksik alanlar varsayılanını alır.
 *
 * @param format dosyanın Cinch yedeği olduğunu söyler; başka bir JSON yanlışlıkla geri yüklenmesin.
 * @param version yedek biçiminin sürümü; daha yeni bir uygulamanın yedeği reddedilir.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: String,
    val budget: BudgetRow? = null,
    val cards: List<CardRow> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
    val incomes: List<IncomeRow> = emptyList(),
    val fixedExpenses: List<FixedExpenseRow> = emptyList(),
    val statements: List<StatementRow> = emptyList(),
    val corrections: List<CorrectionRow> = emptyList(),
    val goals: List<GoalRow> = emptyList(),
    val deposits: List<DepositRow> = emptyList(),
) {
    companion object {
        const val FORMAT = "cinch-backup"

        /** 2: kumbara eklendi. Eski uygulama yeni yedeği reddeder (kumbara sessizce kaybolmasın). */
        const val VERSION = 2
    }

    @Serializable
    data class GoalRow(
        val id: Long,
        val name: String,
        val emoji: String,
        val targetMinor: Long,
        val currency: String,
        val createdEpochDay: Long,
    )

    @Serializable
    data class DepositRow(
        val id: Long,
        val goalId: Long,
        val amountMinor: Long,
        val currency: String,
        val dateEpochDay: Long,
        val note: String = "",
    )

    @Serializable
    data class BudgetRow(
        val weeklyLimitMinor: Long,
        val currency: String,
        val weekStartIso: Long,
        val weeklyGoalMinor: Long? = null,
        val salaryDay: Long? = null,
    )

    @Serializable
    data class CardRow(
        val id: Long,
        val name: String,
        val currency: String,
        val carriedBalanceMinor: Long,
        val monthlyRatePercent: Double,
        val statementDay: Long? = null,
        val last4: String? = null,
        val type: String = "CREDIT",
    )

    @Serializable
    data class ExpenseRow(
        val id: Long,
        val dateEpochDay: Long,
        val grossMinor: Long,
        val chargeableFraction: Double = 1.0,
        val currency: String,
        val category: String,
        val cardId: Long? = null,
        val note: String = "",
        val rawText: String = "",
        val tokens: String = "",
        val fixedExpenseId: Long? = null,
    )

    @Serializable
    data class IncomeRow(val id: Long, val name: String, val amountMinor: Long, val currency: String, val dayOfMonth: Long? = null)

    @Serializable
    data class FixedExpenseRow(
        val id: Long,
        val name: String,
        val amountMinor: Long,
        val currency: String,
        val dayOfMonth: Long,
        val kind: String,
        val cardId: Long? = null,
        val plan: String = "MONTHLY",
        val totalInstallments: Long? = null,
        val paidBefore: Long = 0,
        val debtMinor: Long? = null,
    )

    @Serializable
    data class StatementRow(
        val id: Long,
        val cardId: Long,
        val readEpochDay: Long,
        val statementEpochDay: Long? = null,
        val dueEpochDay: Long? = null,
        val nextStatementEpochDay: Long? = null,
        val currency: String,
        val statementBalanceMinor: Long? = null,
        val minimumPaymentMinor: Long? = null,
        val periodSpendingMinor: Long? = null,
        val futureInstallmentsMinor: Long? = null,
        val totalDebtMinor: Long? = null,
        val creditLimitMinor: Long? = null,
        val availableLimitMinor: Long? = null,
    )

    @Serializable
    data class CorrectionRow(val keyword: String, val category: String)
}

/** Geri yüklemeden önce kullanıcıya gösterilen özet ("12 Ekim'de alınmış: 340 harcama, 2 kart…"). */
data class BackupSummary(
    val createdAt: String,
    val expenses: Int,
    val cards: Int,
    val incomes: Int,
    val fixedExpenses: Int,
    val statements: Int,
    val hasBudget: Boolean,
)

/** Dosya Cinch yedeği değil, bozuk ya da daha yeni bir sürümün; [message] kullanıcıya gösterilir. */
class BackupException(message: String) : Exception(message)

/** Yedek alma ve geri yükleme; ekranlar bunu kullanır (testlerde sahtesi). */
interface Backups {
    suspend fun export(now: LocalDateTime): String

    /** @throws BackupException dosya geçersizse. */
    fun peek(content: String): BackupSummary

    /** @throws BackupException dosya geçersizse; o zaman hiçbir şey değişmez. */
    suspend fun restore(content: String): BackupSummary
}

/**
 * Yedek alır ve geri yükler. Geri yükleme tek işlemde (transaction) yapılır: yarıda kalırsa hiçbir şey
 * değişmez. Ekranlar veritabanını izlediği için geri yüklenen veri hemen görünür.
 */
class BackupService(
    private val db: CinchDatabase,
    private val corrections: CorrectionStore,
    private val ioDispatcher: CoroutineDispatcher,
) : Backups {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val queries get() = db.cinchQueries

    override suspend fun export(now: LocalDateTime): String = withContext(ioDispatcher) {
        val file = BackupFile(
            createdAt = now.toString(),
            budget = queries.currentBudget().executeAsOneOrNull()?.let {
                BackupFile.BudgetRow(it.weekly_limit_minor, it.currency, it.week_start_iso, it.weekly_goal_minor, it.salary_day)
            },
            cards = queries.allCards().executeAsList().map {
                BackupFile.CardRow(
                    it.id, it.name, it.currency, it.carried_balance_minor, it.monthly_rate_percent, it.statement_day, it.last4, it.type,
                )
            },
            expenses = queries.allExpenses().executeAsList().map {
                BackupFile.ExpenseRow(
                    it.id, it.date_epoch_day, it.gross_minor, it.chargeable_fraction, it.currency, it.category, it.card_id,
                    it.note, it.raw_text, it.tokens, it.fixed_expense_id,
                )
            },
            incomes = queries.allIncomes().executeAsList().map {
                BackupFile.IncomeRow(it.id, it.name, it.amount_minor, it.currency, it.day_of_month)
            },
            fixedExpenses = queries.allFixedExpenses().executeAsList().map {
                BackupFile.FixedExpenseRow(
                    it.id, it.name, it.amount_minor, it.currency, it.day_of_month, it.kind, it.card_id, it.plan,
                    it.total_installments, it.paid_before, it.debt_minor,
                )
            },
            statements = queries.allStatements().executeAsList().map {
                BackupFile.StatementRow(
                    it.id, it.card_id, it.read_epoch_day, it.statement_epoch_day, it.due_epoch_day, it.next_statement_epoch_day,
                    it.currency, it.statement_balance_minor, it.minimum_payment_minor, it.period_spending_minor,
                    it.future_installments_minor, it.total_debt_minor, it.credit_limit_minor, it.available_limit_minor,
                )
            },
            corrections = queries.allCorrections().executeAsList().map { BackupFile.CorrectionRow(it.keyword, it.category) },
            goals = queries.allGoals().executeAsList().map {
                BackupFile.GoalRow(it.id, it.name, it.emoji, it.target_minor, it.currency, it.created_epoch_day)
            },
            deposits = queries.allDeposits().executeAsList().map {
                BackupFile.DepositRow(it.id, it.goal_id, it.amount_minor, it.currency, it.date_epoch_day, it.note)
            },
        )
        json.encodeToString(BackupFile.serializer(), file)
    }

    /** Dosyayı okur ve ne içerdiğini söyler; veritabanına dokunmaz. */
    override fun peek(content: String): BackupSummary {
        val file = parse(content)
        return BackupSummary(
            createdAt = file.createdAt,
            expenses = file.expenses.size,
            cards = file.cards.size,
            incomes = file.incomes.size,
            fixedExpenses = file.fixedExpenses.size,
            statements = file.statements.size,
            hasBudget = file.budget != null,
        )
    }

    /** Telefondaki tüm verileri yedektekilerle değiştirir. */
    override suspend fun restore(content: String): BackupSummary {
        val file = parse(content)
        withContext(ioDispatcher) {
            queries.transaction {
                queries.deleteAllStatements()
                queries.deleteAllExpenses()
                queries.deleteAllFixedExpenses()
                queries.deleteAllIncomes()
                queries.deleteAllCards()
                queries.deleteAllCorrections()
                queries.deleteAllDeposits()
                queries.deleteAllGoals()
                queries.deleteBudget()

                file.budget?.let {
                    queries.restoreBudget(it.weeklyLimitMinor, it.currency, it.weekStartIso, it.weeklyGoalMinor, it.salaryDay)
                }
                file.cards.forEach {
                    queries.restoreCard(it.id, it.name, it.currency, it.carriedBalanceMinor, it.monthlyRatePercent, it.statementDay, it.last4, it.type)
                }
                file.expenses.forEach {
                    queries.restoreExpense(
                        it.id, it.dateEpochDay, it.grossMinor, it.chargeableFraction, it.currency, it.category, it.cardId,
                        it.note, it.rawText, it.tokens, it.fixedExpenseId,
                    )
                }
                file.incomes.forEach { queries.restoreIncome(it.id, it.name, it.amountMinor, it.currency, it.dayOfMonth) }
                file.fixedExpenses.forEach {
                    queries.restoreFixedExpense(
                        it.id, it.name, it.amountMinor, it.currency, it.dayOfMonth, it.kind, it.cardId, it.plan,
                        it.totalInstallments, it.paidBefore, it.debtMinor,
                    )
                }
                file.statements.forEach {
                    queries.restoreStatement(
                        it.id, it.cardId, it.readEpochDay, it.statementEpochDay, it.dueEpochDay, it.nextStatementEpochDay,
                        it.currency, it.statementBalanceMinor, it.minimumPaymentMinor, it.periodSpendingMinor,
                        it.futureInstallmentsMinor, it.totalDebtMinor, it.creditLimitMinor, it.availableLimitMinor,
                    )
                }
                file.corrections.forEach { queries.upsertCorrection(it.keyword, it.category) }
                file.goals.forEach { queries.restoreGoal(it.id, it.name, it.emoji, it.targetMinor, it.currency, it.createdEpochDay) }
                file.deposits.forEach {
                    queries.restoreDeposit(it.id, it.goalId, it.amountMinor, it.currency, it.dateEpochDay, it.note)
                }
            }
        }
        corrections.reload()
        return peek(content)
    }

    private fun parse(content: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), content.trim().removePrefix("﻿"))
        } catch (e: SerializationException) {
            throw BackupException("Bu dosya bir Cinch yedeği değil ya da bozuk.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("Bu dosya bir Cinch yedeği değil ya da bozuk.")
        }
        if (file.format != BackupFile.FORMAT) throw BackupException("Bu dosya bir Cinch yedeği değil.")
        if (file.version > BackupFile.VERSION) {
            throw BackupException("Bu yedek uygulamanın daha yeni bir sürümüyle alınmış. Önce uygulamayı güncelle.")
        }
        return file
    }
}
