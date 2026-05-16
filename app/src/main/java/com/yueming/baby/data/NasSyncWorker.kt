package com.yueming.baby.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NasSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            if (DataManager.runBackgroundSync(applicationContext)) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("NasSyncWorker", "NAS background sync failed", e)
            Result.retry()
        }
    }
}
