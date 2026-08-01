package com.rrajath.grove.reminders

import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline

/** Which planning timestamp a reminder tracks. */
enum class PlanningType(val storageKey: String) {
    SCHEDULED("SCHEDULED"),
    DEADLINE("DEADLINE");

    companion object {
        fun fromStorage(value: String): PlanningType? = entries.firstOrNull { it.storageKey == value }
    }
}

/**
 * Best-effort heading identity for reminder tracking (no file mutation, no
 * auto-injected CUSTOM_ID). A heading is identified by its file, its position
 * in the ancestor-title chain, and its level; renaming a heading or moving it
 * to a different level breaks tracking for that heading only: an accepted
 * trade-off, not a bug (see the reminders feature spec).
 */
object ReminderKeys {

    /** Field separator unlikely to appear in a file name or heading title. */
    private const val SEP = ""

    /** Ancestor titles (root-first) + the heading's own title, "/"-joined. */
    fun headingPath(doc: OrgDocument, h: OrgHeadline): String {
        val ancestors = mutableListOf<String>()
        var p = doc.parent(h)
        while (p != null) {
            ancestors.add(p.title)
            p = doc.parent(p)
        }
        ancestors.reverse()
        ancestors.add(h.title)
        return ancestors.joinToString("/")
    }

    /** Composite primary key for the `reminders` table. */
    fun reminderKey(fileName: String, headingPath: String, level: Int, type: PlanningType): String =
        "$fileName$SEP$level$SEP$headingPath$SEP${type.storageKey}"

    /**
     * Stable, positive per-[key] id reused for both the notification and its alarm
     * PendingIntent. Derived from a CRC32 of the key's UTF-8 bytes rather than a bare
     * 31-bit `String.hashCode()`: two distinct composite keys that hash-collide would
     * otherwise share a PendingIntent (extras don't affect PendingIntent equality) and
     * silently clobber each other's alarm/notification. CRC32 spreads the 32-bit space
     * far better, making collisions much less likely.
     */
    fun notificationId(key: String): Int {
        val crc = java.util.zip.CRC32()
        crc.update(key.toByteArray(Charsets.UTF_8))
        return (crc.value and 0x7fffffff).toInt()
    }

    /** Re-locate a heading in a freshly parsed [doc] by its stored [headingPath]/[level]. */
    fun findHeadline(doc: OrgDocument, headingPath: String, level: Int): OrgHeadline? =
        doc.headlines.firstOrNull { it.level == level && headingPath(doc, it) == headingPath }
}
