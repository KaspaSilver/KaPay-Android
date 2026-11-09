package com.kachat.app.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic fallback for Automatic Sync of Contacts and the Portfolio (see
 * [NextcloudSyncService]) — every 6 hours, network connected + battery not low, its own unique
 * work, enqueued only while an account is connected with Automatic Sync on. The upload after a
 * change, and the one on going to the background, are the primary paths; this worker only exists so
 * a backup the process died before finishing (a persisted owed flag still set) eventually lands.
 * When nothing is owed it does no network work at all.
 *
 * Always reports success: a failed attempt leaves the backup owed, and the next 6-hour cycle (or
 * the next change, background or launch) retries — WorkManager's own backoff/retry machinery
 * would only duplicate that.
 */
@HiltWorker
class NextcloudAutoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nextcloudSyncService: NextcloudSyncService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        nextcloudSyncService.periodicUploadIfNeeded()
        return Result.success()
    }
}
