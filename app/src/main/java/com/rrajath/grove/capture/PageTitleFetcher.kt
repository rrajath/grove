package com.rrajath.grove.capture

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** Best-effort fetch of a web page's <title>, so a shared URL reads as a real heading. */
object PageTitleFetcher {
    private val TITLE = Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Bot-check interstitials (Reddit, Cloudflare, etc.) serve one of these instead of the
    // real title while a JS challenge resolves. A plain HTTP GET can't get past them, so a
    // title matching this pattern triggers the WebView fallback, which can run their JS.
    private val PLACEHOLDER_TITLE = Regex(
        "please wait|just a moment|attention required|checking your browser|" +
            "verify(?:ing)? you(?:'| a)re? (?:a )?human|human verification|" +
            "enable javascript|are you a robot|ddos protection by|access denied",
        RegexOption.IGNORE_CASE,
    )

    /** Returns the page title, or null on any failure. Runs its I/O on [Dispatchers.IO]. */
    suspend fun fetch(url: String, context: Context): String? {
        val httpTitle = fetchViaHttp(url)
        return if (httpTitle != null && !PLACEHOLDER_TITLE.containsMatchIn(httpTitle)) {
            httpTitle
        } else {
            fetchViaWebView(url, context)
        }
    }

    private suspend fun fetchViaHttp(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Mobile Safari/537.36",
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                // Read only as far as </title> rather than the whole page.
                val head = conn.inputStream.bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    var line = reader.readLine()
                    while (line != null && sb.length < 200_000) {
                        sb.append(line).append('\n')
                        if (sb.contains("</title>", ignoreCase = true)) break
                        line = reader.readLine()
                    }
                    sb.toString()
                }
                TITLE.find(head)?.groupValues?.get(1)?.let(::cleanTitle)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // Runs the page in a hidden WebView so its JS challenge/redirect can resolve, then reads
    // the real document title. Debounces on onPageFinished since a challenge page typically
    // does a client-side redirect into a second navigation before the real title appears.
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchViaWebView(url: String, context: Context): String? =
        withContext(Dispatchers.Main) {
            val webView = WebView(context.applicationContext)
            try {
                val rawTitle = withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine { cont ->
                        val handler = Handler(Looper.getMainLooper())
                        val settle = Runnable {
                            if (cont.isActive) cont.resume(webView.title?.let(::cleanTitle))
                        }
                        webView.settings.javaScriptEnabled = true
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                handler.removeCallbacks(settle)
                                handler.postDelayed(settle, 1_200)
                            }
                        }
                        cont.invokeOnCancellation { handler.removeCallbacks(settle) }
                        webView.loadUrl(url)
                    }
                }
                rawTitle?.takeIf { it.isNotBlank() && !PLACEHOLDER_TITLE.containsMatchIn(it) }
            } finally {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }

    private fun cleanTitle(raw: String): String =
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ")
            .trim()
}
