package dev.beefers.vendetta.manager.domain.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.getSystemService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class DownloadManager(
    private val context: Context,
    private val prefs: PreferenceManager
) {

    suspend fun downloadDiscordApk(version: String, out: File, onProgressUpdate: (Float) -> Unit): File =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/base", out, onProgressUpdate)

    suspend fun downloadSplit(version: String, split: String, out: File, onProgressUpdate: (Float) -> Unit): File =
        download("${prefs.mirror.baseUrl}/tracker/download/$version/$split", out, onProgressUpdate)

    suspend fun downloadVendetta(out: File, onProgressUpdate: (Float) -> Unit) =
        download(
            "https://github.com/admin-elvistrujanovic/VendettaXposed/releases/latest/download/app-release.apk",
            out,
            onProgressUpdate
        )

    suspend fun downloadUpdate(out: File) =
        download(
            "https://github.com/admin-elvistrujanovic/VendettaManager/releases/latest/download/Manager.apk",
            out
        ) {
            /* TODO: Update a progress bar in the update dialog */
        }

    suspend fun download(url: String, out: File, onProgressUpdate: (Float) -> Unit): File {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Vendetta Manager")
            .setDescription("Downloading ${out.name}...")
            .setDestinationUri(Uri.fromFile(out))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService<DownloadManager>()
            ?: throw IllegalStateException("DownloadManager not available")

        val downloadId = downloadManager.enqueue(request)

        var lastProgress = 0L

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    break
                } else if (status == DownloadManager.STATUS_FAILED) {
                    // Handle download failure
                    break
                } else {
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val progress = if (bytesTotal > 0) bytesDownloaded * 100 / bytesTotal else 0

                    if (progress > lastProgress) {
                        onProgressUpdate(progress.toFloat() / 100f) // Corrected progress calculation
                        lastProgress = progress
                    }
                }
            }
            cursor.close()
        }

        return out
    }

}
