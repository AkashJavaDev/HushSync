package com.hushsync.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs when Android sees a new or changed audio file. It only hands off to
 * [UploadWorker], which re-arms this watcher when it finishes.
 */
class RecordingWatcher(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Sync.runNow(applicationContext)
        return Result.success()
    }
}
