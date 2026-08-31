package com.pdlayer.discordipc.models

import com.pdlayer.discordipc.utils.TimeUtils
import java.net.URI

data class RichPresence(
    val details: String? = null,
    val state: String? = null,
    val type: ActivityType = ActivityType.PLAYING,
    val url: String? = null,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
    val party: Party? = null,
    val secrets: Secrets? = null,
    val buttons: List<Button>? = null,
    val detailsUrl: String? = null,
    val stateUrl: String? = null,
    val instance: Boolean = false,
) {
    init {
        require(buttons == null || buttons.size <= 2) {
            "Discord Rich Presence supports at most 2 buttons"
        }
        requireHttpUrl(detailsUrl, "Details url")
        requireHttpUrl(stateUrl, "State url")
    }

    data class Timestamps(
        val start: Long? = null,
        val end: Long? = null,
    ) {
        companion object {
            fun elapsed(time: String): Timestamps {
                val now = System.currentTimeMillis() / 1000L
                return Timestamps(start = now - TimeUtils.parseToSeconds(time))
            }

            fun loop(min: String, max: String): Timestamps {
                val now = System.currentTimeMillis() / 1000L
                val minSeconds = TimeUtils.parseToSeconds(min)
                val maxSeconds = TimeUtils.parseToSeconds(max)
                val diff = maxSeconds - minSeconds

                require(diff > 0) { "Loop max ($max) must be greater than loop min ($min)" }

                val offset = (now % diff) + minSeconds
                return Timestamps(start = now - offset)
            }
        }
    }

    data class Assets(
        val largeImageKey: String? = null,
        val largeImageText: String? = null,
        val smallImageKey: String? = null,
        val smallImageText: String? = null,
        val largeImageUrl: String? = null,
        val smallImageUrl: String? = null,
    ) {
        init {
            requireHttpUrl(largeImageUrl, "Large image url")
            requireHttpUrl(smallImageUrl, "Small image url")
        }
    }

    data class Party(
        val id: String,
        val size: Int,
        val max: Int,
    ) {
        init {
            require(id.isNotBlank()) { "Party id must not be blank" }
            require(size >= 0) { "Party size cannot be negative: $size" }
            require(max >= 1) { "Party max must be at least 1: $max" }
            require(size <= max) {
                "Party size cannot be greater than party max"
            }
        }
    }

    data class Secrets(
        val match: String? = null,
        val join: String? = null,
        val spectate: String? = null,
    )

    data class Button(
        val label: String,
        val url: String,
    ) {
        init {
            require(label.isNotBlank()) { "Button label must not be blank" }
            require(label.length <= MAX_LABEL_LENGTH) {
                "Button label is longer than $MAX_LABEL_LENGTH characters: \"$label\""
            }
            require(isHttpUrl(url)) { "Button url must be a valid http(s) URL: \"$url\"" }
            require(url.length <= MAX_URL_LENGTH) {
                "Button url is longer than $MAX_URL_LENGTH characters"
            }
        }
    }

    companion object {
        fun playing(
            details: String? = null,
            state: String? = null,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.PLAYING, details, state, null, timestamps, assets, party, buttons)

        fun streaming(
            details: String? = null,
            state: String? = null,
            url: String,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.STREAMING, details, state, url, timestamps, assets, party, buttons)

        fun listening(
            details: String? = null,
            state: String? = null,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.LISTENING, details, state, null, timestamps, assets, party, buttons)

        fun watching(
            details: String? = null,
            state: String? = null,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.WATCHING, details, state, null, timestamps, assets, party, buttons)

        fun competing(
            details: String? = null,
            state: String? = null,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.COMPETING, details, state, null, timestamps, assets, party, buttons)

        fun custom(
            details: String? = null,
            state: String? = null,
            timestamps: Timestamps? = null,
            assets: Assets? = null,
            party: Party? = null,
            buttons: List<Button>? = null,
        ): RichPresence = activity(ActivityType.CUSTOM, details, state, null, timestamps, assets, party, buttons)

        private fun activity(
            type: ActivityType,
            details: String?,
            state: String?,
            url: String?,
            timestamps: Timestamps?,
            assets: Assets?,
            party: Party?,
            buttons: List<Button>?,
        ): RichPresence = RichPresence(
            details = details,
            state = state,
            type = type,
            url = url,
            timestamps = timestamps,
            assets = assets,
            party = party,
            buttons = buttons?.toList(),
        )
    }
}

private const val MAX_LABEL_LENGTH = 32

private const val MAX_URL_LENGTH = 512

private fun requireHttpUrl(value: String?, name: String) {
    if (value == null) return
    require(isHttpUrl(value)) { "$name must be a valid http(s) URL: \"$value\"" }
}

private fun isHttpUrl(value: String): Boolean = try {
    val uri = URI.create(value)
    val scheme = uri.scheme

    (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
} catch (_: Exception) {
    false
}
