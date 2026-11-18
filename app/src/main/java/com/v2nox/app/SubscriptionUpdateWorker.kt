// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class SubscriptionUpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_SUBSCRIPTION_ID) ?: return Result.success()
        return try {
            val repository = SubscriptionRepository(applicationContext)
            val subscription = repository.subscriptionById(id) ?: return Result.success()
            if (!subscription.autoUpdate) return Result.success()
            repository.update(id).fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }

    companion object {
        const val KEY_SUBSCRIPTION_ID = "subscription_id"
    }
}
