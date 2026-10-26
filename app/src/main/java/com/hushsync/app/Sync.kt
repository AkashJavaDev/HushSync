package com.hushsync.app

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [UploadWorker]. The app sleeps until Android's media index sees a new
 * audio file (such as a call the dialer just saved), plus a safety check every
 * 6 hours. WorkManager keeps all of this across reboots.
 */
object Sync {
    private const val PERIODIC = "safety-check"
    private const val NOW = "upload-now"
    private const val WATCH = "watch-recordings"

    /** Uploads run on Wi-Fi or mobile data, whichever is available. */
    private val anyNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(anyNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        watchForRecordings(context)
    }

    /**
     * Wakes [RecordingWatcher] once, the next time an audio file is added or changed.
     * A content trigger fires only once, so [UploadWorker] calls this again after every run.
     */
    fun watchForRecordings(context: Context) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
            // Wait until the dialer has stopped writing for 40 s, but never more than 2 min.
            .setTriggerContentUpdateDelay(40, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(2, TimeUnit.MINUTES)
            .build()
        val request = OneTimeWorkRequestBuilder<RecordingWatcher>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WATCH, ExistingWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(anyNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Checks again in 2 minutes, for a call recording that was still being saved. */
    fun checkLater(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setConstraints(anyNetwork)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
