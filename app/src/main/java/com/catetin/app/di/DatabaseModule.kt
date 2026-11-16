package com.catetin.app.di

import android.content.Context
import com.catetin.app.data.db.AccountDao
import com.catetin.app.data.db.AllowlistDao
import com.catetin.app.data.db.AssistantModelDao
import com.catetin.app.data.db.BudgetDao
import com.catetin.app.data.db.CatetDatabase
import com.catetin.app.data.db.CategoryDao
import com.catetin.app.data.db.DiscoveryDao
import com.catetin.app.data.db.GmailDao
import com.catetin.app.data.db.ManualEntryDao
import com.catetin.app.data.db.ReviewDao
import com.catetin.app.data.db.RuleDao
import com.catetin.app.data.db.SourceEventDao
import com.catetin.app.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CatetDatabase =
        CatetDatabase.build(context)

    @Provides fun transactionDao(db: CatetDatabase): TransactionDao = db.transactionDao()
    @Provides fun manualEntryDao(db: CatetDatabase): ManualEntryDao = db.manualEntryDao()
    @Provides fun sourceEventDao(db: CatetDatabase): SourceEventDao = db.sourceEventDao()
    @Provides fun categoryDao(db: CatetDatabase): CategoryDao = db.categoryDao()
    @Provides fun accountDao(db: CatetDatabase): AccountDao = db.accountDao()
    @Provides fun budgetDao(db: CatetDatabase): BudgetDao = db.budgetDao()
    @Provides fun ruleDao(db: CatetDatabase): RuleDao = db.ruleDao()
    @Provides fun allowlistDao(db: CatetDatabase): AllowlistDao = db.allowlistDao()
    @Provides fun gmailDao(db: CatetDatabase): GmailDao = db.gmailDao()
    @Provides fun reviewDao(db: CatetDatabase): ReviewDao = db.reviewDao()
    @Provides fun assistantModelDao(db: CatetDatabase): AssistantModelDao = db.assistantModelDao()
    @Provides fun discoveryDao(db: CatetDatabase): DiscoveryDao = db.discoveryDao()
}
