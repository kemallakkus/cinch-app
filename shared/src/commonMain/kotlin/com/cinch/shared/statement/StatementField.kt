package com.cinch.shared.statement

/**
 * Banka ekranından okunan alanlar. Ekranlardaki adları dilden dile değişir, dil paketinde durur
 * ([com.cinch.shared.language.StatementWords.labels]).
 */
enum class StatementField(internal val kind: StatementValueKind) {
    STATEMENT_BALANCE(StatementValueKind.MONEY),
    MINIMUM_PAYMENT(StatementValueKind.MONEY),
    DUE_DATE(StatementValueKind.DATE),

    /** Akbank: "Son gün: 8 Ekim" ve altında ödenecek tutar. */
    LAST_DAY(StatementValueKind.DATE_THEN_MONEY),
    NEXT_DUE(StatementValueKind.DATE),
    STATEMENT_DATE(StatementValueKind.DATE),
    NEXT_STATEMENT(StatementValueKind.DATE),
    PERIOD_SPENDING(StatementValueKind.MONEY),
    FUTURE_INSTALLMENTS(StatementValueKind.MONEY),
    TOTAL_DEBT(StatementValueKind.MONEY),
    CREDIT_LIMIT(StatementValueKind.MONEY),
    AVAILABLE_LIMIT(StatementValueKind.MONEY),
}

internal enum class StatementValueKind { MONEY, DATE, DATE_THEN_MONEY }
