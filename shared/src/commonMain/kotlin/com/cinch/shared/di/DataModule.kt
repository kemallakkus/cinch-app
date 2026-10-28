package com.cinch.shared.di

import com.cinch.shared.backup.BackupService
import com.cinch.shared.backup.Backups
import com.cinch.shared.backup.BackupTracker
import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.data.SqlBudgetRepository
import com.cinch.shared.data.SqlCardRepository
import com.cinch.shared.data.SqlCardStatementRepository
import com.cinch.shared.data.SqlCorrectionStore
import com.cinch.shared.data.SqlExpenseRepository
import com.cinch.shared.data.SqlFixedExpenseRepository
import com.cinch.shared.data.SqlIncomeRepository
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.db.DatabaseDriverFactory
import com.cinch.shared.db.createDatabase
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.CardStatementRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
import com.cinch.shared.prefs.KeyValueStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/** Veritabanı ve repository'ler. [DatabaseDriverFactory] platform modülünden gelir. */
@Module
@Configuration
class DataModule {

    @Single
    @Named("ioDispatcher")
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Single
    fun database(factory: DatabaseDriverFactory): CinchDatabase = createDatabase(factory)

    @Single
    fun expenseRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): ExpenseRepository = SqlExpenseRepository(db, io)

    @Single
    fun cardRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): CardRepository = SqlCardRepository(db, io)

    @Single
    fun cardStatementRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): CardStatementRepository = SqlCardStatementRepository(db, io)

    @Single
    fun budgetRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): BudgetRepository = SqlBudgetRepository(db, io)

    @Single
    fun incomeRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): IncomeRepository = SqlIncomeRepository(db, io)

    @Single
    fun fixedExpenseRepository(
        db: CinchDatabase,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): FixedExpenseRepository = SqlFixedExpenseRepository(db, io)

    @Single
    fun correctionStore(db: CinchDatabase): CorrectionStore = SqlCorrectionStore(db)

    @Single
    fun backupService(
        db: CinchDatabase,
        corrections: CorrectionStore,
        @Named("ioDispatcher") io: CoroutineDispatcher,
    ): Backups = BackupService(db, corrections, io)

    @Single
    fun backupTracker(prefs: KeyValueStore): BackupTracker = BackupTracker(prefs)
}
