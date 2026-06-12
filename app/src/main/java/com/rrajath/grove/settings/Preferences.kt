package com.rrajath.grove.settings

// Pure-Kotlin preference enums (no android imports) so mapping logic is JVM-testable.

enum class ThemePreference(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}

enum class FontSizePreference(val storageKey: String, val scale: Float) {
    SMALL("small", 0.9f),
    MEDIUM("medium", 1.0f),
    LARGE("large", 1.12f);

    companion object {
        fun fromStorage(value: String?): FontSizePreference =
            entries.firstOrNull { it.storageKey == value } ?: MEDIUM
    }
}

enum class SyncMode(val storageKey: String, val label: String) {
    MANUAL("manual", "Manual only"),
    ON_OPEN_CLOSE("on_open_close", "On open / close"),
    PERIODIC("periodic", "Periodic"),
    CONTINUOUS("continuous", "Continuous");

    companion object {
        fun fromStorage(value: String?): SyncMode =
            entries.firstOrNull { it.storageKey == value } ?: ON_OPEN_CLOSE
    }
}

enum class OutlineToggle { TAGS, TIMESTAMPS, KEYWORDS }

enum class NoteOpenMode(val storageKey: String) {
    READ("read"),
    EDIT("edit");

    companion object {
        fun fromStorage(value: String?): NoteOpenMode =
            entries.firstOrNull { it.storageKey == value } ?: READ
    }
}
