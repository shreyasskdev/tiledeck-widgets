package io.github.shreyasskdev.tiledeck.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApkDownloader {

    private const val TAG = "ApkDownloader"
    private const val UPDATE_DIR = "updates"
    private const val APK_NAME = "tiledeck-update.apk"

    /**
     * Downloads the APK into the app's private cache dir, then launches the
     * system package installer via a FileProvider URI.
     *
     * Returns true if the installer was successfully launched.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }

            // Nuke any previous download so we don't install a stale APK.
            dir.listFiles()?.forEach { it.delete() }

            val apkFile = File(dir, APK_NAME)
            Log.d(TAG, "Downloading $downloadUrl → ${apkFile.absolutePath}")

            val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                connect()
            }

            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buf = ByteArray(8 * 1024)
                    var read: Int
                    var copied = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        copied += read
                        if (total > 0) {
                            onProgress(((copied * 100) / total).toInt())
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            Log.d(TAG, "Downloaded ${apkFile.length()} bytes")
            withContext(Dispatchers.Main) {
                launchInstaller(context, apkFile)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download/install failed", e)
            false
        }
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}