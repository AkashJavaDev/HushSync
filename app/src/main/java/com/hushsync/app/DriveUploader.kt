package com.hushsync.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Minimal Google Drive v3 REST client: find/create folders and upload files. */
class DriveUploader(private val token: String) {

    /** Google rejected the access token (HTTP 401). */
    class AuthExpired : IOException("Google Drive sign-in expired")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Returns the id of folder [name] inside [parentId], creating it if needed. */
    fun findOrCreateFolder(name: String, parentId: String): String {
        val query = "mimeType='$FOLDER_MIME' and trashed=false" +
            " and name='${escape(name)}' and '$parentId' in parents"
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("spaces", "drive")
            .build()
        val found = send(Request.Builder().url(url).get()) { it.bodyText() }
        val files = JSONObject(found).getJSONArray("files")
        if (files.length() > 0) return files.getJSONObject(0).getString("id")

        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put(parentId))
        val created = send(
            Request.Builder().url("$API/files?fields=id").post(metadata.toString().toRequestBody(JSON))
        ) { it.bodyText() }
        return JSONObject(created).getString("id")
    }

    /**
     * Uploads a file with Drive's resumable upload and returns the size Drive stored,
     * so the caller can check the whole file arrived before deleting the original.
     */
    fun upload(name: String, mimeType: String, size: Long, parentId: String, open: () -> InputStream): Long {
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(parentId))
        val start = Request.Builder()
            .url("$UPLOAD_API/files?uploadType=resumable&fields=id,size")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", size.toString())
            .post(metadata.toString().toRequestBody(JSON))
        val sessionUrl = send(start) { it.header("Location") }
            ?: throw IOException("Drive did not return an upload URL")

        val body = object : RequestBody() {
            override fun contentType(): MediaType = mimeType.toMediaType()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                open().use { sink.writeAll(it.source()) }
            }
        }
        val uploaded = send(Request.Builder().url(sessionUrl).put(body)) { it.bodyText() }
        return JSONObject(uploaded).optString("size", "-1").toLong()
    }

    private fun <T> send(builder: Request.Builder, read: (Response) -> T): T {
        val request = builder.header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw AuthExpired()
            if (!response.isSuccessful) {
                throw IOException("Drive error ${response.code}: ${response.bodyText().take(200)}")
            }
            return read(response)
        }
    }

    private fun Response.bodyText(): String = body?.string().orEmpty()

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
