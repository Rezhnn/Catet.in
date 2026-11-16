package com.catetin.app.maintenance

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.catetin.app.data.prefs.SettingsRepository
import com.catetin.app.data.repo.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class RawTextRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val days = settingsRepository.settings.first().rawTextRetentionDays.coerceIn(1, 3650)
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
        repository.purgeRawText(cutoff)
        return Result.success()
    }
}
