package com.rrajath.grove.capture

/**
 * Content received via the Android share sheet, mapped onto the
 * `%shared_text` / `%shared_url` template placeholders (PRD §10).
 */
data class SharedPayload(
    val text: String = "",
    val url: String = "",
) {
    val isEmpty: Boolean get() = text.isEmpty() && url.isEmpty()

    companion object {
        private val URL = Regex("""https?://\S+""")

        /**
         * Split an ACTION_SEND payload: the first URL becomes `%shared_url`;
         * everything else (plus the subject, e.g. a page title) is `%shared_text`.
         */
        fun from(subject: String?, sharedText: String?): SharedPayload {
            val raw = sharedText.orEmpty().trim()
            val url = URL.find(raw)?.value.orEmpty()
            val remaining = raw.replace(url, "").trim()
            val text = listOfNotNull(subject?.trim()?.takeIf { it.isNotEmpty() }, remaining.takeIf { it.isNotEmpty() })
                .joinToString(" — ")
            return SharedPayload(text = text, url = url)
        }
    }
}
