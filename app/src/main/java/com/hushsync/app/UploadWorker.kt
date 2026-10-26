package com.hushsync.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

/**
 * Finds new call recordings, uploads each one to Google Drive and,
 * once Drive confirms the full file arrived, deletes it from the phone.
 */
class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private class Recording(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val mimeType: String,
        val open: () -> InputStream,
        val delete: () -> Boolean,
    ) {
        val key get() = "$name|$size|$lastModified"
    }

    private val prefs = Prefs(applicationContext)

    /** Set when a call recording was skipped because the dialer may still be writing it. */
    private var stillSaving = false

    override fun doWork(): Result {
        // Several triggers can overlap; only one run should upload at a time.
        if (!lock.tryLock()) return Result.success()
        try {
            return sync()
        } finally {
            lock.unlock()
            if (stillSaving) Sync.checkLater(applicationContext)
            Sync.watchForRecordings(applicationContext)
        }
    }

    private fun sync(): Result {
        if (!prefs.driveConnected) return Result.success()

        val pending = findRecordings().filter { it.size > 0 && !prefs.isUploaded(it.key) }
        if (pending.isEmpty()) return Result.success()

        val token = try {
            DriveAuth.tokenBlocking(applicationContext)
        } catch (e: Exception) {
            return Result.retry()
        }
        if (token == null) {
            prefs.driveConnected = false
            Notifications.showReconnect(applicationContext)
            return Result.failure()
        }

        val drive = DriveUploader(token)
        val monthFolders = HashMap<String, String>()

        try {
            val rootId = drive.findOrCreateFolder(ROOT_FOLDER, "root")
            for (rec in pending) {
                if (isStopped) break
                val month = month(rec.lastModified)
                val folderId = monthFolders.getOrPut(month) { drive.findOrCreateFolder(month, rootId) }
                val driveSize = drive.upload(rec.name, rec.mimeType, rec.size, folderId, rec.open)
                if (driveSize != rec.size) throw IOException("${rec.name} didn't upload completely")
                // Always remove the phone copy once Drive has it. If Android refuses the
                // delete, remember the file so it isn't uploaded again.
                if (!rec.delete()) prefs.markUploaded(rec.key)
            }
        } catch (e: DriveUploader.AuthExpired) {
            DriveAuth.clearToken(applicationContext, token)
            return Result.retry()
        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    /** Call recordings saved by the phone's own dialer, in the folder the user picked. */
    private fun findRecordings(): List<Recording> {
        val out = ArrayList<Recording>()
        val uri = prefs.callFolder ?: return out
        val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(uri))
        if (tree != null && tree.canRead()) collectCalls(tree, depth = 0, out)
        return out
    }

    private fun collectCalls(dir: DocumentFile, depth: Int, out: MutableList<Recording>) {
        val now = System.currentTimeMillis()
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                if (depth < 2) collectCalls(file, depth + 1, out)
                continue
            }
            val name = file.name ?: continue
            if (name.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) continue
            // A file changed in the last 30 seconds may be a call that is still being recorded.
            if (now - file.lastModified() < SETTLE_MS) {
                stillSaving = true
                continue
            }
            out += Recording(
                name = name,
                size = file.length(),
                lastModified = file.lastModified(),
                mimeType = file.type ?: "application/octet-stream",
                open = {
                    applicationContext.contentResolver.openInputStream(file.uri)
                        ?: throw IOException("Can't open $name")
                },
                delete = { file.delete() },
            )
        }
    }

    private fun month(time: Long): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(if (time > 0) time else System.currentTimeMillis()))

    companion object {
        const val ROOT_FOLDER = "HushSync"
        private const val SETTLE_MS = 30 * 1000L
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "amr", "awb", "wav", "ogg", "opus", "3gp")
        private val lock = ReentrantLock()
    }
}
