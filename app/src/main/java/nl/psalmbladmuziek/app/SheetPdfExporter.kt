package nl.psalmbladmuziek.app

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SheetPdfExporter(
    private val activity: SheetMusicActivity,
    private val webView: WebView,
    private val isTextOnlyAsset: () -> Boolean,
    private val titleProvider: () -> String,
    private val setExportingPdf: (Boolean) -> Unit,
    private val shouldRestoreScore: () -> Boolean,
    private val preferSinglePdfPage: () -> Boolean
) {
    private data class VerseRange(val startY: Float, val endY: Float)
    private data class ExportState(val verseRanges: List<VerseRange>, val contentHeightPx: Int)
    private data class PageSlice(val startY: Float, val endY: Float)

    private var exportInProgress = false
    private var originalWebViewHeight: Int? = null

    fun shareCurrentVerseAsPdf() {
        if (exportInProgress) return
        if (isTextOnlyAsset()) {
            Toast.makeText(activity, "Voor dit item is geen bladmuziek-PDF beschikbaar.", Toast.LENGTH_SHORT).show()
            return
        }
        exportInProgress = true
        setExportingPdf(true)
        Toast.makeText(activity, "PDF wordt gemaakt...", Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript("window.__pdfExport = true; document.documentElement.classList.add('pdf-export'); updateScore();") {
            webView.postDelayed({
                webView.scrollTo(0, 0)
                fetchVerseRangesAndWritePdf()
            }, 250)
        }
    }

    private fun fetchVerseRangesAndWritePdf() {
        webView.evaluateJavascript("window.getPdfExportState ? window.getPdfExportState() : '{}' ") { raw ->
            val state = parseExportState(raw)
            prepareWebViewForFullCapture(state)
        }
    }

    private fun prepareWebViewForFullCapture(state: ExportState) {
        val lp = webView.layoutParams ?: return writeCurrentWebViewToPdf(state.verseRanges)
        val widthPx = webView.width.coerceAtLeast(1)
        val currentHeight = webView.height.coerceAtLeast(1)
        val targetHeight = state.contentHeightPx.coerceAtLeast(currentHeight)

        if (targetHeight <= currentHeight) {
            writeCurrentWebViewToPdf(state.verseRanges)
            return
        }

        if (originalWebViewHeight == null) {
            originalWebViewHeight = lp.height
        }
        lp.height = targetHeight
        webView.layoutParams = lp

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY)
        webView.measure(widthSpec, heightSpec)
        webView.layout(webView.left, webView.top, webView.left + widthPx, webView.top + targetHeight)
        webView.invalidate()

        webView.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                webView.post {
                    writeCurrentWebViewToPdf(state.verseRanges)
                }
            }
        })
    }

    private fun parseVerseRanges(raw: String?): List<VerseRange> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val jsonString = unwrapJavascriptString(raw)
            val arr = JSONArray(jsonString)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val start = obj.optDouble("startY", -1.0).toFloat()
                    val end = obj.optDouble("endY", -1.0).toFloat()
                    if (start >= 0f && end > start) {
                        add(VerseRange(start, end))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseExportState(raw: String?): ExportState {
        if (raw.isNullOrBlank() || raw == "null") return ExportState(emptyList(), 0)
        return try {
            val jsonString = unwrapJavascriptString(raw)
            val obj = JSONObject(jsonString)
            val rangesRaw = obj.optJSONArray("verseRanges")?.toString() ?: "[]"
            val ranges = parseVerseRanges(rangesRaw)
            val contentHeightPx = obj.optInt("contentHeightPx", 0)
            ExportState(ranges, contentHeightPx)
        } catch (_: Exception) {
            ExportState(emptyList(), 0)
        }
    }

    private fun writeCurrentWebViewToPdf(verseRanges: List<VerseRange>) {
        val sharedDir = File(activity.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, buildPdfFileName())
        if (file.exists()) file.delete()

        try {
            val sourceWidth = webView.width.toFloat().coerceAtLeast(1f)
            val sourceHeight = webView.height.toFloat().coerceAtLeast(1f)
            if (sourceWidth <= 0f || sourceHeight <= 0f) {
                throw IllegalStateException("PDF-bron is leeg.")
            }
            val pageWidth = PDF_PAGE_WIDTH
            val pageHeight = PDF_PAGE_HEIGHT
            val margin = PDF_MARGIN
            val availableWidth = (pageWidth - margin * 2).coerceAtLeast(1f)
            val availableHeight = (pageHeight - margin * 2).coerceAtLeast(1f)
            val plan = buildPagePlan(
                verseRanges = verseRanges,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                forceSinglePage = preferSinglePdfPage()
            )
            val document = PdfDocument()

            plan.pages.forEachIndexed { pageIndex, range ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val rangeHeight = (range.endY - range.startY).coerceAtLeast(1f)
                page.canvas.save()
                page.canvas.translate(margin, margin)
                page.canvas.scale(plan.scale, plan.scale)
                page.canvas.clipRect(0f, 0f, sourceWidth, rangeHeight)
                page.canvas.translate(0f, -range.startY)
                webView.draw(page.canvas)
                page.canvas.restore()
                document.finishPage(page)
            }

            file.outputStream().use { document.writeTo(it) }
            document.close()
            finishPdfExport()
            sharePdfFile(file)
        } catch (e: Exception) {
            finishPdfExport()
            Toast.makeText(activity, e.message ?: "PDF maken is mislukt.", Toast.LENGTH_LONG).show()
        } finally {
            restoreWebViewAfterCapture()
        }
    }

    private fun restoreWebViewAfterCapture() {
        val oldHeight = originalWebViewHeight ?: return
        originalWebViewHeight = null
        val lp = webView.layoutParams ?: return
        lp.height = oldHeight
        webView.layoutParams = lp
        webView.requestLayout()
    }

    private fun finishPdfExport() {
        exportInProgress = false
        setExportingPdf(false)
        if (shouldRestoreScore()) {
            webView.evaluateJavascript("window.__pdfExport = false; document.documentElement.classList.remove('pdf-export'); updateScore();", null)
        }
    }

    private fun sharePdfFile(file: File) {
        val title = titleProvider()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "PDF delen"))
    }

    private fun buildPdfFileName(): String = titleProvider()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "psalmverzen" } + ".pdf"

    private fun buildPagePlan(
        verseRanges: List<VerseRange>,
        sourceWidth: Float,
        sourceHeight: Float,
        availableWidth: Float,
        availableHeight: Float,
        forceSinglePage: Boolean
    ): PdfPagePlan {
        val widthScale = availableWidth / sourceWidth
        if (forceSinglePage) {
            return PdfPagePlan(
                scale = minOf(widthScale, availableHeight / sourceHeight),
                pages = listOf(PageSlice(0f, sourceHeight))
            )
        }

        val normalizedRanges = if (verseRanges.isEmpty()) {
            listOf(VerseRange(0f, sourceHeight))
        } else {
            verseRanges.sortedBy { it.startY }
        }

        var scale = widthScale
        val tallestVerse = normalizedRanges.maxOf { it.endY - it.startY }
        if (tallestVerse > 0f) {
            scale = minOf(scale, availableHeight / tallestVerse)
        }
        val capacity = (availableHeight / scale).coerceAtLeast(1f)
        return PdfPagePlan(
            scale = scale,
            pages = buildPageRanges(normalizedRanges, capacity, sourceHeight)
        )
    }

    private fun buildPageRanges(ranges: List<VerseRange>, capacity: Float, sourceHeight: Float): List<PageSlice> {
        if (ranges.isEmpty()) return listOf(PageSlice(0f, sourceHeight))

        val pages = mutableListOf<PageSlice>()
        var pageStart = ranges.first().startY
        var pageEnd = pageStart
        val epsilon = 0.5f

        for (range in ranges) {
            val candidateEnd = range.endY
            if (pageEnd <= pageStart + epsilon) {
                pageEnd = candidateEnd
                continue
            }
            if ((candidateEnd - pageStart) <= (capacity + epsilon)) {
                pageEnd = candidateEnd
            } else {
                pages += PageSlice(pageStart, pageEnd)
                pageStart = range.startY
                pageEnd = range.endY
            }
        }

        pages += PageSlice(pageStart, pageEnd)
        return pages.map { slice ->
            PageSlice(
                startY = slice.startY.coerceIn(0f, sourceHeight),
                endY = slice.endY.coerceIn(0f, sourceHeight)
            )
        }
    }

    private fun unwrapJavascriptString(raw: String): String =
        if (raw.startsWith("\"")) JSONArray("[$raw]").getString(0) else raw

    private data class PdfPagePlan(val scale: Float, val pages: List<PageSlice>)

    companion object {
        private const val PDF_PAGE_WIDTH = 595
        private const val PDF_PAGE_HEIGHT = 842
        private const val PDF_MARGIN = 28f
    }
}
