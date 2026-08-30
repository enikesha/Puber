package com.kino.puber.domain.model

/** Where the player remembers audio and subtitle selections. */
enum class TrackPreferenceScope {
    /** One selection shared by every title. */
    GLOBAL,

    /** A separate selection for each title, falling back to the shared one for an unseen title. */
    PER_TITLE,

    /**
     * A separate selection for each title and nothing else: an unseen title starts with no
     * remembered track instead of inheriting the shared selection.
     */
    PER_VIDEO,
}
