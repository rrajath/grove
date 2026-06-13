package com.rrajath.grove.capture

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Best-effort fetch of a web page's <title>, so a shared URL reads as a real heading. */
object PageTitleFetcher {
    private val TITLE = Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Returns the page title, or null on any failure. Runs its I/O on [Dispatchers.IO]. */
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Grove")
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
                TITLE.find(head)?.groupValues?.get(1)?.let { raw ->
                    Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
