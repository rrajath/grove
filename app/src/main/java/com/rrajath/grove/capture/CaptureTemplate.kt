package com.rrajath.grove.capture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Where a capture template inserts its entry (PRD §7.4). */
@Serializable
sealed class TargetLocation {
    @Serializable
    @SerialName("top")
    data object TopOfFile : TargetLocation()

    @Serializable
    @SerialName("bottom")
    data object BottomOfFile : TargetLocation()

    /**
     * Insert as child of a heading, found by [customId] (recommended, robust)
     * or by exact [title]. [appendLast] = insert after the last existing child.
     */
    @Serializable
    @SerialName("under_heading")
    data class UnderHeading(
        val title: String? = null,
        val customId: String? = null,
        val appendLast: Boolean = true,
    ) : TargetLocation()

    /** Year → Month → Day tree; entry under the day heading. */
    @Serializable
    @SerialName("datetree_date")
    data object DatetreeDate : TargetLocation()

    /** Same tree; by convention the template leads with a `%U`-style time. */
    @Serializable
    @SerialName("datetree_datetime")
    data object DatetreeDatetime : TargetLocation()

    val isDatetree: Boolean get() = this is DatetreeDate || this is DatetreeDatetime

    fun describe(): String = when (this) {
        is TopOfFile -> "top of file"
        is BottomOfFile -> "bottom of file"
        is UnderHeading -> "under " + (customId?.let { "#$it" } ?: title ?: "heading")
        is DatetreeDate, is DatetreeDatetime -> "datetree"
    }
}

@Serializable
data class CaptureTemplate(
    val id: String,
    val name: String,
    /** Glyph shown in the picker tile. */
    val icon: String = "✶",
    val targetFile: String,
    val location: TargetLocation,
    val template: String,
)

object TemplateSerializer {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun encode(templates: List<CaptureTemplate>): String =
        json.encodeToString(TemplateListSurrogate(templates))

    fun decode(text: String): List<CaptureTemplate> =
        runCatching { json.decodeFromString<TemplateListSurrogate>(text).templates }
            .getOrDefault(emptyList())

    @Serializable
    private data class TemplateListSurrogate(val templates: List<CaptureTemplate>)
}

/** Built-in defaults (PRD §7.5); editable/deletable like any other template. */
object DefaultTemplates {
    val all: List<CaptureTemplate> = listOf(
        CaptureTemplate(
            id = "builtin-journal",
            name = "Journal Entry",
            icon = "✶",
            targetFile = "journal.org",
            location = TargetLocation.DatetreeDatetime,
            // First line of the entry is its heading; the cursor lands right
            // after the stamp, on the heading, ready for input.
            template = "%U %cursor",
        ),
        CaptureTemplate(
            id = "builtin-quick-note",
            name = "Quick Note",
            icon = "✷",
            targetFile = "inbox.org",
            location = TargetLocation.BottomOfFile,
            template = "* %^{Title}\n%cursor",
        ),
        CaptureTemplate(
            id = "builtin-todo",
            name = "TODO",
            icon = "✓",
            targetFile = "inbox.org",
            location = TargetLocation.BottomOfFile,
            template = "* TODO %^{Title}\nSCHEDULED: %T\n%cursor",
        ),
    )
}
