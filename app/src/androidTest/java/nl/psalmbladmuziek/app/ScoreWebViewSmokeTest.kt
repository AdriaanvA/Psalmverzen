package nl.psalmbladmuziek.app

import android.content.Intent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ScoreWebViewSmokeTest {
    @Test
    fun representativePsalmRendersNonEmptySvg() {
        assertScoreRenders("Psalm001_v1.json")
    }

    @Test
    fun gezang26RendersNonEmptySvg() {
        assertScoreRenders("Gezang026_v1.json")
    }

    private fun assertScoreRenders(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, SheetMusicActivity::class.java)
            .putExtra(SheetMusicActivity.EXTRA_FILE_NAME, fileName)
        val rendered = AtomicBoolean(false)
        val completed = CountDownLatch(1)

        ActivityScenario.launch<SheetMusicActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "Boolean(document.querySelector('svg') && document.querySelector('svg').childElementCount > 0)"
                    ) { result ->
                        rendered.set(result == "true")
                        completed.countDown()
                    }
                }, 1_000)
            }

            assertTrue("WebView render callback timed out for $fileName", completed.await(10, TimeUnit.SECONDS))
            assertTrue("Rendered score SVG is empty for $fileName", rendered.get())
        }
    }
}
