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

    // Bot-check interstitials (Cloudflare, etc.) serve one of these instead of the real title
    // while a JS challenge resolves. Reddit instead serves a generic app-shell title (just
    // "Reddit", or a marketing tagline like "Reddit - The heart of the internet") to every URL
    // — the real post/subreddit title is only set client-side once its JS bundle hydrates and
    // fetches the post data. Either way, a plain HTTP GET can't get the real title, so a title
    // matching this pattern triggers the WebView fallback (which can run that JS) and, once
    // there, keeps that fallback polling rather than accepting the shell title as final.
    private val PLACEHOLDER_TITLE = Regex(
        "please wait|just a moment|attention required|checking your browser|" +
            "verify(?:ing)? you(?:'| a)re? (?:a )?human|human verification|" +
            "enable javascript|are you a robot|ddos protection by|access denied|" +
            "^reddit$|^reddit\\s*[-|]\\s*(the heart of the internet|dive into anything)$",
        RegexOption.IGNORE_CASE,
    )

    private const val WEBVIEW_TIMEOUT_MS = 12_000L
    private const val WEBVIEW_POLL_INTERVAL_MS = 400L

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

    // Runs the page in a hidden WebView so its JS challenge/redirect (or, for an SPA like
    // Reddit, its client-side data fetch) can resolve, then reads the real document title.
    // Polls webView.title after onPageFinished rather than reading it once after a fixed
    // delay: how long an SPA takes to swap its shell title for the real one varies with
    // network speed, so this keeps checking — at [WEBVIEW_POLL_INTERVAL_MS] intervals — until
    // the title escapes [PLACEHOLDER_TITLE], or the outer timeout gives up and this returns
    // null rather than surface a shell/interstitial title as if it were real.
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchViaWebView(url: String, context: Context): String? =
        withContext(Dispatchers.Main) {
            val webView = WebView(context.applicationContext)
            try {
                withTimeoutOrNull(WEBVIEW_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val handler = Handler(Looper.getMainLooper())
                        lateinit var poll: Runnable
                        poll = Runnable {
                            val title = webView.title?.let(::cleanTitle)
                            if (title != null && title.isNotBlank() && !PLACEHOLDER_TITLE.containsMatchIn(title)) {
                                if (cont.isActive) cont.resume(title)
                            } else {
                                handler.postDelayed(poll, WEBVIEW_POLL_INTERVAL_MS)
                            }
                        }
                        webView.settings.javaScriptEnabled = true
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                handler.removeCallbacks(poll)
                                handler.postDelayed(poll, WEBVIEW_POLL_INTERVAL_MS)
                            }
                        }
                        cont.invokeOnCancellation { handler.removeCallbacks(poll) }
                        webView.loadUrl(url)
                    }
                }
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
