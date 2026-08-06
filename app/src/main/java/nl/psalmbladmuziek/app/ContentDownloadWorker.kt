package nl.psalmbladmuziek.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ContentDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val manifestUrl = inputData.getString(KEY_MANIFEST_URL)
            ?: applicationContext.getString(R.string.content_manifest_url).takeIf { it.isNotBlank() }
            ?: return Result.success()
        if (!isHttpsUrl(manifestUrl)) return Result.failure()

        return runCatching {
            val manifestJson = downloadText(manifestUrl)
            val manifest = JSONObject(manifestJson)
            val items = manifest.optJSONArray("items") ?: return@runCatching

            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val fileName = item.optString("fileName")
                val musicXmlUrl = item.optString("musicXmlUrl")
                if (fileName.isNotBlank() && musicXmlUrl.isNotBlank()) {
                    ContentStorage.writeDownloadedMusicXml(
                        applicationContext,
                        fileName,
                        downloadText(musicXmlUrl)
                    )
                }
            }

            ContentStorage.writeDownloadedManifest(applicationContext, manifestJson)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private fun downloadText(url: String): String {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != "https") throw IOException("Only HTTPS content downloads are supported")

        val connection = parsedUrl.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            if (connection.responseCode !in 200..299) {
                connection.errorStream?.close()
                throw IOException("HTTP ${connection.responseCode} while downloading $url")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "content-download"
        private const val KEY_MANIFEST_URL = "manifestUrl"

        private fun isHttpsUrl(url: String): Boolean =
            runCatching { URL(url).protocol == "https" }.getOrDefault(false)

        fun enqueueIfConfigured(context: Context) {
            val manifestUrl = context.getString(R.string.content_manifest_url)
            if (manifestUrl.isBlank()) {
                return
            }
            enqueue(context, manifestUrl)
        }

        fun enqueue(context: Context, manifestUrl: String) {
            val request = OneTimeWorkRequestBuilder<ContentDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf(KEY_MANIFEST_URL to manifestUrl))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}