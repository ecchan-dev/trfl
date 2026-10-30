package org.trfl.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** Android owns the transfer so it survives the activity/process and retries lost networks. */
class MediaDownloads(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences("transfers", 0)
    fun start(url: String): Long {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" && uri.host == "i.4cdn.org")
        val original = uri.lastPathSegment ?: "attachment"
        val filename = "TRFL-${System.currentTimeMillis()}-$original"
        val request = DownloadManager.Request(uri)
            .setTitle(original).setDescription("Trfl media export")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        val id = manager.enqueue(request)
        prefs.edit().putString(id.toString(), url).putBoolean("download-only:$id", true).apply()
        return id
    }
    data class Transfer(val id: Long, val title: String, val done: Long, val total: Long, val status: Int, val uri: String? = null, val url: String? = null, val library: Boolean = true)
    fun list(): List<Transfer> {
        val ids = prefs.all.keys.mapNotNull { it.toLongOrNull() }.toLongArray()
        if (ids.isEmpty()) return emptyList()
        return buildList {
            manager.query(DownloadManager.Query().setFilterById(*ids)).use { cursor ->
                while (cursor.moveToNext()) {
                    fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    add(Transfer(number(DownloadManager.COLUMN_ID), cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)),
                        number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), number(DownloadManager.COLUMN_STATUS).toInt(),
                        manager.getUriForDownloadedFile(number(DownloadManager.COLUMN_ID))?.toString(), prefs.getString(number(DownloadManager.COLUMN_ID).toString(), null), !prefs.getBoolean("download-only:${number(DownloadManager.COLUMN_ID)}", false)))
                }
            }
        }.sortedByDescending { it.id }
    }
    fun cancel(id: Long) { manager.remove(id); prefs.edit().remove(id.toString()).apply() }
    fun retry(id: Long) {
        val url = prefs.getString(id.toString(), null) ?: return
        start(url)
        cancel(id)
    }
}
