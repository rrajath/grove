package com.rrajath.grove.ui.newbadge

import com.rrajath.grove.settings.GroveSettings

/**
 * Which "NEW" badges are live right now, derived from persisted state
 * ([GroveSettings.newBadgeBaseline] + [GroveSettings.seenNewFeatures]) and the
 * [NEW_FEATURES] registry.
 *
 * A feature is *active* — its badges show — when all of:
 *  - a baseline is recorded (the framework has run at least once), and
 *  - the feature shipped *after* that baseline (the user updated into it rather
 *    than installing fresh), and
 *  - the user hasn't reached its destination yet.
 */
class NewBadgeState private constructor(
    private val activeAnchors: Set<String>,
    private val destinationToFeatureIds: Map<String, List<String>>,
) {
    /** Whether [anchorKey] should render a NEW badge right now. */
    fun isNew(anchorKey: String): Boolean = anchorKey in activeAnchors

    /** Ids of the features retired by reaching [anchorKey] (an active feature's destination). */
    fun featuresReachedAt(anchorKey: String): List<String> =
        destinationToFeatureIds[anchorKey].orEmpty()

    companion object {
        val EMPTY = NewBadgeState(emptySet(), emptyMap())

        fun from(settings: GroveSettings, features: List<NewFeature> = NEW_FEATURES): NewBadgeState {
            val baseline = settings.newBadgeBaseline ?: return EMPTY
            val active = features.filter {
                it.since > baseline && it.id !in settings.seenNewFeatures
            }
            if (active.isEmpty()) return EMPTY
            return NewBadgeState(
                activeAnchors = active.flatMapTo(mutableSetOf()) { it.anchors },
                destinationToFeatureIds = active.groupBy({ it.destination }, { it.id }),
            )
        }
    }
}
