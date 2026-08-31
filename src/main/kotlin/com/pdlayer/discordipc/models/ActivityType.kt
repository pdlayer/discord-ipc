package com.pdlayer.discordipc.models

enum class ActivityType(val id: Int) {
    PLAYING(0),
    STREAMING(1),
    LISTENING(2),
    WATCHING(3),
    CUSTOM(4),
    COMPETING(5);

    val supportedBySetActivity: Boolean
        get() = this in SUPPORTED_BY_SET_ACTIVITY

    private companion object {
        val SUPPORTED_BY_SET_ACTIVITY = setOf(PLAYING, LISTENING, WATCHING, COMPETING)
    }
}
