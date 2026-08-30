package com.kino.puber.domain.model

/** Where the player remembers audio and subtitle selections. */
enum class TrackPreferenceScope {
    /** One selection shared by every title. */
    GLOBAL,

    /** A separate selection for each title. */
    PER_TITLE,
}
