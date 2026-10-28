package com.cinch.shared.domain

import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate

/** Biriktirme hedefi: "🏖️ Tatil 20.000 ₺". */
data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val emoji: String = "🐷",
    val target: Money,
    val createdOn: LocalDate,
)

/**
 * Kumbaraya atılan para. [amount] eksiyse kumbaradan çekilen. Para bankadan çıkmaz; o hedef için ayrılmış sayılır ve
 * "Bu ay elinde kalan"dan düşülür.
 *
 * @param note "Haftalık artan · 19 Eki" gibi kısa açıklama.
 */
data class SavingsDeposit(
    val id: Long = 0,
    val goalId: Long,
    val amount: Money,
    val date: LocalDate,
    val note: String = "",
)

/** Hedefin durumu: biriken, oran, ulaşıldı mı. */
data class GoalProgress(val goal: SavingsGoal, val saved: Money, val deposits: List<SavingsDeposit>) {
    val fraction: Float
        get() = if (goal.target.isZero) 1f else (saved.minor.toFloat() / goal.target.minor).coerceIn(0f, 1f)

    val isReached: Boolean get() = !goal.target.isZero && saved.minor >= goal.target.minor

    val remaining: Money get() = Money((goal.target.minor - saved.minor).coerceAtLeast(0), goal.target.currency)

    companion object {
        fun of(goals: List<SavingsGoal>, deposits: List<SavingsDeposit>): List<GoalProgress> = goals.map { goal ->
            val own = deposits.filter { it.goalId == goal.id && it.amount.currency == goal.target.currency }
            GoalProgress(goal, Money(own.sumOf { it.amount.minor }, goal.target.currency), own)
        }
    }
}
