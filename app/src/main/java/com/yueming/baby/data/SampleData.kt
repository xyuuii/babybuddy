package com.yueming.baby.data

// All sample data has been removed from the production build.
// Prepopulate logic was causing bugs (sample data overwriting user data).
// File retained to avoid breaking any stale imports — all members are gone.

@Deprecated("Sample data removed; use empty defaults instead", ReplaceWith("BabyInfo()"))
val sampleBaby = BabyInfo(
    name = "",
    nickname = "",
    birthDate = "",
    gender = ""
)

@Deprecated("Sample data removed")
val sampleTimeline = emptyList<TimelineRecord>()

@Deprecated("Sample data removed")
val samplePhotos = emptyList<PhotoEntry>()
