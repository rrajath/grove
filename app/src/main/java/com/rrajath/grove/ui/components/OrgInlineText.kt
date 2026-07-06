package com.rrajath.grove.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.InlineTokenizer
import com.rrajath.grove.org.InlineType
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono

/**
 * Render org inline markup (bold/italic/code/links/…) into an AnnotatedString.
 * Links display their description, not the raw `[[target][desc]]` syntax. When
 * [onLink] is given they are tappable; when null they're only styled (for rows
 * that are themselves clickable, like the outline).
 */
fun annotateOrgInline(
    text: String,
    c: GroveColors,
    onLink: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    for (token in InlineTokenizer.tokenize(text)) {
        when (token.type) {
            InlineType.TEXT -> append(token.text)
            InlineType.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(token.text) }
            InlineType.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.text) }
            InlineType.UNDERLINE -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(token.text) }
            InlineType.CODE, InlineType.VERBATIM -> withStyle(
                SpanStyle(fontFamily = PlexMono, fontSize = 13.5.sp, background = c.surface2)
            ) { append(token.text) }

            InlineType.TIMESTAMP -> withStyle(
                SpanStyle(fontFamily = PlexMono, fontSize = 13.5.sp, color = c.synTs)
            ) { append(token.text) }

            InlineType.LINK -> {
                val target = token.target ?: token.text
                val linkStyle = SpanStyle(color = c.synLink, textDecoration = TextDecoration.Underline)
                if (onLink != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = target,
                            styles = TextLinkStyles(linkStyle),
                        ) { onLink(target) }
                    ) { append(token.text) }
                } else {
                    withStyle(linkStyle) { append(token.text) }
                }
            }
        }
    }
}

/** A link's character range within the string [annotateOrgInline] renders, and its target. */
data class InlineLink(val range: IntRange, val target: String)

/** Every link's rendered character range in [text] once tokenized (labels shown, not raw syntax). */
fun orgInlineLinks(text: String): List<InlineLink> {
    var offset = 0
    val links = mutableListOf<InlineLink>()
    for (token in InlineTokenizer.tokenize(text)) {
        val start = offset
        offset += token.text.length
        if (token.type == InlineType.LINK) {
            links += InlineLink(start until offset, token.target ?: token.text)
        }
    }
    return links
}
