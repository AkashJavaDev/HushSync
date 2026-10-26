package com.hushsync.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.Identity
import com.hushsync.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        // Keep access after reboot; write access is needed to delete uploaded files.
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.callFolder = uri.toString()
        refresh()
        Sync.runNow(this)
    }

    private val authResolution =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) onDriveConnected() else toast("Google Drive was not connected")
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        Notifications.createChannels(this)

        binding.connectDrive.setOnClickListener { connectDrive() }
        binding.pickFolder.setOnClickListener { pickFolder.launch(null) }

        // Only used for the "Uploads paused" alert if Google Drive needs signing in again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-arm the new-recording trigger in case Android dropped it.
        if (prefs.driveConnected) Sync.watchForRecordings(this)
        refresh()
    }

    private fun connectDrive() {
        Identity.getAuthorizationClient(this).authorize(DriveAuth.request())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    authResolution.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    onDriveConnected()
                }
            }
            .addOnFailureListener { e -> toast("Couldn't connect: ${e.message}") }
    }

    private fun onDriveConnected() {
        prefs.driveConnected = true
        Sync.schedule(this)
        Sync.runNow(this)
        refresh()
    }

    private fun refresh() {
        val connected = prefs.driveConnected
        binding.driveStatus.text = if (connected) "✅ Connected" else "Not connected yet"
        binding.connectDrive.text = if (connected) "Reconnect Google Drive" else "Connect Google Drive"

        binding.folderStatus.text = prefs.callFolder?.let { "✅ ${readableFolder(Uri.parse(it))}" }
            ?: "Not chosen yet. Pick the folder where your phone keeps saved calls."
    }

    /** Turns "primary:Music/Recordings/Call Recordings" into "Internal storage/Music/Recordings/Call Recordings". */
    private fun readableFolder(uri: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val path = id.substringAfter(':')
        return if (id.startsWith("primary:")) "Internal storage/$path" else path
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
