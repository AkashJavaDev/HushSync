package com.hushsync.app

import android.content.Context

/** Small wrapper around SharedPreferences for the app's settings and upload bookkeeping. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Tree URI of the folder where the phone's dialer saves call recordings. */
    var callFolder: String?
        get() = sp.getString("callFolder", null)
        set(value) = sp.edit().putString("callFolder", value).apply()

    var driveConnected: Boolean
        get() = sp.getBoolean("driveConnected", false)
        set(value) = sp.edit().putBoolean("driveConnected", value).apply()

    /** Recordings already uploaded that couldn't be deleted, so they aren't uploaded twice. */
    fun isUploaded(key: String): Boolean =
        sp.getStringSet("uploaded", emptySet())!!.contains(key)

    fun markUploaded(key: String) {
        val keys = HashSet(sp.getStringSet("uploaded", emptySet())!!)
        keys.add(key)
        sp.edit().putStringSet("uploaded", keys).apply()
    }
}
