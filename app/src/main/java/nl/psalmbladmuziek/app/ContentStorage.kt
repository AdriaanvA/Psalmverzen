package nl.psalmbladmuziek.app

import android.content.Context
import java.io.File

object ContentStorage {
    private const val CONTENT_DIR = "content"
    private const val MUSIC_XML_DIR = "musicxml"
    private const val DOWNLOADED_MANIFEST_FILE = "content_manifest.json"

    fun musicXmlDirectory(context: Context): File = File(contentDirectory(context), MUSIC_XML_DIR).apply {
        mkdirs()
    }

    fun downloadedManifestFile(context: Context): File = File(contentDirectory(context), DOWNLOADED_MANIFEST_FILE)

    fun readMusicXml(context: Context, fileName: String): String {
        readDownloadedMusicXmlOrNull(context, fileName)?.let { return it }

        return readBundledAsset(context, fileName)
    }

    fun readDownloadedMusicXmlOrNull(context: Context, fileName: String): String? {
        val downloadedFile = File(musicXmlDirectory(context), fileName)
        return if (downloadedFile.exists()) downloadedFile.readText() else null
    }

    fun readBundledAsset(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    fun writeDownloadedMusicXml(context: Context, fileName: String, content: String) {
        val targetFile = File(musicXmlDirectory(context), fileName)
        val tempFile = File(targetFile.parentFile, "$fileName.tmp")
        tempFile.writeText(content)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    fun writeDownloadedManifest(context: Context, content: String) {
        val targetFile = downloadedManifestFile(context)
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    private fun contentDirectory(context: Context): File = File(context.filesDir, CONTENT_DIR).apply {
        mkdirs()
    }
}