package com.hushsync.app

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Google sign-in for Drive. Uses the "drive.file" scope, so the app can only see
 * files and folders it created itself, never the rest of your Drive.
 */
object DriveAuth {
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    /**
     * Gets an access token without showing any UI. Must be called off the main thread.
     * Returns null if the user has to open the app and sign in again.
     */
    fun tokenBlocking(context: Context): String? {
        val result = Tasks.await(
            Identity.getAuthorizationClient(context).authorize(request()),
            60, TimeUnit.SECONDS
        )
        return if (result.hasResolution()) null else result.accessToken
    }

    /** Forgets a token Google rejected, so the next call fetches a fresh one. */
    fun clearToken(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {
        }
    }
}
